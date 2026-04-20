package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Document;
import mr.gov.finances.sgci.domain.entity.Dqe;
import mr.gov.finances.sgci.domain.entity.DqeLigne;
import mr.gov.finances.sgci.domain.entity.DecisionCorrection;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.FiscaliteInterieure;
import mr.gov.finances.sgci.domain.entity.LigneImportation;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.ModeleFiscal;
import mr.gov.finances.sgci.domain.entity.DemandeCorrectionRejet;
import mr.gov.finances.sgci.domain.entity.Recapitulatif;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.EffectiveIdentityService;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.web.dto.CreateDemandeCorrectionRequest;
import mr.gov.finances.sgci.web.dto.UpdateDemandeCorrectionRequest;
import mr.gov.finances.sgci.web.dto.DecisionCorrectionDto;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionDto;
import mr.gov.finances.sgci.web.dto.DemandeCorrectionRejetDto;
import mr.gov.finances.sgci.web.dto.DqeDto;
import mr.gov.finances.sgci.web.dto.DqeLigneDto;
import mr.gov.finances.sgci.web.dto.DocumentDto;
import mr.gov.finances.sgci.web.dto.FiscaliteInterieureDto;
import mr.gov.finances.sgci.web.dto.LigneImportationDto;
import mr.gov.finances.sgci.web.dto.MarcheDto;
import mr.gov.finances.sgci.web.dto.ModeleFiscalDto;
import mr.gov.finances.sgci.web.dto.RecapitulatifDto;
import mr.gov.finances.sgci.workflow.DemandeCorrectionWorkflow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DemandeCorrectionService {

    private final DemandeCorrectionRepository demandeRepository;
    private final AutoriteContractanteRepository autoriteRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final MarcheRepository marcheRepository;
    private final ConventionRepository conventionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DemandeCorrectionWorkflow workflow;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final DocumentService documentService;
    private final DocumentRequirementValidator requirementValidator;
    private final DossierGedService dossierGedService;
    private final EffectiveIdentityService effectiveIdentityService;

    private static String generateNumero() {
        return "DC-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findAll(AuthenticatedUser user) {
        List<DemandeCorrection> list = resolveDemandeList(user);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DemandeCorrectionDto findById(Long id, AuthenticatedUser user) {
        DemandeCorrection dc = demandeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + id));
        if (!canAccessDemandeCorrection(dc.getId(), user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: demande hors périmètre");
        }
        return toDto(dc);
    }

    private List<DemandeCorrection> resolveDemandeList(AuthenticatedUser user) {
        if (user == null || user.getUserId() == null) {
            return filterBrouillonHorsAc(demandeRepository.findAllByOrderByDateDepotDescIdDesc());
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Role role = user.getRole();

        if (role == Role.AUTORITE_CONTRACTANTE) {
            Long acId = effectiveIdentityService.resolveAutoriteContractanteId(user, u);
            if (acId == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
            }
            return demandeRepository.findByAutoriteContractanteIdOrderByDateDepotDescIdDesc(acId);
        }

        if (role == Role.AUTORITE_UPM || role == Role.AUTORITE_UEP) {
            return filterBrouillonHorsAc(demandeRepository.findByDelegueId(u.getId()));
        }

        if (role == Role.ENTREPRISE) {
            Long entId = effectiveIdentityService.resolveEntrepriseId(user, u);
            if (entId == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
            }
            return filterBrouillonHorsAc(demandeRepository.findByEntrepriseIdOrderByDateDepotDescIdDesc(entId));
        }

        return filterBrouillonHorsAc(demandeRepository.findAllByOrderByDateDepotDescIdDesc());
    }

    /** Les brouillons ne sont visibles que pour l’AC créatrice (pas pour les autres rôles / files). */
    private List<DemandeCorrection> filterBrouillonHorsAc(List<DemandeCorrection> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        return list.stream()
                .filter(d -> d.getStatut() != StatutDemande.BROUILLON)
                .collect(Collectors.toList());
    }

    private boolean canAccessDemandeCorrection(Long demandeId, AuthenticatedUser user) {
        if (demandeId == null) {
            return false;
        }
        return demandeRepository.findById(demandeId)
                .map(dc -> canAccessDemandeCorrectionEntity(dc, user))
                .orElse(false);
    }

    private boolean canAccessDemandeCorrectionEntity(DemandeCorrection dc, AuthenticatedUser user) {
        if (dc.getStatut() == StatutDemande.BROUILLON) {
            if (user == null || user.getUserId() == null) {
                return false;
            }
            Utilisateur u = utilisateurRepository.findById(user.getUserId()).orElse(null);
            if (u == null || user.getRole() != Role.AUTORITE_CONTRACTANTE) {
                return false;
            }
            Long acId = effectiveIdentityService.resolveAutoriteContractanteId(user, u);
            if (acId == null) {
                return false;
            }
            return dc.getAutoriteContractante() != null
                    && acId.equals(dc.getAutoriteContractante().getId());
        }
        if (user == null || user.getUserId() == null) {
            return true;
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId()).orElse(null);
        if (u == null || user.getRole() == null) {
            return false;
        }

        if (user.getRole() == Role.AUTORITE_CONTRACTANTE) {
            Long acId = effectiveIdentityService.resolveAutoriteContractanteId(user, u);
            if (acId == null) {
                return false;
            }
            return dc.getAutoriteContractante() != null
                    && dc.getAutoriteContractante().getId().equals(acId);
        }

        if (user.getRole() == Role.AUTORITE_UPM || user.getRole() == Role.AUTORITE_UEP) {
            return demandeRepository.existsAccessByDelegue(u.getId(), dc.getId());
        }

        if (user.getRole() == Role.ENTREPRISE) {
            Long entId = effectiveIdentityService.resolveEntrepriseId(user, u);
            if (entId == null) {
                return false;
            }
            return dc.getEntreprise() != null
                    && dc.getEntreprise().getId().equals(entId);
        }

        return true;
    }

    @Transactional
    public DemandeCorrectionDto create(CreateDemandeCorrectionRequest request, AuthenticatedUser user) {
        boolean brouillon = Boolean.TRUE.equals(request.getBrouillon());
        if (!brouillon && (request.getModeleFiscal() == null || request.getDqe() == null)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le modèle fiscal et le DQE sont obligatoires (ou utilisez brouillon=true)");
        }

        AutoriteContractante autorite = autoriteRepository.findById(request.getAutoriteContractanteId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Autorité contractante non trouvée"));
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise non trouvée"));
        Convention convention = conventionRepository.findById(request.getConventionId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée"));
        Marche marche = null;
        if (request.getMarcheId() != null) {
            marche = marcheRepository.findById(request.getMarcheId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Marché non trouvé: " + request.getMarcheId()));
            DemandeCorrection liee = marche.getDemandeCorrection();
            if (liee != null) {
                if (liee.getStatut() != StatutDemande.ANNULEE) {
                    throw ApiException.conflict(ApiErrorCode.MARCHE_DEMANDE_ACTIVE,
                            "Le marché est déjà associé à une demande de correction active",
                            Map.of(
                                    "code", ApiErrorCode.MARCHE_DEMANDE_ACTIVE,
                                    "marcheId", marche.getId(),
                                    "demandeCorrectionId", liee.getId()));
                }
                detachMarcheFromCancelledDemande(liee, marche);
            }

            if (marche.getConvention() == null || marche.getConvention().getId() == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le marché n'est rattaché à aucune convention");
            }
            if (!marche.getConvention().getId().equals(request.getConventionId())) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le marché n'appartient pas à la convention sélectionnée");
            }

            Long marcheAcId = marche.getConvention().getAutoriteContractante() != null
                    ? marche.getConvention().getAutoriteContractante().getId()
                    : null;
            if (marcheAcId == null || !marcheAcId.equals(request.getAutoriteContractanteId())) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Le marché est hors périmètre de l'autorité contractante sélectionnée");
            }
        }
        StatutDemande initial = brouillon ? StatutDemande.BROUILLON : StatutDemande.RECUE;
        DemandeCorrection entity = DemandeCorrection.builder()
                .numero(generateNumero())
                .dateDepot(Instant.now())
                .statut(initial)
                .autoriteContractante(autorite)
                .entreprise(entreprise)
                .convention(convention)
                .build();
        if (marche != null) {
            entity.setMarche(marche);
            marche.setDemandeCorrection(entity);
        }
        ModeleFiscal modeleFiscal = toModeleFiscalEntity(request.getModeleFiscal(), entity);
        Dqe dqe = toDqeEntity(request.getDqe(), entity);
        entity.setModeleFiscal(modeleFiscal);
        entity.setDqe(dqe);
        entity = demandeRepository.save(entity);

        dossierGedService.ensureCreatedForDemandeCorrection(entity.getId());

        DemandeCorrectionDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "DemandeCorrection", String.valueOf(entity.getId()), result);
        if (!brouillon) {
            notifyDemandeCorrection(entity, StatutDemande.RECUE, null, user, false);
        }
        return result;
    }

    private static boolean isEvaluationNotStarted(DemandeCorrection d) {
        return d != null && !d.isValidationDgd() && !d.isValidationDgtcp() && !d.isValidationDgi() && !d.isValidationDgb();
    }

    private static final Set<StatutDemande> STATUTS_EDITABLE_CONTENT = EnumSet.of(
            StatutDemande.BROUILLON, StatutDemande.RECUE, StatutDemande.INCOMPLETE);

    private void assertDemandeCorrectionContentEditable(DemandeCorrection d) {
        if (d == null) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande introuvable");
        }
        if (!isEvaluationNotStarted(d)) {
            throw ApiException.conflict(ApiErrorCode.DEMANDE_NON_EDITABLE,
                    "Modification impossible : le traitement par les services a déjà commencé");
        }
        if (!STATUTS_EDITABLE_CONTENT.contains(d.getStatut())) {
            throw ApiException.conflict(ApiErrorCode.DEMANDE_NON_EDITABLE,
                    "Modification impossible dans le statut: " + d.getStatut());
        }
    }

    private void assertDeposantPeutModifierDemande(AuthenticatedUser user, DemandeCorrection d) {
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        if (user.getRole() == Role.ENTREPRISE) {
            Long entId = effectiveIdentityService.resolveEntrepriseId(user, u);
            if (entId == null || d.getEntreprise() == null || !entId.equals(d.getEntreprise().getId())) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Seule l'entreprise dépositaire peut modifier cette demande");
            }
            return;
        }
        if (user.getRole() == Role.AUTORITE_CONTRACTANTE) {
            Long acId = effectiveIdentityService.resolveAutoriteContractanteId(user, u);
            if (acId == null || d.getAutoriteContractante() == null || !acId.equals(d.getAutoriteContractante().getId())) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Seule l'autorité contractante liée peut modifier cette demande");
            }
            return;
        }
        throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seuls l'entreprise ou l'autorité contractante peuvent modifier cette demande");
    }

    private void assertSoumissionCorrectionPret(DemandeCorrection entity) {
        if (entity.getModeleFiscal() == null || entity.getDqe() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Modèle fiscal et DQE obligatoires pour la soumission");
        }
    }

    /**
     * Passe une demande {@link StatutDemande#BROUILLON} → {@link StatutDemande#RECUE} (notification, même effet qu'une création directe).
     */
    @Transactional
    public DemandeCorrectionDto soumettreBrouillon(Long id, AuthenticatedUser user) {
        DemandeCorrection entity = demandeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + id));
        if (!canAccessDemandeCorrection(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: demande hors périmètre");
        }
        assertDeposantPeutModifierDemande(user, entity);
        if (entity.getStatut() != StatutDemande.BROUILLON) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Soumission réservée aux demandes en brouillon (statut actuel: " + entity.getStatut() + ")");
        }
        assertSoumissionCorrectionPret(entity);
        workflow.validateTransition(StatutDemande.BROUILLON, StatutDemande.RECUE);
        entity.setStatut(StatutDemande.RECUE);
        entity = demandeRepository.save(entity);
        DemandeCorrectionDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "DemandeCorrection", String.valueOf(id), result);
        notifyDemandeCorrection(entity, StatutDemande.RECUE, null, user, false);
        return result;
    }

    /**
     * Suppression définitive d'un brouillon uniquement. Les demandes déjà soumises se gèrent par {@link StatutDemande#ANNULEE}.
     */
    @Transactional
    public void deleteBrouillon(Long id, AuthenticatedUser user) {
        DemandeCorrection entity = demandeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + id));
        if (!canAccessDemandeCorrection(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: demande hors périmètre");
        }
        assertDeposantPeutModifierDemande(user, entity);
        if (entity.getStatut() != StatutDemande.BROUILLON) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Suppression réservée aux brouillons. Pour une demande soumise, utilisez l'annulation (statut ANNULEE).");
        }
        Marche marche = entity.getMarche();
        if (marche != null) {
            entity.setMarche(null);
            marche.setDemandeCorrection(null);
            marcheRepository.save(marche);
        }
        dossierGedService.deleteDossierForDemandeCorrectionIfPresent(id);
        auditService.log(AuditAction.DELETE, "DemandeCorrection", String.valueOf(id), null);
        demandeRepository.delete(entity);
    }

    @Transactional
    public DemandeCorrectionDto update(Long id, UpdateDemandeCorrectionRequest request, AuthenticatedUser user) {
        DemandeCorrection entity = demandeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + id));
        if (!canAccessDemandeCorrection(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: demande hors périmètre");
        }
        assertDeposantPeutModifierDemande(user, entity);
        assertDemandeCorrectionContentEditable(entity);

        AutoriteContractante autorite = autoriteRepository.findById(request.getAutoriteContractanteId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Autorité contractante non trouvée"));
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise non trouvée"));
        Convention convention = conventionRepository.findById(request.getConventionId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée"));
        entity.setAutoriteContractante(autorite);
        entity.setEntreprise(entreprise);
        entity.setConvention(convention);

        Marche marcheCible = null;
        if (request.getMarcheId() != null) {
            marcheCible = marcheRepository.findById(request.getMarcheId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Marché non trouvé: " + request.getMarcheId()));
            DemandeCorrection liee = marcheCible.getDemandeCorrection();
            if (liee != null && !liee.getId().equals(entity.getId())) {
                if (liee.getStatut() != StatutDemande.ANNULEE) {
                    throw ApiException.conflict(ApiErrorCode.MARCHE_DEMANDE_ACTIVE,
                            "Le marché est déjà associé à une autre demande active",
                            Map.of("code", ApiErrorCode.MARCHE_DEMANDE_ACTIVE,
                                    "marcheId", marcheCible.getId(),
                                    "demandeCorrectionId", liee.getId()));
                }
                detachMarcheFromCancelledDemande(liee, marcheCible);
            }
            if (marcheCible.getConvention() == null || !marcheCible.getConvention().getId().equals(request.getConventionId())) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le marché n'appartient pas à la convention sélectionnée");
            }
            Long marcheAcId = marcheCible.getConvention().getAutoriteContractante() != null
                    ? marcheCible.getConvention().getAutoriteContractante().getId() : null;
            if (marcheAcId == null || !marcheAcId.equals(request.getAutoriteContractanteId())) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Le marché est hors périmètre de l'autorité contractante sélectionnée");
            }
        }

        if (entity.getMarche() != null) {
            Marche ancien = entity.getMarche();
            if (marcheCible == null || !ancien.getId().equals(marcheCible.getId())) {
                entity.setMarche(null);
                ancien.setDemandeCorrection(null);
                marcheRepository.save(ancien);
            }
        }
        if (marcheCible != null) {
            entity.setMarche(marcheCible);
            marcheCible.setDemandeCorrection(entity);
            marcheRepository.save(marcheCible);
        }

        entity.setModeleFiscal(null);
        entity.setDqe(null);
        demandeRepository.saveAndFlush(entity);

        ModeleFiscal modeleFiscal = toModeleFiscalEntity(request.getModeleFiscal(), entity);
        Dqe dqe = toDqeEntity(request.getDqe(), entity);
        entity.setModeleFiscal(modeleFiscal);
        entity.setDqe(dqe);
        entity = demandeRepository.save(entity);

        DemandeCorrectionDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "DemandeCorrection", String.valueOf(id), result);
        return result;
    }

    private void detachMarcheFromCancelledDemande(DemandeCorrection cancelled, Marche marche) {
        if (cancelled == null || marche == null) {
            return;
        }
        cancelled.setMarcheIdTrace(marche.getId());
        cancelled.setMarche(null);
        marche.setDemandeCorrection(null);
        demandeRepository.save(cancelled);
        marcheRepository.save(marche);
    }

    private void detachMarcheOnAnnulation(DemandeCorrection entity) {
        Marche marche = entity.getMarche();
        if (marche == null) {
            return;
        }
        entity.setMarcheIdTrace(marche.getId());
        entity.setMarche(null);
        marche.setDemandeCorrection(null);
        marcheRepository.save(marche);
    }

    /**
     * Réactivation ANNULEE → RECUE : le marché historique ({@link DemandeCorrection#getMarcheIdTrace()}) ne doit pas
     * être déjà rattaché à une autre demande.
     */
    private void assertMarcheDisponiblePourReactivation(DemandeCorrection demandeReactivee) {
        Long traceId = demandeReactivee.getMarcheIdTrace();
        if (traceId == null) {
            return;
        }
        marcheRepository.findById(traceId).ifPresent(m -> {
            DemandeCorrection occupe = m.getDemandeCorrection();
            if (occupe != null && occupe.getId() != null && !occupe.getId().equals(demandeReactivee.getId())) {
                String num = occupe.getNumero() != null ? occupe.getNumero() : String.valueOf(occupe.getId());
                throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Réactivation impossible : le marché est déjà rattaché à une autre demande active (" + num + ").");
            }
        });
    }

    /** {@code true} si aucun conflit avec une autre demande sur le marché tracé. */
    private boolean isMarcheLibrePourDemande(DemandeCorrection d) {
        Long traceId = d.getMarcheIdTrace();
        if (traceId == null) {
            return true;
        }
        return marcheRepository.findById(traceId)
                .map(m -> {
                    DemandeCorrection occupe = m.getDemandeCorrection();
                    return occupe == null
                            || occupe.getId() == null
                            || occupe.getId().equals(d.getId());
                })
                .orElse(true);
    }

    @Transactional
    public DemandeCorrectionDto updateStatut(Long id, StatutDemande statut, AuthenticatedUser user, String motifRejet, Boolean decisionFinale) {
        DemandeCorrection entity = demandeRepository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + id));

        if (!canAccessDemandeCorrection(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: demande hors périmètre");
        }

        if (statut == StatutDemande.RECUE && entity.getStatut() == StatutDemande.ANNULEE) {
            if (user == null || user.getRole() != Role.AUTORITE_CONTRACTANTE) {
                throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                        "Seule l'autorité contractante peut réactiver une demande annulée");
            }
            assertMarcheDisponiblePourReactivation(entity);
            resetParallelValidations(entity);
            entity.setStatut(StatutDemande.RECUE);
            entity.setMotifRejet(null);
            entity = demandeRepository.save(entity);
            DemandeCorrectionDto reactivated = toDto(entity);
            auditService.log(AuditAction.UPDATE, "DemandeCorrection", String.valueOf(id), reactivated);
            notifyDemandeCorrection(entity, StatutDemande.RECUE, null, user, false);
            return reactivated;
        }

        boolean soumissionDepuisBrouillon = user != null && user.getRole() != null
                && (user.getRole() == Role.AUTORITE_CONTRACTANTE || user.getRole() == Role.ENTREPRISE)
                && entity.getStatut() == StatutDemande.BROUILLON
                && statut == StatutDemande.RECUE;

        if (user != null && user.getRole() != null
                && (user.getRole() == Role.AUTORITE_CONTRACTANTE || user.getRole() == Role.ENTREPRISE)
                && statut != StatutDemande.ANNULEE
                && !soumissionDepuisBrouillon) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: action non autorisée");
        }

        workflow.validateTransition(entity.getStatut(), statut);

        if (soumissionDepuisBrouillon) {
            assertSoumissionCorrectionPret(entity);
        }

        if (statut == StatutDemande.ANNULEE) {
            detachMarcheOnAnnulation(entity);
        }

        if (statut == StatutDemande.RECEVABLE || statut == StatutDemande.EN_EVALUATION) {
            requirementValidator.assertRequiredDocumentsPresent(
                    ProcessusDocument.CORRECTION_OFFRE_FISCALE,
                    documentService.findActiveDocumentTypes(entity.getId())
            );
        }

        boolean finale = Boolean.TRUE.equals(decisionFinale);
        if (statut == StatutDemande.ADOPTEE) {
            applyParallelValidation(entity, user);
            if (finale) {
                assertFinalDecisionRole(user);
                entity.setStatut(StatutDemande.ADOPTEE);
                entity.setMotifRejet(null);
            }
        } else if (statut == StatutDemande.REJETEE) {
            if (motifRejet == null || motifRejet.isBlank()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de rejet est obligatoire");
            }
            DemandeCorrectionRejet rejet = createRejet(entity, user, motifRejet);
            entity.getRejets().add(rejet);
            if (finale) {
                assertFinalDecisionRole(user);
                entity.setStatut(StatutDemande.REJETEE);
                entity.setMotifRejet(motifRejet);
            }
        } else if (finale) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Décision finale non supportée pour le statut: " + statut);
        } else {
            entity.setStatut(statut);
        }
        entity = demandeRepository.save(entity);
        DemandeCorrectionDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "DemandeCorrection", String.valueOf(id), result);
        notifyDemandeCorrection(entity, statut, motifRejet, user, finale);
        return result;
    }

    private void applyParallelValidation(DemandeCorrection entity, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Role role = user.getRole();
        if (role == Role.PRESIDENT) {
            return;
        }
        if (role == Role.DGD) {
            entity.setValidationDgd(true);
            entity.setValidationDgdUserId(user.getUserId());
            entity.setValidationDgdDate(Instant.now());
        } else if (role == Role.DGTCP) {
            entity.setValidationDgtcp(true);
            entity.setValidationDgtcpUserId(user.getUserId());
            entity.setValidationDgtcpDate(Instant.now());
        } else if (role == Role.DGI) {
            entity.setValidationDgi(true);
            entity.setValidationDgiUserId(user.getUserId());
            entity.setValidationDgiDate(Instant.now());
        } else if (role == Role.DGB) {
            entity.setValidationDgb(true);
            entity.setValidationDgbUserId(user.getUserId());
            entity.setValidationDgbDate(Instant.now());
        } else {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Rôle non autorisé pour la validation: " + role);
        }
    }

    private void assertFinalDecisionRole(AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP && user.getRole() != Role.PRESIDENT) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Rôle non autorisé pour la décision finale: " + user.getRole());
        }
    }

    private void resetParallelValidations(DemandeCorrection d) {
        d.setValidationDgd(false);
        d.setValidationDgdUserId(null);
        d.setValidationDgdDate(null);
        d.setValidationDgtcp(false);
        d.setValidationDgtcpUserId(null);
        d.setValidationDgtcpDate(null);
        d.setValidationDgi(false);
        d.setValidationDgiUserId(null);
        d.setValidationDgiDate(null);
        d.setValidationDgb(false);
        d.setValidationDgbUserId(null);
        d.setValidationDgbDate(null);
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findByAutoriteContractante(Long autoriteId, AuthenticatedUser user) {
        List<DemandeCorrection> raw = demandeRepository.findByAutoriteContractanteIdOrderByDateDepotDescIdDesc(autoriteId);
        raw = filterBrouillonPourConsultationAutorite(raw, autoriteId, user);
        return raw.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Brouillons : visibles seulement si l’appelant est un utilisateur AC de cette même autorité.
     */
    private List<DemandeCorrection> filterBrouillonPourConsultationAutorite(
            List<DemandeCorrection> list, Long autoriteId, AuthenticatedUser user) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        boolean acConsulteSaStructure = user != null && user.getUserId() != null
                && Role.AUTORITE_CONTRACTANTE.equals(user.getRole())
                && utilisateurRepository.findById(user.getUserId())
                .map(u -> {
                    Long acId = effectiveIdentityService.resolveAutoriteContractanteId(user, u);
                    return acId != null && autoriteId != null && autoriteId.equals(acId);
                })
                .orElse(false);
        if (acConsulteSaStructure) {
            return list;
        }
        return filterBrouillonHorsAc(list);
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findByEntreprise(Long entrepriseId) {
        return filterBrouillonHorsAc(demandeRepository.findByEntrepriseIdOrderByDateDepotDescIdDesc(entrepriseId))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findByDelegue(Long delegueId, AuthenticatedUser user) {
        if (user == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if ((user.getRole() == Role.AUTORITE_UPM || user.getRole() == Role.AUTORITE_UEP)
                && (delegueId == null || !delegueId.equals(user.getUserId()))) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: vous ne pouvez consulter que vos propres demandes");
        }
        return filterBrouillonHorsAc(demandeRepository.findByDelegueId(delegueId))
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DemandeCorrectionDto> findByStatut(StatutDemande statut, AuthenticatedUser user) {
        if (statut == StatutDemande.BROUILLON) {
            if (user == null || user.getUserId() == null || user.getRole() != Role.AUTORITE_CONTRACTANTE) {
                return List.of();
            }
            Utilisateur u = utilisateurRepository.findById(user.getUserId()).orElse(null);
            Long acId = u != null ? effectiveIdentityService.resolveAutoriteContractanteId(user, u) : null;
            if (acId == null) {
                return List.of();
            }
            return demandeRepository
                    .findByAutoriteContractanteIdAndStatutOrderByDateDepotDescIdDesc(
                            acId, StatutDemande.BROUILLON)
                    .stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }
        return demandeRepository.findByStatutOrderByDateDepotDescIdDesc(statut).stream().map(this::toDto).collect(Collectors.toList());
    }

    private DemandeCorrectionDto toDto(DemandeCorrection d) {
        return DemandeCorrectionDto.builder()
                .id(d.getId())
                .numero(d.getNumero())
                .dateDepot(d.getDateDepot())
                .statut(d.getStatut())
                .validationDgd(d.isValidationDgd())
                .validationDgtcp(d.isValidationDgtcp())
                .validationDgi(d.isValidationDgi())
                .validationDgb(d.isValidationDgb())
                .validationDgdUserId(d.getValidationDgdUserId())
                .validationDgdDate(d.getValidationDgdDate())
                .validationDgtcpUserId(d.getValidationDgtcpUserId())
                .validationDgtcpDate(d.getValidationDgtcpDate())
                .validationDgiUserId(d.getValidationDgiUserId())
                .validationDgiDate(d.getValidationDgiDate())
                .validationDgbUserId(d.getValidationDgbUserId())
                .validationDgbDate(d.getValidationDgbDate())
                .motifRejet(d.getMotifRejet())
                .dateCreation(d.getDateCreation())
                .dateModification(d.getDateModification())
                .autoriteContractanteId(d.getAutoriteContractante() != null ? d.getAutoriteContractante().getId() : null)
                .autoriteContractanteNom(d.getAutoriteContractante() != null ? d.getAutoriteContractante().getNom() : null)
                .entrepriseId(d.getEntreprise() != null ? d.getEntreprise().getId() : null)
                .entrepriseRaisonSociale(d.getEntreprise() != null ? d.getEntreprise().getRaisonSociale() : null)
                .conventionId(d.getConvention() != null ? d.getConvention().getId() : null)
                .marcheId(d.getMarche() != null ? d.getMarche().getId() : null)
                .marcheIdTrace(d.getMarcheIdTrace())
                .marcheReactivable(d.getStatut() == StatutDemande.ANNULEE
                        ? isMarcheLibrePourDemande(d)
                        : null)
                .modeleFiscal(toModeleFiscalDto(d.getModeleFiscal()))
                .dqe(toDqeDto(d.getDqe()))
                .marche(toMarcheDto(d.getMarche()))
                .documents(d.getDocuments() != null ? d.getDocuments().stream().map(this::documentToDto).collect(Collectors.toList()) : List.of())
                .rejets(d.getRejets() != null ? d.getRejets().stream().map(this::toRejetDto).collect(Collectors.toList()) : List.of())
                .decisions(d.getDecisions() != null ? d.getDecisions().stream().map(this::toDecisionDto).collect(Collectors.toList()) : List.of())
                .build();
    }

    private DemandeCorrectionRejet createRejet(DemandeCorrection entity, AuthenticatedUser user, String motifRejet) {
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié pour le rejet");
        }
        Utilisateur utilisateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        return DemandeCorrectionRejet.builder()
                .demandeCorrection(entity)
                .utilisateur(utilisateur)
                .motifRejet(motifRejet)
                .dateRejet(Instant.now())
                .build();
    }

    private DemandeCorrectionRejetDto toRejetDto(DemandeCorrectionRejet rejet) {
        return DemandeCorrectionRejetDto.builder()
                .id(rejet.getId())
                .motifRejet(rejet.getMotifRejet())
                .dateRejet(rejet.getDateRejet())
                .utilisateurId(rejet.getUtilisateur() != null ? rejet.getUtilisateur().getId() : null)
                .utilisateurNom(rejet.getUtilisateur() != null ? rejet.getUtilisateur().getNomComplet() : null)
                .build();
    }

    private DecisionCorrectionDto toDecisionDto(DecisionCorrection decision) {
        return DecisionCorrectionDto.builder()
                .id(decision.getId())
                .role(decision.getRole())
                .decision(decision.getDecision())
                .motifRejet(decision.getMotifRejet())
                .documentsDemandes(decision.getDocumentsDemandes())
                .dateDecision(decision.getDateDecision())
                .rejetTempStatus(decision.getRejetTempStatus())
                .rejetTempResolvedAt(decision.getRejetTempResolvedAt())
                .utilisateurId(decision.getUtilisateur() != null ? decision.getUtilisateur().getId() : null)
                .utilisateurNom(decision.getUtilisateur() != null ? decision.getUtilisateur().getNomComplet() : null)
                .rejetTempResponses(decision.getRejetTempResponses() != null
                        ? decision.getRejetTempResponses().stream().map(this::toRejetTempResponseDto).collect(Collectors.toList())
                        : java.util.List.of())
                .build();
    }

    private mr.gov.finances.sgci.web.dto.RejetTempResponseDto toRejetTempResponseDto(mr.gov.finances.sgci.domain.entity.RejetTempResponse entity) {
        return mr.gov.finances.sgci.web.dto.RejetTempResponseDto.builder()
                .id(entity.getId())
                .message(entity.getMessage())
                .documentUrl(entity.getDocumentUrl())
                .documentType(entity.getDocumentType())
                .documentVersion(entity.getDocumentVersion())
                .createdAt(entity.getCreatedAt())
                .utilisateurId(entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null)
                .utilisateurNom(entity.getUtilisateur() != null ? entity.getUtilisateur().getNomComplet() : null)
                .build();
    }

    private ModeleFiscal toModeleFiscalEntity(ModeleFiscalDto dto, DemandeCorrection demande) {
        ModeleFiscal modele = ModeleFiscal.builder()
                .referenceDossier(dto != null ? dto.getReferenceDossier() : null)
                .typeProjet(dto != null ? dto.getTypeProjet() : null)
                .afficherNomenclature(dto != null ? dto.getAfficherNomenclature() : null)
                .dateCreation(dto != null ? dto.getDateCreation() : null)
                .dateModification(dto != null ? dto.getDateModification() : null)
                .demandeCorrection(demande)
                .build();

        List<LigneImportation> lignes = dto != null && dto.getImportations() != null
                ? dto.getImportations().stream().map(this::toLigneImportationEntity).collect(Collectors.toList())
                : new ArrayList<>();
        lignes.forEach(ligne -> ligne.setModeleFiscal(modele));
        modele.setImportations(lignes);

        FiscaliteInterieure fiscalite = dto != null && dto.getFiscaliteInterieure() != null
                ? toFiscaliteInterieureEntity(dto.getFiscaliteInterieure())
                : FiscaliteInterieure.builder().build();
        fiscalite.setModeleFiscal(modele);
        modele.setFiscaliteInterieure(fiscalite);

        Recapitulatif recapitulatif = dto != null && dto.getRecapitulatif() != null
                ? toRecapitulatifEntity(dto.getRecapitulatif())
                : Recapitulatif.builder().build();
        recapitulatif.setModeleFiscal(modele);
        modele.setRecapitulatif(recapitulatif);

        return modele;
    }

    private LigneImportation toLigneImportationEntity(LigneImportationDto dto) {
        return LigneImportation.builder()
                .designation(dto.getDesignation())
                .unite(dto.getUnite())
                .quantite(dto.getQuantite())
                .prixUnitaire(dto.getPrixUnitaire())
                .nomenclature(dto.getNomenclature())
                .tauxDD(dto.getTauxDD())
                .tauxRS(dto.getTauxRS())
                .tauxPSC(dto.getTauxPSC())
                .tauxTVA(dto.getTauxTVA())
                .valeurDouane(dto.getValeurDouane())
                .dd(dto.getDd())
                .rs(dto.getRs())
                .psc(dto.getPsc())
                .baseTVA(dto.getBaseTVA())
                .tvaDouane(dto.getTvaDouane())
                .totalTaxes(dto.getTotalTaxes())
                .build();
    }

    private FiscaliteInterieure toFiscaliteInterieureEntity(FiscaliteInterieureDto dto) {
        return FiscaliteInterieure.builder()
                .montantHT(dto.getMontantHT())
                .tauxTVA(dto.getTauxTVA())
                .autresTaxes(dto.getAutresTaxes())
                .tvaCollectee(dto.getTvaCollectee())
                .tvaDeductible(dto.getTvaDeductible())
                .tvaNette(dto.getTvaNette())
                .creditInterieur(dto.getCreditInterieur())
                .build();
    }

    private Recapitulatif toRecapitulatifEntity(RecapitulatifDto dto) {
        return Recapitulatif.builder()
                .creditExterieur(dto.getCreditExterieur())
                .creditInterieur(dto.getCreditInterieur())
                .creditTotal(dto.getCreditTotal())
                .build();
    }

    private Dqe toDqeEntity(DqeDto dto, DemandeCorrection demande) {
        Dqe dqe = Dqe.builder()
                .numeroAAOI(dto != null ? dto.getNumeroAAOI() : null)
                .projet(dto != null ? dto.getProjet() : null)
                .lot(dto != null ? dto.getLot() : null)
                .tauxTVA(dto != null ? dto.getTauxTVA() : null)
                .totalHT(dto != null ? dto.getTotalHT() : null)
                .montantTVA(dto != null ? dto.getMontantTVA() : null)
                .totalTTC(dto != null ? dto.getTotalTTC() : null)
                .demandeCorrection(demande)
                .build();

        List<DqeLigne> lignes = dto != null && dto.getLignes() != null
                ? dto.getLignes().stream().map(this::toDqeLigneEntity).collect(Collectors.toList())
                : new ArrayList<>();
        lignes.forEach(ligne -> ligne.setDqe(dqe));
        dqe.setLignes(lignes);
        return dqe;
    }

    private DqeLigne toDqeLigneEntity(DqeLigneDto dto) {
        return DqeLigne.builder()
                .designation(dto.getDesignation())
                .unite(dto.getUnite())
                .quantite(dto.getQuantite())
                .prixUnitaireHT(dto.getPrixUnitaireHT())
                .montantHT(dto.getMontantHT())
                .build();
    }

    private ModeleFiscalDto toModeleFiscalDto(ModeleFiscal modele) {
        if (modele == null) {
            return null;
        }
        return ModeleFiscalDto.builder()
                .id(modele.getId())
                .referenceDossier(modele.getReferenceDossier())
                .typeProjet(modele.getTypeProjet())
                .afficherNomenclature(modele.getAfficherNomenclature())
                .dateCreation(modele.getDateCreation())
                .dateModification(modele.getDateModification())
                .importations(modele.getImportations() != null
                        ? modele.getImportations().stream().map(this::toLigneImportationDto).collect(Collectors.toList())
                        : List.of())
                .fiscaliteInterieure(modele.getFiscaliteInterieure() != null ? toFiscaliteInterieureDto(modele.getFiscaliteInterieure()) : null)
                .recapitulatif(modele.getRecapitulatif() != null ? toRecapitulatifDto(modele.getRecapitulatif()) : null)
                .build();
    }

    private LigneImportationDto toLigneImportationDto(LigneImportation entity) {
        return LigneImportationDto.builder()
                .id(entity.getId())
                .designation(entity.getDesignation())
                .unite(entity.getUnite())
                .quantite(entity.getQuantite())
                .prixUnitaire(entity.getPrixUnitaire())
                .nomenclature(entity.getNomenclature())
                .tauxDD(entity.getTauxDD())
                .tauxRS(entity.getTauxRS())
                .tauxPSC(entity.getTauxPSC())
                .tauxTVA(entity.getTauxTVA())
                .valeurDouane(entity.getValeurDouane())
                .dd(entity.getDd())
                .rs(entity.getRs())
                .psc(entity.getPsc())
                .baseTVA(entity.getBaseTVA())
                .tvaDouane(entity.getTvaDouane())
                .totalTaxes(entity.getTotalTaxes())
                .build();
    }

    private FiscaliteInterieureDto toFiscaliteInterieureDto(FiscaliteInterieure entity) {
        return FiscaliteInterieureDto.builder()
                .id(entity.getId())
                .montantHT(entity.getMontantHT())
                .tauxTVA(entity.getTauxTVA())
                .autresTaxes(entity.getAutresTaxes())
                .tvaCollectee(entity.getTvaCollectee())
                .tvaDeductible(entity.getTvaDeductible())
                .tvaNette(entity.getTvaNette())
                .creditInterieur(entity.getCreditInterieur())
                .build();
    }

    private RecapitulatifDto toRecapitulatifDto(Recapitulatif entity) {
        return RecapitulatifDto.builder()
                .id(entity.getId())
                .creditExterieur(entity.getCreditExterieur())
                .creditInterieur(entity.getCreditInterieur())
                .creditTotal(entity.getCreditTotal())
                .build();
    }

    private DqeDto toDqeDto(Dqe dqe) {
        if (dqe == null) {
            return null;
        }
        return DqeDto.builder()
                .id(dqe.getId())
                .numeroAAOI(dqe.getNumeroAAOI())
                .projet(dqe.getProjet())
                .lot(dqe.getLot())
                .tauxTVA(dqe.getTauxTVA())
                .totalHT(dqe.getTotalHT())
                .montantTVA(dqe.getMontantTVA())
                .totalTTC(dqe.getTotalTTC())
                .lignes(dqe.getLignes() != null
                        ? dqe.getLignes().stream().map(this::toDqeLigneDto).collect(Collectors.toList())
                        : List.of())
                .build();
    }

    private DqeLigneDto toDqeLigneDto(DqeLigne entity) {
        return DqeLigneDto.builder()
                .id(entity.getId())
                .designation(entity.getDesignation())
                .unite(entity.getUnite())
                .quantite(entity.getQuantite())
                .prixUnitaireHT(entity.getPrixUnitaireHT())
                .montantHT(entity.getMontantHT())
                .build();
    }

    private MarcheDto toMarcheDto(Marche marche) {
        if (marche == null) {
            return null;
        }
        return MarcheDto.builder()
                .id(marche.getId())
                .conventionId(marche.getConvention() != null ? marche.getConvention().getId() : null)
                .demandeCorrectionId(marche.getDemandeCorrection() != null ? marche.getDemandeCorrection().getId() : null)
                .numeroMarche(marche.getNumeroMarche())
                .intitule(marche.getIntitule())
                .dateSignature(marche.getDateSignature())
                .montantContratHt(marche.getMontantContratHt())
                .statut(marche.getStatut())
                .build();
    }

    private DocumentDto documentToDto(Document doc) {
        return DocumentDto.builder()
                .id(doc.getId())
                .type(doc.getType())
                .nomFichier(doc.getNomFichier())
                .chemin(doc.getChemin())
                .dateUpload(doc.getDateUpload())
                .taille(doc.getTaille())
                .build();
    }

    private void notifyDemandeCorrection(DemandeCorrection entity,
                                         StatutDemande statut,
                                         String motifRejet,
                                         AuthenticatedUser user,
                                         boolean finale) {
        List<Long> userIds = resolveRelatedUserIds(entity);
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("statut", statut.name());
        payload.put("finale", finale);
        if (motifRejet != null && !motifRejet.isBlank()) {
            payload.put("motifRejet", motifRejet);
        }
        if (user != null && user.getRole() != null) {
            payload.put("acteurRole", user.getRole().name());
            payload.put("acteurUserId", user.getUserId());
        }
        String message = "Demande de correction " + entity.getNumero() + " statut: " + statut;
        notificationService.notifyUsers(userIds, NotificationType.CORRECTION_STATUT_CHANGE,
                "DemandeCorrection", entity.getId(), message, payload);
    }

    private List<Long> resolveRelatedUserIds(DemandeCorrection entity) {
        if (entity == null) {
            return List.of();
        }
        List<Long> entrepriseUsers = entity.getEntreprise() != null
                ? utilisateurRepository.findByEntrepriseId(entity.getEntreprise().getId()).stream()
                .map(Utilisateur::getId)
                .collect(Collectors.toList())
                : List.of();
        List<Long> autoriteUsers = entity.getAutoriteContractante() != null
                ? utilisateurRepository.findByAutoriteContractanteId(entity.getAutoriteContractante().getId()).stream()
                .map(Utilisateur::getId)
                .collect(Collectors.toList())
                : List.of();
        List<Long> commission = Stream.of(Role.PRESIDENT, Role.DGD, Role.DGTCP, Role.DGI, Role.DGB)
                .flatMap(r -> utilisateurRepository.findByRole(r).stream())
                .map(Utilisateur::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return Stream.of(entrepriseUsers.stream(), autoriteUsers.stream(), commission.stream())
                .flatMap(s -> s)
                .distinct()
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean userCanAccessDemandeCorrection(Long demandeId, AuthenticatedUser user) {
        return canAccessDemandeCorrection(demandeId, user);
    }

    public void notifyCorrectionStatutChange(DemandeCorrection entity,
                                            StatutDemande statut,
                                            AuthenticatedUser user,
                                            String motifRejet,
                                            boolean finale) {
        notifyDemandeCorrection(entity, statut, motifRejet, user, finale);
    }
}
