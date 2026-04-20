package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.LettreCorrection;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.EffectiveIdentityService;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DecisionCertificatCreditRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentCertificatCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.LettreCorrectionRepository;
import mr.gov.finances.sgci.repository.TvaDeductibleStockRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.web.dto.CertificatCreditDto;
import mr.gov.finances.sgci.web.dto.CreateCertificatCreditRequest;
import mr.gov.finances.sgci.web.dto.UpdateCertificatCreditMontantsRequest;
import mr.gov.finances.sgci.workflow.CertificatCreditWorkflow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CertificatCreditService {

    /** Tolérance MRU sur les totaux récap. (arrondis manuels). */
    private static final BigDecimal RECAP_TOLERANCE_MRU = new BigDecimal("1");

    private final CertificatCreditRepository repository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final LettreCorrectionRepository lettreCorrectionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final UtilisationCreditRepository utilisationCreditRepository;
    private final UtilisationCreditService utilisationCreditService;
    private final DocumentCertificatCreditRepository documentCertificatCreditRepository;
    private final DecisionCertificatCreditRepository decisionCertificatCreditRepository;
    private final TvaDeductibleStockRepository tvaDeductibleStockRepository;
    private final CertificatCreditWorkflow workflow;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final DocumentCertificatCreditService documentService;
    private final DocumentRequirementValidator requirementValidator;
    private final DossierGedService dossierGedService;
    private final EffectiveIdentityService effectiveIdentityService;

    @Transactional(readOnly = true)
    public List<CertificatCreditDto> findAll(AuthenticatedUser user) {
        List<CertificatCredit> list = resolveCertificatList(user, null);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CertificatCreditDto findById(Long id, AuthenticatedUser user) {
        CertificatCredit c = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        if (!canAccessCertificat(c.getId(), user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: certificat hors périmètre");
        }
        return toDto(c);
    }

    @Transactional(readOnly = true)
    public List<CertificatCreditDto> findByEntreprise(Long entrepriseId) {
        return repository.findByEntrepriseIdOrderByDateEmissionDescIdDesc(entrepriseId).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CertificatCreditDto> findByStatut(StatutCertificat statut, AuthenticatedUser user) {
        List<CertificatCredit> list = resolveCertificatList(user, statut);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    private List<CertificatCredit> resolveCertificatList(AuthenticatedUser user, StatutCertificat statut) {
        if (user == null || user.getUserId() == null) {
            if (statut == null) {
                return repository.findAllByOrderByDateEmissionDescIdDesc();
            }
            return repository.findByStatutOrderByDateEmissionDescIdDesc(statut);
        }

        mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Role role = user.getRole();

        List<CertificatCredit> base;
        if (role == Role.AUTORITE_CONTRACTANTE) {
            Long acId = effectiveIdentityService.resolveAutoriteContractanteId(user, u);
            if (acId == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
            }
            base = repository.findAllByAutoriteContractanteId(acId);
        } else if (role == Role.AUTORITE_UPM || role == Role.AUTORITE_UEP) {
            base = repository.findAllByDelegueId(u.getId());
        } else if (role == Role.ENTREPRISE) {
            Long entId = effectiveIdentityService.resolveEntrepriseId(user, u);
            if (entId == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
            }
            base = repository.findByEntrepriseIdOrderByDateEmissionDescIdDesc(entId);
        } else {
            if (statut == null) {
                return repository.findAllByOrderByDateEmissionDescIdDesc();
            }
            return repository.findByStatutOrderByDateEmissionDescIdDesc(statut);
        }

        if (statut == null) {
            return base;
        }
        return base.stream().filter(c -> c.getStatut() == statut).collect(Collectors.toList());
    }

    private boolean canAccessCertificat(Long certificatId, AuthenticatedUser user) {
        if (certificatId == null) {
            return false;
        }
        if (user == null || user.getUserId() == null) {
            return true;
        }
        mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId()).orElse(null);
        if (u == null || user.getRole() == null) {
            return false;
        }

        if (user.getRole() == Role.AUTORITE_CONTRACTANTE) {
            Long acId = effectiveIdentityService.resolveAutoriteContractanteId(user, u);
            if (acId == null) {
                return false;
            }
            return repository.findById(certificatId)
                    .map(c -> c.getDemandeCorrection() != null
                            && c.getDemandeCorrection().getAutoriteContractante() != null
                            && c.getDemandeCorrection().getAutoriteContractante().getId().equals(acId))
                    .orElse(false);
        }

        if (user.getRole() == Role.AUTORITE_UPM || user.getRole() == Role.AUTORITE_UEP) {
            return repository.existsAccessByDelegue(u.getId(), certificatId);
        }

        if (user.getRole() == Role.ENTREPRISE) {
            Long entId = effectiveIdentityService.resolveEntrepriseId(user, u);
            if (entId == null) {
                return false;
            }
            return repository.findById(certificatId)
                    .map(c -> c.getEntreprise() != null
                            && c.getEntreprise().getId().equals(entId))
                    .orElse(false);
        }

        return true;
    }

    /**
     * Au plus un certificat non {@link StatutCertificat#ANNULE} par demande de correction.
     *
     * @param excludeCertificatId certificat courant à exclure (brouillon existant), ou {@code null} à la création
     */
    private void assertAtMostOneActiveCertificatPourDemande(Long demandeCorrectionId, Long excludeCertificatId) {
        if (demandeCorrectionId == null) {
            return;
        }
        long autres;
        if (excludeCertificatId == null) {
            autres = repository.countByDemandeCorrectionIdAndStatutNot(demandeCorrectionId, StatutCertificat.ANNULE);
        } else {
            autres = repository.countByDemandeCorrectionIdAndStatutNotAndIdNot(
                    demandeCorrectionId, StatutCertificat.ANNULE, excludeCertificatId);
        }
        if (autres > 0) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT,
                    "Un certificat de crédit actif existe déjà pour cette demande de correction");
        }
    }

    /** Mise à jour du contenu (PUT) : réservée aux déposants, pas aux acteurs de file. */
    private void assertDeposantPeutModifierDemandeMiseEnPlace(AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        Role r = user.getRole();
        if (r != Role.AUTORITE_CONTRACTANTE && r != Role.AUTORITE_UPM && r != Role.AUTORITE_UEP && r != Role.ENTREPRISE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Modification réservée au déposant (autorité contractante, délégué ou entreprise)");
        }
    }

    @Transactional
    public CertificatCreditDto create(CreateCertificatCreditRequest request) {
        boolean brouillon = Boolean.TRUE.equals(request.getBrouillon());
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise non trouvée"));

        if (!brouillon && request.getLettreCorrectionId() == null && request.getDemandeCorrectionId() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La demande de correction ou la lettre de correction est obligatoire pour la mise en place du crédit d'impôt");
        }

        LettreCorrection lettreCorrection = null;
        DemandeCorrection demandeCorrection = null;

        if (request.getLettreCorrectionId() != null) {
            lettreCorrection = lettreCorrectionRepository.findById(request.getLettreCorrectionId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Lettre de correction non trouvée: " + request.getLettreCorrectionId()));
            demandeCorrection = lettreCorrection.getFeuilleEvaluation() != null
                    ? lettreCorrection.getFeuilleEvaluation().getDemandeCorrection()
                    : null;
        } else if (request.getDemandeCorrectionId() != null) {
            demandeCorrection = demandeCorrectionRepository.findById(request.getDemandeCorrectionId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + request.getDemandeCorrectionId()));
        }

        if (!brouillon) {
            assertMiseEnPlaceTrigger(lettreCorrection, demandeCorrection);
        }

        if (demandeCorrection != null && demandeCorrection.getId() != null) {
            assertAtMostOneActiveCertificatPourDemande(demandeCorrection.getId(), null);
        }

        if (!brouillon && demandeCorrection != null && demandeCorrection.getEntreprise() != null
                && demandeCorrection.getEntreprise().getId() != null
                && !demandeCorrection.getEntreprise().getId().equals(entreprise.getId())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Incohérence: l'entreprise de la demande de correction ne correspond pas à l'entreprise du certificat");
        }

        String numero = "CERT-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal soldeCordon = request.getSoldeCordon() != null ? request.getSoldeCordon() : request.getMontantCordon();
        BigDecimal soldeTVA = request.getSoldeTVA() != null ? request.getSoldeTVA() : request.getMontantTVAInterieure();
        StatutCertificat initialStatut = brouillon ? StatutCertificat.BROUILLON : StatutCertificat.ENVOYEE;
        CertificatCredit entity = CertificatCredit.builder()
                .numero(numero)
                .dateEmission(Instant.now())
                .dateValidite(request.getDateValidite())
                .montantCordon(request.getMontantCordon())
                .montantTVAInterieure(request.getMontantTVAInterieure())
                .soldeCordon(soldeCordon)
                .soldeTVA(soldeTVA)
                .statut(initialStatut)
                .entreprise(entreprise)
                .lettreCorrection(lettreCorrection)
                .demandeCorrection(demandeCorrection)
                .build();
        applyRecapFromCreateRequest(entity, request);
        assertRecapitulatifCoherence(entity);
        entity = repository.save(entity);

        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "CertificatCredit", String.valueOf(entity.getId()), result);
        return result;
    }

    /**
     * Prise en charge par un acteur DGI / DGD / DGTCP : {@link StatutCertificat#ENVOYEE} → {@link StatutCertificat#EN_CONTROLE}.
     * Délègue à {@link #updateStatut(Long, StatutCertificat, AuthenticatedUser)} (rattachement GED, notifications).
     */
    @Transactional
    public CertificatCreditDto prendreEnCharge(Long id, AuthenticatedUser user) {
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        if (entity.getStatut() != StatutCertificat.ENVOYEE) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Prise en charge réservée aux certificats en statut ENVOYEE (statut actuel: " + entity.getStatut() + ")");
        }
        return updateStatut(id, StatutCertificat.EN_CONTROLE, user);
    }

    @Transactional
    public CertificatCreditDto soumettreBrouillon(Long id, AuthenticatedUser user) {
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        if (!canAccessCertificat(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé");
        }
        if (entity.getStatut() != StatutCertificat.BROUILLON) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Soumission réservée aux certificats en brouillon (statut: " + entity.getStatut() + ")");
        }
        if (entity.getDemandeCorrection() != null && entity.getDemandeCorrection().getId() != null) {
            assertAtMostOneActiveCertificatPourDemande(entity.getDemandeCorrection().getId(), entity.getId());
        }
        assertMiseEnPlaceTrigger(entity.getLettreCorrection(), entity.getDemandeCorrection());
        workflow.validateTransition(StatutCertificat.BROUILLON, StatutCertificat.ENVOYEE);
        entity.setStatut(StatutCertificat.ENVOYEE);
        entity = repository.save(entity);
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "CertificatCredit", String.valueOf(id), result);
        notifyCertificat(entity, StatutCertificat.ENVOYEE);
        return result;
    }

    @Transactional
    public CertificatCreditDto updateBrouillon(Long id, CreateCertificatCreditRequest request, AuthenticatedUser user) {
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        if (!canAccessCertificat(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé");
        }
        assertDeposantPeutModifierDemandeMiseEnPlace(user);
        if (entity.getStatut() != StatutCertificat.BROUILLON && entity.getStatut() != StatutCertificat.ENVOYEE) {
            throw ApiException.conflict(ApiErrorCode.DEMANDE_NON_EDITABLE,
                    "Modification réservée aux statuts BROUILLON et ENVOYEE (avant prise en charge par les services)");
        }

        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise non trouvée"));

        LettreCorrection lettreCorrection = null;
        DemandeCorrection demandeCorrection = null;
        if (request.getLettreCorrectionId() != null) {
            lettreCorrection = lettreCorrectionRepository.findById(request.getLettreCorrectionId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Lettre de correction non trouvée"));
            demandeCorrection = lettreCorrection.getFeuilleEvaluation() != null
                    ? lettreCorrection.getFeuilleEvaluation().getDemandeCorrection()
                    : null;
        } else if (request.getDemandeCorrectionId() != null) {
            demandeCorrection = demandeCorrectionRepository.findById(request.getDemandeCorrectionId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée"));
        }

        if (demandeCorrection != null && demandeCorrection.getId() != null) {
            assertAtMostOneActiveCertificatPourDemande(demandeCorrection.getId(), id);
        }

        entity.setEntreprise(entreprise);
        entity.setLettreCorrection(lettreCorrection);
        entity.setDemandeCorrection(demandeCorrection);
        entity.setDateValidite(request.getDateValidite());
        entity.setMontantCordon(request.getMontantCordon());
        entity.setMontantTVAInterieure(request.getMontantTVAInterieure());
        BigDecimal soldeCordon = request.getSoldeCordon() != null ? request.getSoldeCordon() : request.getMontantCordon();
        BigDecimal soldeTVA = request.getSoldeTVA() != null ? request.getSoldeTVA() : request.getMontantTVAInterieure();
        entity.setSoldeCordon(soldeCordon);
        entity.setSoldeTVA(soldeTVA);
        applyRecapFromCreateRequest(entity, request);
        assertRecapitulatifCoherence(entity);
        entity = repository.save(entity);
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "CertificatCredit", String.valueOf(id), result);
        return result;
    }

    /**
     * Suppression définitive d'un brouillon uniquement. Les certificats déjà en circuit se gèrent par annulation (statut ANNULE).
     */
    @Transactional
    public void deleteBrouillon(Long id, AuthenticatedUser user) {
        CertificatCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        if (!canAccessCertificat(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé");
        }
        if (entity.getStatut() != StatutCertificat.BROUILLON) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Suppression réservée aux brouillons. Pour un certificat déjà soumis, utilisez l'annulation (statut ANNULE).");
        }
        for (UtilisationCredit u : utilisationCreditRepository.findByCertificatCreditId(id)) {
            if (u.getStatut() != StatutUtilisation.BROUILLON) {
                throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Suppression impossible : une utilisation liée n'est pas en brouillon (statut: " + u.getStatut() + ")");
            }
            utilisationCreditService.deleteBrouillon(u.getId(), user);
        }
        decisionCertificatCreditRepository.findByCertificatCreditId(id).forEach(decisionCertificatCreditRepository::delete);
        documentCertificatCreditRepository.findByCertificatCreditId(id).forEach(documentCertificatCreditRepository::delete);
        tvaDeductibleStockRepository.findByCertificatCreditIdOrderByDateCreationAsc(id).forEach(tvaDeductibleStockRepository::delete);
        dossierGedService.clearCertificatFromDossierIfPresent(id);
        auditService.log(AuditAction.DELETE, "CertificatCredit", String.valueOf(id), null);
        repository.delete(entity);
    }

    @Transactional
    public CertificatCreditDto updateStatut(Long id, StatutCertificat statut, AuthenticatedUser user) {
        CertificatCredit entity = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        if (!canAccessCertificat(id, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: certificat hors périmètre");
        }
        StatutCertificat fromStatut = entity.getStatut();
        if (fromStatut == StatutCertificat.BROUILLON
                && (statut == StatutCertificat.ENVOYEE || statut == StatutCertificat.EN_CONTROLE)) {
            return soumettreBrouillon(id, user);
        }
        workflow.validateTransition(entity.getStatut(), statut);

        assertActorCanTransition(entity, statut, user);

        if (statut == StatutCertificat.OUVERT && fromStatut != StatutCertificat.OUVERT) {
            assertMontantsRenseignes(entity);
            BigDecimal montantCordon = entity.getMontantCordon() != null ? entity.getMontantCordon() : BigDecimal.ZERO;
            BigDecimal montantTVA = entity.getMontantTVAInterieure() != null ? entity.getMontantTVAInterieure() : BigDecimal.ZERO;
            if (entity.getSoldeCordon() == null) {
                entity.setSoldeCordon(montantCordon);
            }
            if (entity.getSoldeTVA() == null) {
                entity.setSoldeTVA(montantTVA);
            }
        }

        entity.setStatut(statut);
        entity = repository.save(entity);

        if (fromStatut == StatutCertificat.ENVOYEE && statut == StatutCertificat.EN_CONTROLE
                && entity.getDemandeCorrection() != null && entity.getDemandeCorrection().getId() != null) {
            dossierGedService.attachCertificatToDossier(entity.getDemandeCorrection().getId(), entity.getId());
        }
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "CertificatCredit", String.valueOf(id), result);
        notifyCertificat(entity, statut);
        return result;
    }

    @Transactional
    public CertificatCreditDto updateMontants(Long id, UpdateCertificatCreditMontantsRequest request, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut renseigner les montants");
        }
        CertificatCredit entity = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé: " + id));

        entity.setMontantCordon(request.getMontantCordon());
        entity.setMontantTVAInterieure(request.getMontantTVAInterieure());
        applyRecapFromMontantsRequest(entity, request);

        // Synchroniser les soldes tant que le crédit n'est pas ouvert (sinon incohérence avec les utilisations)
        if (entity.getStatut() != StatutCertificat.OUVERT) {
            if (entity.getSoldeCordon() == null || BigDecimal.ZERO.compareTo(entity.getSoldeCordon()) == 0) {
                entity.setSoldeCordon(request.getMontantCordon());
            }
            if (entity.getSoldeTVA() == null || BigDecimal.ZERO.compareTo(entity.getSoldeTVA()) == 0) {
                entity.setSoldeTVA(request.getMontantTVAInterieure());
            }
        }

        assertRecapitulatifCoherence(entity);
        entity = repository.save(entity);
        CertificatCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "CertificatCredit", String.valueOf(id), result);
        return result;
    }

    private void applyRecapFromCreateRequest(CertificatCredit entity, CreateCertificatCreditRequest request) {
        if (entity == null || request == null) {
            return;
        }
        if (request.getValeurDouaneFournitures() != null) {
            entity.setValeurDouaneFournitures(request.getValeurDouaneFournitures());
        }
        if (request.getDroitsEtTaxesDouaneHorsTva() != null) {
            entity.setDroitsEtTaxesDouaneHorsTva(request.getDroitsEtTaxesDouaneHorsTva());
        }
        if (request.getTvaImportationDouane() != null) {
            entity.setTvaImportationDouaneAccordee(request.getTvaImportationDouane());
            entity.setTvaImportationDouane(request.getTvaImportationDouane());
        }
        if (request.getMontantMarcheHt() != null) {
            entity.setMontantMarcheHt(request.getMontantMarcheHt());
        }
        if (request.getTvaCollecteeTravaux() != null) {
            entity.setTvaCollecteeTravaux(request.getTvaCollecteeTravaux());
        }
    }

    private void applyRecapFromMontantsRequest(CertificatCredit entity, UpdateCertificatCreditMontantsRequest request) {
        if (entity == null || request == null) {
            return;
        }
        if (request.getValeurDouaneFournitures() != null) {
            entity.setValeurDouaneFournitures(request.getValeurDouaneFournitures());
        }
        if (request.getDroitsEtTaxesDouaneHorsTva() != null) {
            entity.setDroitsEtTaxesDouaneHorsTva(request.getDroitsEtTaxesDouaneHorsTva());
        }
        if (request.getTvaImportationDouane() != null) {
            entity.setTvaImportationDouaneAccordee(request.getTvaImportationDouane());
            entity.setTvaImportationDouane(request.getTvaImportationDouane());
        }
        if (request.getMontantMarcheHt() != null) {
            entity.setMontantMarcheHt(request.getMontantMarcheHt());
        }
        if (request.getTvaCollecteeTravaux() != null) {
            entity.setTvaCollecteeTravaux(request.getTvaCollecteeTravaux());
        }
    }

    /**
     * Si le récapitulatif (lignes b, d, g) et les montants agrégés sont renseignés, vérifie
     * {@code montantCordon ≈ b + d} (crédit extérieur) et {@code montantTVAInterieure ≈ g − d} (crédit intérieur net).
     */
    /** (d) utilisé pour les formules récap : accord figé, sinon anciennes lignes sans colonne accordee. */
    private static BigDecimal resolveTvaImportationDouanePourRecap(CertificatCredit c) {
        if (c == null) {
            return null;
        }
        if (c.getTvaImportationDouaneAccordee() != null) {
            return c.getTvaImportationDouaneAccordee();
        }
        return c.getTvaImportationDouane();
    }

    private void assertRecapitulatifCoherence(CertificatCredit c) {
        if (c == null) {
            return;
        }
        BigDecimal b = c.getDroitsEtTaxesDouaneHorsTva();
        BigDecimal d = resolveTvaImportationDouanePourRecap(c);
        BigDecimal g = c.getTvaCollecteeTravaux();
        BigDecimal mc = c.getMontantCordon();
        BigDecimal mt = c.getMontantTVAInterieure();
        if (b != null && d != null && mc != null) {
            BigDecimal e = b.add(d);
            if (!approxEqual(e, mc)) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Récapitulatif incohérent : montantCordon doit correspondre au crédit extérieur (b + d) = "
                                + e.setScale(2, RoundingMode.HALF_UP) + " (montantCordon=" + mc + ")");
            }
        }
        if (g != null && d != null && mt != null) {
            BigDecimal h = g.subtract(d);
            if (!approxEqual(h, mt)) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Récapitulatif incohérent : montant TVA intérieure doit correspondre à la TVA nette (g − d) = "
                                + h.setScale(2, RoundingMode.HALF_UP) + " (montantTVAInterieure=" + mt + ")");
            }
        }
    }

    private static boolean approxEqual(BigDecimal x, BigDecimal y) {
        return x.subtract(y).abs().compareTo(RECAP_TOLERANCE_MRU) <= 0;
    }

    private void assertActorCanTransition(CertificatCredit entity, StatutCertificat to, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        StatutCertificat from = entity.getStatut();
        Role role = user.getRole();

        if (to == StatutCertificat.INCOMPLETE || to == StatutCertificat.A_RECONTROLER) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Transition manuelle vers " + to
                            + " interdite : ce statut est géré automatiquement par le système (via rejet temporaire / résolution de documents).");
        }

        if (to == StatutCertificat.EN_VALIDATION_PRESIDENT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Transition manuelle vers EN_VALIDATION_PRESIDENT interdite : "
                            + "ce statut est attribué automatiquement lorsque les 3 visas (DGI, DGD, DGTCP) sont validés.");
        }

        if (to == StatutCertificat.EN_CONTROLE) {
            if (role != Role.DGI && role != Role.DGD && role != Role.DGTCP) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Seuls DGI, DGD ou DGTCP peuvent prendre en charge (ENVOYEE) ou remettre le certificat en contrôle");
            }
        }

        if (to == StatutCertificat.VALIDE_PRESIDENT && role != Role.PRESIDENT) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul le Président peut valider le certificat");
        }

        if (to == StatutCertificat.EN_OUVERTURE_DGTCP && role != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut passer en ouverture");
        }

        if (to == StatutCertificat.OUVERT) {
            boolean byPresident = role == Role.PRESIDENT
                    && (from == StatutCertificat.VALIDE_PRESIDENT || from == StatutCertificat.EN_VALIDATION_PRESIDENT);
            boolean byDgtcp = role == Role.DGTCP
                    && (from == StatutCertificat.EN_OUVERTURE_DGTCP);
            if (!byPresident && !byDgtcp) {
                throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                        "Seul le Président peut ouvrir le crédit après son visa, ou DGTCP après la phase EN_OUVERTURE_DGTCP");
            }
        }

        if (to == StatutCertificat.ANNULE) {
            if (role == Role.ENTREPRISE) {
                mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                        .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
                Long myEnt = effectiveIdentityService.resolveEntrepriseId(user, u);
                if (myEnt == null || entity.getEntreprise() == null
                        || !entity.getEntreprise().getId().equals(myEnt)) {
                    throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Annulation réservée au titulaire du certificat");
                }
                return;
            }
            if (role != Role.AUTORITE_CONTRACTANTE && role != Role.AUTORITE_UPM
                    && role != Role.AUTORITE_UEP && role != Role.PRESIDENT) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Rôle non autorisé à annuler le certificat");
            }
        }
    }

    private void assertMiseEnPlaceTrigger(LettreCorrection lettreCorrection, DemandeCorrection demandeCorrection) {
        if (demandeCorrection == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La demande de correction est obligatoire pour la mise en place du crédit d'impôt");
        }
        if (lettreCorrection != null) {
            if (!Boolean.TRUE.equals(lettreCorrection.getSignee())) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La lettre de correction doit être signée");
            }
            if (!Boolean.TRUE.equals(lettreCorrection.getNotifiee())) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La lettre de correction doit être notifiée");
            }
        }
        if (demandeCorrection.getMarche() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le certificat doit être rattaché à un marché (via la demande de correction)");
        }

        StatutDemande statutDemande = demandeCorrection.getStatut();
        if (statutDemande != StatutDemande.ADOPTEE && statutDemande != StatutDemande.NOTIFIEE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La demande de correction doit être visée (ADOPTEE/NOTIFIEE) avant la mise en place du crédit d'impôt");
        }

        if (demandeCorrection.getMarche().getDateSignature() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le contrat (marché) doit être signé");
        }
    }

    private void assertMontantsRenseignes(CertificatCredit entity) {
        if (entity == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Certificat invalide");
        }
        if (entity.getMontantCordon() == null || entity.getMontantTVAInterieure() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Les montants (cordon et TVA intérieure) doivent être renseignés avant l'ouverture du crédit");
        }
        if (entity.getMontantCordon().compareTo(BigDecimal.ZERO) <= 0 || entity.getMontantTVAInterieure().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Les montants (cordon et TVA intérieure) doivent être strictement supérieurs à zéro");
        }
        assertRecapitulatifCoherence(entity);
    }

    private CertificatCreditDto toDto(CertificatCredit c) {
        Entreprise e = c.getEntreprise();
        Long demandeCorrectionId = c.getDemandeCorrection() != null ? c.getDemandeCorrection().getId() : null;
        Long marcheId = c.getDemandeCorrection() != null && c.getDemandeCorrection().getMarche() != null
                ? c.getDemandeCorrection().getMarche().getId()
                : null;
        BigDecimal bRec = c.getDroitsEtTaxesDouaneHorsTva();
        BigDecimal dPourRecap = resolveTvaImportationDouanePourRecap(c);
        BigDecimal dRestant = c.getTvaImportationDouane();
        BigDecimal gRec = c.getTvaCollecteeTravaux();
        BigDecimal creditExterieurRecap = null;
        if (bRec != null && dPourRecap != null) {
            creditExterieurRecap = bRec.add(dPourRecap);
        }
        BigDecimal creditInterieurNetRecap = null;
        if (gRec != null && dPourRecap != null) {
            creditInterieurNetRecap = gRec.subtract(dPourRecap);
        }
        BigDecimal totalCreditImpotRecap = null;
        if (creditExterieurRecap != null && creditInterieurNetRecap != null) {
            totalCreditImpotRecap = creditExterieurRecap.add(creditInterieurNetRecap);
        }
        return CertificatCreditDto.builder()
                .id(c.getId())
                .numero(c.getNumero())
                .dateEmission(c.getDateEmission())
                .dateValidite(c.getDateValidite())
                .montantCordon(c.getMontantCordon())
                .montantTVAInterieure(c.getMontantTVAInterieure())
                .soldeCordon(c.getSoldeCordon())
                .soldeTVA(c.getSoldeTVA())
                .valeurDouaneFournitures(c.getValeurDouaneFournitures())
                .droitsEtTaxesDouaneHorsTva(bRec)
                .tvaImportationDouaneAccordee(c.getTvaImportationDouaneAccordee())
                .tvaImportationDouane(dRestant)
                .montantMarcheHt(c.getMontantMarcheHt())
                .tvaCollecteeTravaux(gRec)
                .creditExterieurRecap(creditExterieurRecap)
                .creditInterieurNetRecap(creditInterieurNetRecap)
                .totalCreditImpotRecap(totalCreditImpotRecap)
                .statut(c.getStatut())
                .entrepriseId(e != null ? e.getId() : null)
                .entrepriseRaisonSociale(e != null ? e.getRaisonSociale() : null)
                .demandeCorrectionId(demandeCorrectionId)
                .marcheId(marcheId)
                .build();
    }

    private void notifyCertificat(CertificatCredit certificat, StatutCertificat statut) {
        if (certificat == null || certificat.getEntreprise() == null) {
            return;
        }
        List<Long> userIds = utilisateurRepository.findByEntrepriseId(certificat.getEntreprise().getId())
                .stream()
                .map(mr.gov.finances.sgci.domain.entity.Utilisateur::getId)
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("statut", statut.name());
        payload.put("numero", certificat.getNumero());
        String message = "Certificat " + certificat.getNumero() + " statut: " + statut;
        notificationService.notifyUsers(userIds, NotificationType.CERTIFICAT_STATUT_CHANGE,
                "CertificatCredit", certificat.getId(), message, payload);
    }
}
