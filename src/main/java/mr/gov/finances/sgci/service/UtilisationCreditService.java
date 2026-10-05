package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.LigneBulletinLiquidation;
import mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit;
import mr.gov.finances.sgci.domain.entity.QuittanceDgi;
import mr.gov.finances.sgci.domain.entity.QuittanceTresor;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationDouaniere;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.document.DocumentVisibilitePolicy;
import mr.gov.finances.sgci.domain.enums.AffectationTaxe;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.TypeAchat;
import mr.gov.finances.sgci.domain.enums.TvaDeductibleStockSource;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.LigneBulletinLiquidationRepository;
import mr.gov.finances.sgci.repository.QuittanceDgiRepository;
import mr.gov.finances.sgci.repository.QuittanceTresorRepository;
import mr.gov.finances.sgci.repository.DecisionUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.DocumentUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.EntrepriseRepository;
import mr.gov.finances.sgci.repository.TvaDeductibleStockRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.EffectiveIdentityService;
import mr.gov.finances.sgci.web.dto.AdminCorrectionUtilisationRequest;
import mr.gov.finances.sgci.web.dto.ApurerTVAInterieureRequest;
import mr.gov.finances.sgci.web.dto.CertificatUtilisationEmissionDto;
import mr.gov.finances.sgci.web.dto.CreateUtilisationCreditRequest;
import mr.gov.finances.sgci.web.dto.LigneBulletinDto;
import mr.gov.finances.sgci.web.dto.LiquiderUtilisationDouaneRequest;
import mr.gov.finances.sgci.web.dto.QuittanceDgiDto;
import mr.gov.finances.sgci.web.dto.QuittanceTresorDto;
import mr.gov.finances.sgci.web.dto.SaisirChequeRequest;
import mr.gov.finances.sgci.web.dto.SaisirQuittancesRequest;
import mr.gov.finances.sgci.web.dto.TvaDeductibleStockDto;
import mr.gov.finances.sgci.web.dto.UtilisationCreditDto;
import mr.gov.finances.sgci.workflow.UtilisationCreditWorkflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class UtilisationCreditService {

    private final UtilisationCreditRepository repository;
    private final CertificatCreditRepository certificatRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentUtilisationCreditRepository documentUtilisationCreditRepository;
    private final DecisionUtilisationCreditRepository decisionUtilisationCreditRepository;
    private final LigneBulletinLiquidationRepository ligneBulletinRepository;
    private final QuittanceTresorRepository quittanceTresorRepository;
    private final QuittanceDgiRepository quittanceDgiRepository;
    private final SousTraitanceService sousTraitanceService;
    private final UtilisationCreditWorkflow workflow;
    private final AuditService auditService;
    private final WorkflowNotificationHelper workflowNotificationHelper;
    private final DocumentUtilisationCreditService documentService;
    private final DocumentRequirementValidator requirementValidator;
    private final MinioService minioService;
    private final ObjectMapper objectMapper;
    private final TvaDeductibleStockRepository tvaStockRepository;
    private final EffectiveIdentityService effectiveIdentityService;
    private final UtilisationCreditEligibilityHelper eligibilityHelper;
    private final ReferenceSequenceGenerator referenceSequenceGenerator;

    @Transactional(readOnly = true)
    public List<UtilisationCreditDto> findAll() {
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Liste filtrée selon le rôle : titulaire voit toutes les demandes sur ses certificats (dont sous-traitants) ;
     * sous-traitant voit uniquement ses propres demandes ; services (DGD, DGTCP, …) voient tout.
     *
     * @param demandeurSousTraitantOnly si {@code true} et rôle titulaire, ne garde que les demandes où
     *                                  {@link UtilisationCreditDto#getDemandeurEstSousTraitant()} est vrai.
     * @param sousTraitantEntrepriseId  si renseigné et rôle titulaire, ne garde que les demandes dont
     *                                  l'entreprise demandeuse est cette id et {@code demandeurEstSousTraitant}.
     */
    @Transactional(readOnly = true)
    public List<UtilisationCreditDto> findAllVisible(
            AuthenticatedUser auth,
            Boolean demandeurSousTraitantOnly,
            Long sousTraitantEntrepriseId
    ) {
        List<UtilisationCredit> rows = resolveVisibleEntities(auth);
        List<UtilisationCreditDto> dtos = rows.stream()
                .map(u -> appliquerVisibiliteDocuments(toDto(u), u.getStatut(), auth))
                .collect(Collectors.toList());
        if (auth != null && auth.getRole() == Role.ENTREPRISE && sousTraitantEntrepriseId != null) {
            dtos = dtos.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getDemandeurEstSousTraitant())
                            && sousTraitantEntrepriseId.equals(d.getEntrepriseId()))
                    .collect(Collectors.toList());
        }
        if (Boolean.TRUE.equals(demandeurSousTraitantOnly) && auth != null && auth.getRole() == Role.ENTREPRISE) {
            return dtos.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getDemandeurEstSousTraitant()))
                    .collect(Collectors.toList());
        }
        return dtos;
    }

    private List<UtilisationCredit> resolveVisibleEntities(AuthenticatedUser auth) {
        if (auth == null || auth.getUserId() == null) {
            return repository.findAll();
        }
        Utilisateur u = utilisateurRepository.findById(auth.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Role r = auth.getRole();
        if (r == Role.SOUS_TRAITANT) {
            Long entId = effectiveIdentityService.resolveEntrepriseId(auth, u);
            if (entId == null) {
                return List.of();
            }
            return repository.findByEntrepriseId(entId);
        }
        if (r == Role.ENTREPRISE) {
            Long entId = effectiveIdentityService.resolveEntrepriseId(auth, u);
            if (entId == null) {
                return List.of();
            }
            return repository.findByCertificatCredit_Entreprise_Id(entId);
        }
        if (r == Role.AUTORITE_CONTRACTANTE) {
            Long acId = effectiveIdentityService.resolveAutoriteContractanteId(auth, u);
            if (acId == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
            }
            return repository.findAllByAutoriteContractanteId(acId);
        }
        if (r == Role.AUTORITE_UPM || r == Role.AUTORITE_UEP) {
            return repository.findAllByDelegueId(u.getId());
        }
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public UtilisationCreditDto findById(Long id, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));
        if (user != null) {
            assertCanViewUtilisation(user, entity);
        }
        return appliquerVisibiliteDocuments(toDto(entity), entity.getStatut(), user);
    }

    @Transactional(readOnly = true)
    public List<UtilisationCreditDto> findByCertificatCreditId(Long certificatCreditId, AuthenticatedUser user) {
        CertificatCredit cert = certificatRepository.findById(certificatCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé"));
        if (user != null) {
            assertCanAccessCertificatUtilisations(user, cert);
        }
        return repository.findByCertificatCreditId(certificatCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private void assertCanViewUtilisation(AuthenticatedUser auth, UtilisationCredit u) {
        Utilisateur logged = utilisateurRepository.findById(auth.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Role r = auth.getRole();
        if (r == Role.AUTORITE_CONTRACTANTE || r == Role.AUTORITE_UPM || r == Role.AUTORITE_UEP) {
            assertAcOrDelegueCanAccessCertificat(auth, logged, u.getCertificatCredit());
            return;
        }
        if (r != Role.ENTREPRISE && r != Role.SOUS_TRAITANT) {
            return;
        }
        Long eid = effectiveIdentityService.resolveEntrepriseId(auth, logged);
        if (eid == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
        }
        CertificatCredit cert = u.getCertificatCredit();
        Long titId = cert != null && cert.getEntreprise() != null ? cert.getEntreprise().getId() : null;
        if (r == Role.SOUS_TRAITANT) {
            if (u.getEntreprise() != null && u.getEntreprise().getId().equals(eid)) {
                return;
            }
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: utilisation hors périmètre");
        }
        if (titId != null && titId.equals(eid)) {
            return;
        }
        if (u.getEntreprise() != null && u.getEntreprise().getId().equals(eid)) {
            return;
        }
        throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: utilisation hors périmètre");
    }

    private void assertCanAccessCertificatUtilisations(AuthenticatedUser auth, CertificatCredit cert) {
        Utilisateur logged = utilisateurRepository.findById(auth.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Role r = auth.getRole();
        if (r == Role.AUTORITE_CONTRACTANTE || r == Role.AUTORITE_UPM || r == Role.AUTORITE_UEP) {
            assertAcOrDelegueCanAccessCertificat(auth, logged, cert);
            return;
        }
        if (r != Role.ENTREPRISE && r != Role.SOUS_TRAITANT) {
            return;
        }
        Long eid = effectiveIdentityService.resolveEntrepriseId(auth, logged);
        if (eid == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
        }
        Long titId = cert.getEntreprise() != null ? cert.getEntreprise().getId() : null;
        if (r == Role.ENTREPRISE && titId != null && titId.equals(eid)) {
            return;
        }
        if (r == Role.SOUS_TRAITANT) {
            sousTraitanceService.assertSousTraitantEntrepriseAuthorizedOnCertificat(cert.getId(), eid);
            return;
        }
        throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: certificat hors périmètre");
    }

    private void assertAcOrDelegueCanAccessCertificat(AuthenticatedUser auth, Utilisateur logged, CertificatCredit cert) {
        if (cert == null || cert.getId() == null) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: certificat invalide");
        }
        if (auth.getRole() == Role.AUTORITE_CONTRACTANTE) {
            Long acId = effectiveIdentityService.resolveAutoriteContractanteId(auth, logged);
            if (acId == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
            }
            boolean ok = certificatRepository.findById(cert.getId())
                    .map(c -> c.getDemandeCorrection() != null
                            && c.getDemandeCorrection().getAutoriteContractante() != null
                            && c.getDemandeCorrection().getAutoriteContractante().getId().equals(acId))
                    .orElse(false);
            if (!ok) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: certificat hors périmètre");
            }
            return;
        }
        if (auth.getRole() == Role.AUTORITE_UPM || auth.getRole() == Role.AUTORITE_UEP) {
            if (!certificatRepository.existsAccessByDelegue(logged.getId(), cert.getId())) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: certificat hors périmètre");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<TvaDeductibleStockDto> findTvaStockByCertificat(Long certificatCreditId) {
        return tvaStockRepository.findByCertificatCreditIdOrderByDateCreationAsc(certificatCreditId)
                .stream()
                .map(this::toStockDto)
                .collect(Collectors.toList());
    }

    private TvaDeductibleStockDto toStockDto(mr.gov.finances.sgci.domain.entity.TvaDeductibleStock s) {
        BigDecimal initial = s.getMontantInitial() != null ? s.getMontantInitial() : BigDecimal.ZERO;
        BigDecimal restant = s.getMontantRestant() != null ? s.getMontantRestant() : BigDecimal.ZERO;
        String numeroDeclaration = s.getUtilisationDouane() != null
                ? s.getUtilisationDouane().getNumeroDeclaration() : null;
        TvaDeductibleStockSource source = s.getSource();
        if (source == null) {
            source = s.getUtilisationDouane() != null
                    ? TvaDeductibleStockSource.UTILISATION_DOUANE
                    : TvaDeductibleStockSource.TRANSFERT_CREDIT;
        }
        return TvaDeductibleStockDto.builder()
                .id(s.getId())
                .source(source)
                .utilisationDouaneId(s.getUtilisationDouane() != null ? s.getUtilisationDouane().getId() : null)
                .numeroDeclaration(numeroDeclaration)
                .montantInitial(initial)
                .montantRestant(restant)
                .montantConsomme(initial.subtract(restant))
                .dateCreation(s.getDateCreation())
                .epuise(restant.compareTo(BigDecimal.ZERO) == 0)
                .build();
    }

    @Transactional
    public UtilisationCreditDto create(CreateUtilisationCreditRequest request) {
        return create(request, null);
    }

    @Transactional
    public UtilisationCreditDto create(CreateUtilisationCreditRequest request, AuthenticatedUser user) {
        boolean brouillon = Boolean.TRUE.equals(request.getBrouillon());
        CertificatCredit certificat = certificatRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé"));
        eligibilityHelper.assertCertificatEligible(certificat, request.getType());
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise non trouvée"));

        // Contrôle d'accès création
        if (user != null && user.getRole() != null) {
            Utilisateur u = utilisateurRepository.findById(user.getUserId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

            if (user.getRole() == Role.ENTREPRISE || user.getRole() == Role.SOUS_TRAITANT) {
                Long userEntrepriseId = effectiveIdentityService.resolveEntrepriseId(user, u);
                if (userEntrepriseId == null) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
                }
                if (!userEntrepriseId.equals(entreprise.getId())) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: entreprise requête invalide");
                }
                if (certificat.getEntreprise() == null || certificat.getEntreprise().getId() == null) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Certificat sans entreprise");
                }
                Long certificatEntrepriseId = certificat.getEntreprise().getId();
                if (!certificatEntrepriseId.equals(userEntrepriseId)) {
                    // Entreprise sous-traitante: doit être autorisée sur le certificat
                    sousTraitanceService.assertSousTraitantEntrepriseAuthorizedOnCertificat(certificat.getId(), userEntrepriseId);
                }
            }
        }

        UtilisationCredit entity;
        if (request.getType() == TypeUtilisation.DOUANIER) {
            UtilisationDouaniere d = new UtilisationDouaniere();
            mapBase(d, request, certificat, entreprise);
            d.setNumeroDeclaration(request.getNumeroDeclaration());
            d.setNumeroBulletin(request.getNumeroBulletin());
            d.setDateDeclaration(request.getDateDeclaration());
            d.setEnregistreeSYDONIA(request.getEnregistreeSYDONIA());

            // Compute montant from lines total so the record has a value before liquidation
            if (d.getMontant() == null && request.getLignes() != null) {
                BigDecimal total = request.getLignes().stream()
                        .map(l -> l.getValeurTaxe() != null ? l.getValeurTaxe() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                d.setMontant(total.compareTo(BigDecimal.ZERO) > 0 ? total : null);
            }

            if (!brouillon) {
                assertLignesBulletinAffectationEntreprise(request.getLignes(), true);
                eligibilityHelper.assertSoldesDouaneProposes(certificat, request.getLignes());
            }
            // Save first to get the id, then attach lines
            entity = repository.save(d);
            attachLignes((UtilisationDouaniere) entity, request.getLignes());
            // entity already saved; no second save needed
        } else {
            UtilisationTVAInterieure t = new UtilisationTVAInterieure();
            mapBase(t, request, certificat, entreprise);
            assertJustificatifNonDejaUtilise(certificat, request.getNumeroDecompte(),
                    request.getNumeroFacture(), null);
            t.setTypeAchat(resolveTypeAchat(request));
            t.setNumeroFacture(request.getNumeroFacture());
            t.setDateFacture(request.getDateFacture());
            t.setMontantTVA(request.getMontantTVAInterieure());
            t.setNumeroDecompte(request.getNumeroDecompte());

            if (t.getMontant() == null) {
                t.setMontant(t.getMontantTVA());
            }
            entity = repository.save(t);
        }
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "UtilisationCredit", String.valueOf(entity.getId()), result);
        if (!brouillon) {
            notifyUtilisationStatutChange(entity, StatutUtilisation.DEMANDEE, user);
        }
        return result;
    }

    /**
     * Suppression définitive d'un brouillon uniquement. Les utilisations soumises ne se suppriment pas ainsi.
     */
    @Transactional
    public void deleteBrouillon(Long id, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));
        assertCanViewUtilisation(user, entity);
        assertDeposantPeutModifierUtilisation(user, entity);
        if (entity.getStatut() != StatutUtilisation.BROUILLON) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Suppression réservée aux brouillons (statut actuel: " + entity.getStatut() + ")");
        }
        documentUtilisationCreditRepository.findByUtilisationCreditId(id).forEach(documentUtilisationCreditRepository::delete);
        decisionUtilisationCreditRepository.findByUtilisationCreditId(id).forEach(decisionUtilisationCreditRepository::delete);
        auditService.log(AuditAction.DELETE, "UtilisationCredit", String.valueOf(id), null);
        repository.delete(entity);
    }

    private static final Set<StatutUtilisation> STATUTS_UTILISATION_EDITABLE = EnumSet.of(
            StatutUtilisation.BROUILLON, StatutUtilisation.DEMANDEE, StatutUtilisation.INCOMPLETE);

    /**
     * Le contenu d'une demande n'est modifiable qu'avant instruction, ou pendant un rejet temporaire.
     *
     * <p>{@code INCOMPLETE} n'est posé que par un rejet temporaire, et la résolution le quitte vers
     * {@code A_RECONTROLER} : le cas « incomplète sans rejet ouvert » ne devrait pas exister. On le
     * vérifie quand même, pour que le refus nomme sa cause au lieu d'un message générique.
     */
    private void assertUtilisationContentEditable(UtilisationCredit entity) {
        if (entity == null || !STATUTS_UTILISATION_EDITABLE.contains(entity.getStatut())) {
            throw ApiException.conflict(ApiErrorCode.DEMANDE_NON_EDITABLE,
                    "Modification impossible : statut incompatible ou traitement déjà engagé");
        }
        if (entity.getStatut() == StatutUtilisation.INCOMPLETE && !aUnRejetTemporaireOuvert(entity.getId())) {
            throw ApiException.conflict(ApiErrorCode.DEMANDE_NON_EDITABLE,
                    "Modification impossible : aucun rejet temporaire n'est ouvert sur cette demande");
        }
    }

    private boolean aUnRejetTemporaireOuvert(Long utilisationCreditId) {
        return decisionUtilisationCreditRepository.findByUtilisationCreditId(utilisationCreditId).stream()
                .anyMatch(d -> d.getDecision() == DecisionCorrectionType.REJET_TEMP
                        && d.getRejetTempStatus() == RejetTempStatus.OUVERT);
    }

    private void assertDeposantPeutModifierUtilisation(AuthenticatedUser auth, UtilisationCredit entity) {
        if (auth == null || auth.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        Utilisateur u = utilisateurRepository.findById(auth.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        if (auth.getRole() != Role.ENTREPRISE && auth.getRole() != Role.SOUS_TRAITANT) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seuls le titulaire ou le sous-traitant peuvent modifier cette demande");
        }
        Long myEnt = effectiveIdentityService.resolveEntrepriseId(auth, u);
        if (myEnt == null || entity.getEntreprise() == null
                || !myEnt.equals(entity.getEntreprise().getId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Entreprise non autorisée");
        }
    }

    @Transactional
    public UtilisationCreditDto soumettreBrouillon(Long id, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));
        assertCanViewUtilisation(user, entity);
        assertDeposantPeutModifierUtilisation(user, entity);
        if (entity.getStatut() != StatutUtilisation.BROUILLON) {
            throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Soumission réservée aux brouillons (statut: " + entity.getStatut() + ")");
        }
        CertificatCredit certificat = entity.getCertificatCredit();
        if (certificat == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Certificat manquant");
        }
        eligibilityHelper.assertCertificatEligible(certificat, entity.getType());
        if (entity.getType() == TypeUtilisation.DOUANIER) {
            UtilisationDouaniere douane = (UtilisationDouaniere) entity;
            List<LigneBulletinLiquidation> lignes = ligneBulletinRepository
                    .findByUtilisationDouaniere_IdOrderByTypeLigneAscIdAsc(douane.getId());
            assertLignesBulletinAffectationEntrepriseFromEntities(lignes, true);
            eligibilityHelper.assertSoldesDouaneProposesFromEntities(certificat, lignes);
        }
        workflow.validateTransition(StatutUtilisation.BROUILLON, StatutUtilisation.DEMANDEE);
        entity.setStatut(StatutUtilisation.DEMANDEE);
        entity = repository.save(entity);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisationStatutChange(entity, StatutUtilisation.DEMANDEE, user);
        return result;
    }

    @Transactional
    public UtilisationCreditDto update(Long id, CreateUtilisationCreditRequest request, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));
        assertCanViewUtilisation(user, entity);
        assertDeposantPeutModifierUtilisation(user, entity);
        assertUtilisationContentEditable(entity);
        if (!entity.getType().equals(request.getType())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le type d'utilisation ne peut pas être modifié");
        }

        CertificatCredit certificat = certificatRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé"));
        eligibilityHelper.assertCertificatEligible(certificat, request.getType());
        Entreprise entreprise = entrepriseRepository.findById(request.getEntrepriseId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Entreprise non trouvée"));

        if (user != null && user.getRole() != null) {
            Utilisateur u = utilisateurRepository.findById(user.getUserId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
            if (user.getRole() == Role.ENTREPRISE || user.getRole() == Role.SOUS_TRAITANT) {
                Long userEntrepriseId = effectiveIdentityService.resolveEntrepriseId(user, u);
                if (userEntrepriseId == null) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
                }
                if (!userEntrepriseId.equals(entreprise.getId())) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: entreprise requête invalide");
                }
                if (certificat.getEntreprise() == null || certificat.getEntreprise().getId() == null) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Certificat sans entreprise");
                }
                Long certificatEntrepriseId = certificat.getEntreprise().getId();
                if (!certificatEntrepriseId.equals(userEntrepriseId)) {
                    sousTraitanceService.assertSousTraitantEntrepriseAuthorizedOnCertificat(certificat.getId(), userEntrepriseId);
                }
            }
        }

        entity.setCertificatCredit(certificat);
        entity.setEntreprise(entreprise);
        entity.setMontant(request.getMontant());

        if (entity instanceof UtilisationDouaniere d) {
            d.setNumeroDeclaration(request.getNumeroDeclaration());
            d.setNumeroBulletin(request.getNumeroBulletin());
            d.setDateDeclaration(request.getDateDeclaration());
            d.setEnregistreeSYDONIA(request.getEnregistreeSYDONIA());

            // Replace lines
            if (request.getLignes() != null) {
                if (entity.getStatut() == StatutUtilisation.DEMANDEE) {
                    assertLignesBulletinAffectationEntreprise(request.getLignes(), true);
                    eligibilityHelper.assertSoldesDouaneProposes(certificat, request.getLignes());
                }
                // Les lignes sont supprimées et recréées : l'annotation de la DGD disparaît avec
                // elles. Les agrégats calculés à partir des anciennes lignes survivraient, faux —
                // on les invalide, et c'est cela qui empêche le dossier d'avancer. Toutes les
                // portes en aval refusent déjà un dossier aux agrégats nuls : liquiderDouane exige
                // totalPrisEnCharge > 0, la transmission DGTCP exige CHEQUE_SAISI, et un nouveau
                // visa DGD recalcule tout. Aucun contrôle supplémentaire n'est nécessaire.
                boolean visaDejaPose = d.getTotalPrisEnCharge() != null;
                d.getLignes().clear();
                repository.save(d); // flush orphan removal before re-attaching
                attachLignes(d, request.getLignes());
                if (visaDejaPose) {
                    invaliderVisaEtCheque(d);
                }
                // Recompute montant from lines total
                if (d.getMontant() == null) {
                    BigDecimal total = request.getLignes().stream()
                            .map(l -> l.getValeurTaxe() != null ? l.getValeurTaxe() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    d.setMontant(total.compareTo(BigDecimal.ZERO) > 0 ? total : null);
                }
            }
        } else if (entity instanceof UtilisationTVAInterieure t) {
            assertJustificatifNonDejaUtilise(t.getCertificatCredit(), request.getNumeroDecompte(),
                    request.getNumeroFacture(), t.getId());
            t.setTypeAchat(resolveTypeAchat(request));
            t.setNumeroFacture(request.getNumeroFacture());
            t.setDateFacture(request.getDateFacture());
            t.setMontantTVA(request.getMontantTVAInterieure());
            t.setNumeroDecompte(request.getNumeroDecompte());
            if (t.getMontant() == null) {
                t.setMontant(t.getMontantTVA());
            }
        }

        entity = repository.save(entity);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        return result;
    }

    /**
     * Correction administrateur (ADMIN_SI) d'informations d'une demande d'utilisation, à tout
     * moment quel que soit le statut. Patch partiel volontairement restreint (montant + champs
     * déclaratifs douaniers) : ne touche ni au certificat/entreprise liés, ni aux lignes de
     * bulletin déjà liquidées. Motif obligatoire, journalisée dans l'audit sous
     * {@link AuditAction#ADMIN_CORRECTION}.
     */
    @Transactional
    public UtilisationCreditDto adminCorrectInfo(Long id, AdminCorrectionUtilisationRequest request, String motif, AuthenticatedUser user) {
        assertAdminOverride(user, motif);
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));

        if (request.getMontant() != null) {
            entity.setMontant(request.getMontant());
        }
        if (entity instanceof UtilisationDouaniere d) {
            if (request.getNumeroDeclaration() != null) {
                d.setNumeroDeclaration(request.getNumeroDeclaration());
            }
            if (request.getNumeroBulletin() != null) {
                d.setNumeroBulletin(request.getNumeroBulletin());
            }
            if (request.getDateDeclaration() != null) {
                d.setDateDeclaration(request.getDateDeclaration());
            }
        }

        entity = repository.save(entity);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.ADMIN_CORRECTION, "UtilisationCredit", String.valueOf(id), result, motif);
        return result;
    }

    private void assertAdminOverride(AuthenticatedUser user, String motif) {
        if (user == null || user.getRole() != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Correction administrateur réservée à l'administrateur système");
        }
        if (motif == null || motif.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de la correction administrateur est obligatoire");
        }
    }

    /**
     * Notifie les acteurs concernés selon le statut (entreprise titulaire, commission relais via entreprise, services).
     */
    /**
     * Dépôt de la quittance DGI sur une utilisation de TVA intérieure validée.
     *
     * <p>Étape intercalée entre la validation DGTCP et l'apurement : elle atteste le paiement
     * effectif de la TVA. Le dépôt est idempotent — un second appel remplace la quittance
     * précédente plutôt que d'en créer une seconde.
     *
     * <p>Le justificatif est stocké avant tout changement de statut : si le stockage est
     * indisponible, l'appel échoue en 503 et le dossier reste intact.
     */
    @Transactional
    public UtilisationCreditDto deposerQuittanceDgi(Long id, String numeroQuittance, Instant dateQuittance,
                                                    BigDecimal montant, MultipartFile file,
                                                    AuthenticatedUser user) throws IOException {
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation non trouvée: " + id));
        if (!(entity instanceof UtilisationTVAInterieure)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cette utilisation n'est pas de type TVA intérieure");
        }
        Role role = user != null ? user.getRole() : null;
        if (role != Role.DGI && role != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Seule la DGI peut déposer la quittance de TVA intérieure");
        }

        StatutUtilisation actuel = entity.getStatut();
        boolean remplacement = actuel == StatutUtilisation.QUITTANCE_DGI_ENREGISTREE;
        // La DGI délivre sa quittance sur présentation du certificat : il doit précéder, pas suivre.
        if (actuel != StatutUtilisation.CERTIFICAT_EMIS && !remplacement) {
            throw new ApiException(HttpStatus.CONFLICT.value(), ApiErrorCode.CERTIFICAT_UTILISATION_NON_EMIS,
                    "La quittance DGI suppose le certificat d'utilisation émis par le Président. "
                            + "Statut actuel : " + actuel);
        }

        if (numeroQuittance == null || numeroQuittance.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Le numéro de quittance est obligatoire");
        }
        if (dateQuittance != null && dateQuittance.isAfter(Instant.now())) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "La date de quittance ne peut pas être dans le futur");
        }
        if (montant == null || montant.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Le montant de la quittance doit être strictement positif");
        }

        QuittanceDgi quittance = quittanceDgiRepository.findByUtilisationCreditId(id).orElse(null);
        boolean fichierFourni = file != null && !file.isEmpty();
        if (!fichierFourni && quittance == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Le justificatif de la quittance est obligatoire au premier dépôt");
        }
        if (fichierFourni) {
            assertJustificatifAccepte(file);
        }

        if (quittance == null) {
            quittance = QuittanceDgi.builder().utilisationCredit(entity).build();
        }
        quittance.setNumeroQuittance(numeroQuittance.trim());
        quittance.setDateQuittance(dateQuittance != null ? dateQuittance : Instant.now());
        quittance.setMontant(montant);
        quittance.setDeposeePar(user != null ? user.getUsername() : null);
        quittance.setDateDepot(Instant.now());

        if (fichierFourni) {
            // Stockage d'abord : une indisponibilité doit laisser le dossier tel quel.
            String chemin = minioService.uploadFile(file);
            String nom = file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getName();
            documentUtilisationCreditRepository
                    .findByUtilisationCreditIdAndCodeDocumentAndActifTrue(id, "QUITTANCE_DGI")
                    .ifPresent(precedent -> {
                        precedent.setActif(false);
                        documentUtilisationCreditRepository.save(precedent);
                    });
            DocumentUtilisationCredit doc = documentUtilisationCreditRepository.save(
                    DocumentUtilisationCredit.builder()
                            .codeDocument("QUITTANCE_DGI")
                            .nomFichier(nom)
                            .chemin(chemin)
                            .dateUpload(Instant.now())
                            .taille(file.getSize())
                            .version(1)
                            .actif(true)
                            .utilisationCredit(entity)
                            .build());
            quittance.setDocumentChemin(chemin);
            quittance.setDocumentNomFichier(nom);
            quittance.setDocumentId(doc.getId());
        }
        quittanceDgiRepository.save(quittance);

        if (!remplacement) {
            workflow.validateTransition(actuel, StatutUtilisation.QUITTANCE_DGI_ENREGISTREE);
        }
        entity.setStatut(StatutUtilisation.QUITTANCE_DGI_ENREGISTREE);
        entity = repository.save(entity);

        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisationStatutChange(entity, StatutUtilisation.QUITTANCE_DGI_ENREGISTREE, user);
        return result;
    }

    /**
     * Le chèque ne se saisit qu'une fois le bulletin visé par la DGD.
     *
     * <p>Les dossiers antérieurs à l'introduction du statut {@code VISE} sont restés en
     * {@code EN_CONTROLE_DGD} : ils sont acceptés si un visa DGD figure bien dans leurs décisions,
     * pour ne pas bloquer un dossier légitimement instruit avant la correction.
     */
    private void assertChequeSaisissable(UtilisationCredit entity) {
        StatutUtilisation actuel = entity.getStatut();
        if (actuel == StatutUtilisation.VISE) {
            return;
        }
        if (actuel == StatutUtilisation.EN_CONTROLE_DGD) {
            boolean visaDgd = decisionUtilisationCreditRepository
                    .existsByUtilisationCreditIdAndRoleAndDecision(
                            entity.getId(), Role.DGD, DecisionCorrectionType.VISA);
            if (visaDgd) {
                return;
            }
            throw new ApiException(HttpStatus.CONFLICT.value(), ApiErrorCode.VISA_PREALABLE_MANQUANT,
                    "Le bulletin doit d'abord être visé par la DGD avant la saisie du chèque.");
        }
        throw new ApiException(HttpStatus.CONFLICT.value(), ApiErrorCode.STATUT_INCOMPATIBLE,
                "La saisie du chèque suppose un bulletin visé par la DGD. Statut actuel : " + actuel);
    }

    /** PDF, PNG ou JPG uniquement : le justificatif est destiné à être relu et archivé. */
    private void assertJustificatifAccepte(MultipartFile file) {
        String nom = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        boolean extensionOk = nom.endsWith(".pdf") || nom.endsWith(".png")
                || nom.endsWith(".jpg") || nom.endsWith(".jpeg");
        if (!extensionOk) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Justificatif refusé : formats acceptés PDF, PNG ou JPG");
        }
    }

    private static QuittanceDgiDto toQuittanceDgiDto(QuittanceDgi q) {
        return QuittanceDgiDto.builder()
                .id(q.getId())
                .numeroQuittance(q.getNumeroQuittance())
                .dateQuittance(q.getDateQuittance())
                .montant(q.getMontant())
                .documentChemin(q.getDocumentChemin())
                .documentNomFichier(q.getDocumentNomFichier())
                .documentId(q.getDocumentId())
                .deposeePar(q.getDeposeePar())
                .dateDepot(q.getDateDepot())
                .build();
    }

    private void notifyUtilisationStatutChange(UtilisationCredit utilisation, StatutUtilisation statut, AuthenticatedUser actor) {
        workflowNotificationHelper.utilisationStatut(utilisation, statut.name(), actor, null);
    }

    @Transactional
    public UtilisationCreditDto apurerTVAInterieure(Long id, ApurerTVAInterieureRequest request, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));
        if (!(entity instanceof UtilisationTVAInterieure t)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Cette utilisation n'est pas de type TVA intérieure");
        }
        // La quittance DGI atteste le paiement : sans elle, le solde de TVA ne doit pas bouger.
        if (entity.getStatut() != StatutUtilisation.QUITTANCE_DGI_ENREGISTREE) {
            throw new ApiException(HttpStatus.CONFLICT.value(), ApiErrorCode.QUITTANCE_DGI_MANQUANTE,
                    "L'apurement suppose la quittance DGI enregistrée. Statut actuel : " + entity.getStatut());
        }
        workflow.validateTransition(entity.getStatut(), StatutUtilisation.APUREE);
        assertActorCanTransition(entity, StatutUtilisation.APUREE, user);
        assertRequiredDocumentsPresent(entity);

        BigDecimal tvaCollectee = t.getMontantTVA() != null ? t.getMontantTVA() : BigDecimal.ZERO;
        if (tvaCollectee.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Montant TVA collectée invalide (doit être >= 0)");
        }

        CertificatCredit certificat = t.getCertificatCredit();
        if (certificat == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Certificat manquant");
        }

        // Calcul FIFO du stock disponible
        BigDecimal tvaDeductibleDispo = tvaStockRepository
                .findByCertificatCreditIdOrderByDateCreationAsc(certificat.getId())
                .stream()
                .map(s -> s.getMontantRestant() != null ? s.getMontantRestant() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Si non fourni: consommer tout le stock disponible (règle FIFO intégrale)
        BigDecimal tvaDeductible;
        if (request == null || request.getTvaDeductibleUtilisee() == null) {
            tvaDeductible = tvaDeductibleDispo;
        } else {
            tvaDeductible = request.getTvaDeductibleUtilisee();
            if (tvaDeductible.compareTo(BigDecimal.ZERO) < 0) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "tvaDeductibleUtilisee doit être >= 0");
            }
            if (tvaDeductible.compareTo(tvaDeductibleDispo) > 0) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "TVA déductible insuffisante (disponible=" + tvaDeductibleDispo
                        + ", demandée=" + tvaDeductible + ")");
            }
        }

        BigDecimal soldeAvant = certificat.getSoldeTVA() != null ? certificat.getSoldeTVA() : BigDecimal.ZERO;

        consumeTvaDeductible(certificat.getId(), tvaDeductible);

        BigDecimal tvaNette = tvaCollectee.subtract(tvaDeductible);
        t.setTvaDeductibleUtilisee(tvaDeductible);
        t.setTvaNette(tvaNette);
        t.setSoldeTVAAvant(soldeAvant);

        BigDecimal creditUtilise = BigDecimal.ZERO;
        BigDecimal paiementEntreprise = BigDecimal.ZERO;
        BigDecimal report = BigDecimal.ZERO;
        BigDecimal soldeApres = soldeAvant;

        int cmp = tvaNette.compareTo(BigDecimal.ZERO);
        if (cmp == 0) {
            // cas 1: neutre
        } else if (cmp > 0) {
            // cas 2
            if (soldeAvant.compareTo(tvaNette) >= 0) {
                creditUtilise = tvaNette;
                soldeApres = soldeAvant.subtract(tvaNette);
            } else {
                creditUtilise = soldeAvant;
                paiementEntreprise = tvaNette.subtract(soldeAvant);
                soldeApres = BigDecimal.ZERO;
            }
        } else {
            // cas 3
            report = tvaNette.abs();
            soldeApres = soldeAvant.add(report);
        }

        certificat.setSoldeTVA(soldeApres);
        certificatRepository.save(certificat);

        t.setCreditInterieurUtilise(creditUtilise);
        t.setPaiementEntreprise(paiementEntreprise);
        t.setReportANouveau(report);
        t.setSoldeTVAApres(soldeApres);

        t.setStatut(StatutUtilisation.APUREE);
        t.setDateLiquidation(Instant.now());
        // Le certificat d'utilisation n'est PAS numéroté ici : l'apurement est le calcul de la
        // DGTCP, l'émission est l'acte du Président (emettreCertificatUtilisation). On marque en
        // revanche le dossier comme soumis à cette émission, ce qui le distingue des dossiers
        // historiques et rend le rattrapage au démarrage auto-limité.
        t.setEmissionCertificatRequise(Boolean.TRUE);

        entity = repository.save(t);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisationStatutChange(entity, StatutUtilisation.APUREE, user);
        return result;
    }

    private void consumeTvaDeductible(Long certificatCreditId, BigDecimal amount) {
        BigDecimal remaining = amount;
        List<mr.gov.finances.sgci.domain.entity.TvaDeductibleStock> stocks = tvaStockRepository.findByCertificatCreditIdOrderByDateCreationAsc(certificatCreditId);
        for (mr.gov.finances.sgci.domain.entity.TvaDeductibleStock s : stocks) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal rest = s.getMontantRestant() != null ? s.getMontantRestant() : BigDecimal.ZERO;
            if (rest.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal take = rest.min(remaining);
            s.setMontantRestant(rest.subtract(take));
            remaining = remaining.subtract(take);
            tvaStockRepository.save(s);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Consommation TVA déductible impossible (reste=" + remaining + ")");
        }
    }

    private void assertRequiredDocumentsPresent(UtilisationCredit utilisation) {
        ProcessusDocument processus = resolveProcessus(utilisation);
        List<String> presentTypes = documentService.findActiveDocumentTypes(utilisation.getId());

        // Base: contraintes déclaratives via DocumentRequirement
        requirementValidator.assertRequiredDocumentsPresent(processus, presentTypes);

        // Complément: règles conditionnelles TVA intérieure (achat local vs décompte)
        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE && utilisation instanceof UtilisationTVAInterieure t) {
            Set<String> present = Set.copyOf(presentTypes);
            TypeAchat typeAchat = t.getTypeAchat();
            if (typeAchat == TypeAchat.ACHAT_LOCAL) {
                if (!present.contains("FACTURE") || !present.contains("DECLARATION_TVA")) {
                    throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                            "Documents obligatoires manquants (Achat local): "
                            + (present.contains("FACTURE") ? "" : "FACTURE ")
                            + (present.contains("DECLARATION_TVA") ? "" : "DECLARATION_TVA"));
                }
            } else if (typeAchat == TypeAchat.DECOMPTE) {
                if (!present.contains("DECOMPTE")) {
                    throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Document obligatoire manquant (Décompte): DECOMPTE");
                }
            }
        }
    }

    /**
     * Refuse de consommer deux fois le même justificatif sur un même crédit.
     *
     * <p>Sans ce contrôle, une facture ou un décompte déjà imputé pouvait servir à une seconde
     * demande et consommer le crédit une fois de plus. La comparaison ignore la casse et les
     * espaces de bordure, et laisse de côté les demandes rejetées, dont le justificatif redevient
     * légitimement disponible.
     *
     * @param exclureId identifiant de la demande en cours de modification, à ne pas comparer à elle-même
     */
    private void assertJustificatifNonDejaUtilise(CertificatCredit certificat, String numeroDecompte,
                                                  String numeroFacture, Long exclureId) {
        if (certificat == null || certificat.getId() == null) {
            return;
        }
        String decompte = normaliserJustificatif(numeroDecompte);
        String facture = normaliserJustificatif(numeroFacture);
        if (decompte == null && facture == null) {
            return;
        }
        for (UtilisationCredit autre : repository.findByCertificatCreditId(certificat.getId())) {
            if (!(autre instanceof UtilisationTVAInterieure t)) {
                continue;
            }
            if (exclureId != null && exclureId.equals(t.getId())) {
                continue;
            }
            if (t.getStatut() == StatutUtilisation.REJETEE) {
                continue;
            }
            String autreDecompte = normaliserJustificatif(t.getNumeroDecompte());
            String autreFacture = normaliserJustificatif(t.getNumeroFacture());
            if (decompte != null && decompte.equals(autreDecompte)) {
                throw conflitJustificatif("décompte", t.getNumeroDecompte(), t);
            }
            if (facture != null && facture.equals(autreFacture)) {
                throw conflitJustificatif("facture", t.getNumeroFacture(), t);
            }
        }
    }

    private static String normaliserJustificatif(String valeur) {
        if (valeur == null) {
            return null;
        }
        String nettoye = valeur.trim();
        return nettoye.isEmpty() ? null : nettoye.toUpperCase();
    }

    private ApiException conflitJustificatif(String libelle, String valeur, UtilisationTVAInterieure autre) {
        String reference = autre.getReference() != null ? autre.getReference() : String.valueOf(autre.getId());
        return new ApiException(HttpStatus.CONFLICT.value(), ApiErrorCode.JUSTIFICATIF_DEJA_UTILISE,
                "Le numéro de " + libelle + " « " + valeur + " » est déjà utilisé sur ce crédit "
                        + "par la demande " + reference + " (statut " + autre.getStatut() + ").");
    }

    private TypeAchat resolveTypeAchat(CreateUtilisationCreditRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getTypeAchat() != null) {
            return request.getTypeAchat();
        }
        String numeroDecompte = request.getNumeroDecompte();
        if (numeroDecompte != null && !numeroDecompte.trim().isEmpty()) {
            return TypeAchat.DECOMPTE;
        }
        return TypeAchat.ACHAT_LOCAL;
    }

    /** Persists the list of bulletin lines for a douanière utilisation. */
    /**
     * Remet le dossier à l'état d'avant visa après réécriture des lignes du bulletin.
     *
     * <p>Les agrégats ne sont pas « remis à zéro » mais à {@code null} : zéro serait un montant,
     * donc une affirmation fausse, là que {@code null} dit « pas encore calculé ». Le chèque suit,
     * puisque {@code totalAPayer} change : le montant déjà remis ne correspond plus à rien, et la
     * pièce est désactivée plutôt que supprimée, pour conserver la trace de ce qui avait été remis.
     */
    private void invaliderVisaEtCheque(UtilisationDouaniere d) {
        d.setTotalPrisEnCharge(null);
        d.setTotalAPayer(null);
        d.setMontantTVA(null);
        d.setMontantDroits(null);

        d.setBanqueNom(null);
        d.setNumeroCheque(null);
        d.setMontantCheque(null);
        d.setDateCheque(null);
        documentUtilisationCreditRepository
                .findByUtilisationCreditIdAndCodeDocumentAndActifTrue(d.getId(), "CHEQUE_CERTIFIE")
                .ifPresent(doc -> {
                    doc.setActif(false);
                    documentUtilisationCreditRepository.save(doc);
                });
    }

    private void attachLignes(UtilisationDouaniere d, List<CreateUtilisationCreditRequest.LigneBulletinRequest> ligneRequests) {
        if (ligneRequests == null || ligneRequests.isEmpty()) {
            return;
        }
        List<LigneBulletinLiquidation> lignes = ligneRequests.stream()
                .map(r -> LigneBulletinLiquidation.builder()
                        .codeTaxe(r.getCodeTaxe())
                        .denominationTaxe(r.getDenominationTaxe())
                        .typeLigne(r.getTypeLigne())
                        .valeurTaxe(r.getValeurTaxe())
                        .affectationEntreprise(r.getAffectation())
                        .utilisationDouaniere(d)
                        .build())
                .collect(Collectors.toList());
        ligneBulletinRepository.saveAll(lignes);
    }

    /**
     * À la soumission, chaque ligne avec montant &gt; 0 doit avoir une affectation entreprise (AU_CI / A_PAYER).
     */
    private void assertLignesBulletinAffectationEntreprise(
            List<CreateUtilisationCreditRequest.LigneBulletinRequest> ligneRequests, boolean strict) {
        if (ligneRequests == null || ligneRequests.isEmpty()) {
            if (strict) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                        "Le bulletin de liquidation doit contenir au moins une ligne");
            }
            return;
        }
        for (CreateUtilisationCreditRequest.LigneBulletinRequest r : ligneRequests) {
            BigDecimal val = r.getValeurTaxe() != null ? r.getValeurTaxe() : BigDecimal.ZERO;
            if (val.compareTo(BigDecimal.ZERO) > 0 && r.getAffectation() == null) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                        "Chaque ligne avec un montant doit indiquer AU_CI (cordon) ou A_PAYER pour la taxe "
                                + r.getCodeTaxe());
            }
        }
    }

    private void assertLignesBulletinAffectationEntrepriseFromEntities(
            List<LigneBulletinLiquidation> lignes, boolean strict) {
        if (lignes == null || lignes.isEmpty()) {
            if (strict) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                        "Le bulletin de liquidation doit contenir au moins une ligne");
            }
            return;
        }
        for (LigneBulletinLiquidation l : lignes) {
            BigDecimal val = l.getValeurTaxe() != null ? l.getValeurTaxe() : BigDecimal.ZERO;
            if (val.compareTo(BigDecimal.ZERO) > 0 && l.getAffectationEntreprise() == null) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                        "Chaque ligne avec un montant doit indiquer AU_CI (cordon) ou A_PAYER pour la taxe "
                                + l.getCodeTaxe());
            }
        }
    }

    private void mapBase(UtilisationCredit u, CreateUtilisationCreditRequest r, CertificatCredit c, Entreprise e) {
        u.setDateDemande(Instant.now());
        u.setReference(referenceSequenceGenerator.next(ReferenceSequenceGenerator.PREFIX_UTILISATION));
        u.setMontant(r.getMontant());
        u.setStatut(Boolean.TRUE.equals(r.getBrouillon()) ? StatutUtilisation.BROUILLON : StatutUtilisation.DEMANDEE);
        u.setCertificatCredit(c);
        u.setEntreprise(e);
    }

    /**
     * Étape DGD : annotation des lignes du bulletin (AU_CI / A_PAYER) + visa.
     * <p>
     * Le DGD valide ou modifie, pour chaque ligne, la proposition entreprise ({@code affectationEntreprise}) :
     * décision finale {@code affectation} (AU_CI / A_PAYER), correction éventuelle des montants,
     * upload du bulletin annoté. Si le DGD omet une décision sur une ligne, la proposition entreprise est retenue (validation).
     * Appelable aussi depuis EN_CONTROLE_DGD ou VISE pour ré-annotation. Aucune opération financière ici.
     * Statut résultant : {@link StatutUtilisation#VISE} — le contrôle est clos, la main passe à
     * l'entreprise pour la saisie du chèque.
     */
    @Transactional
    public UtilisationCreditDto visaDgd(Long id, String decisionsJson, MultipartFile file, AuthenticatedUser user) throws IOException {
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));
        if (!(entity instanceof UtilisationDouaniere d)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Cette utilisation n'est pas de type Douane");
        }

        workflow.validateTransition(entity.getStatut(), StatutUtilisation.VISE);
        assertActorCanTransition(entity, StatutUtilisation.VISE, user);

        // Le bulletin annoté matérialise la décision de la DGD : il est exigé au visa, et le
        // contrôle est posé ici pour qu'un fichier manquant n'engage aucune mutation.
        //
        // L'exemption n'est pas une facilité : le graphe autorise l'auto-transition VISE → VISE
        // pour permettre à la DGD de ré-annoter le bulletin tant que l'entreprise n'a pas payé.
        // Exiger le fichier inconditionnellement casserait cette ré-annotation.
        boolean bulletinDejaDepose = documentUtilisationCreditRepository
                .findByUtilisationCreditIdAndCodeDocumentAndActifTrue(id, "BULLETIN_ANNOTE")
                .isPresent();
        if ((file == null || file.isEmpty()) && !bulletinDejaDepose) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Le bulletin annoté est obligatoire au visa DGD");
        }
        if (file != null && !file.isEmpty()) {
            assertJustificatifAccepte(file);
        }

        List<LiquiderUtilisationDouaneRequest.DecisionLigneRequest> decisions;
        try {
            decisions = objectMapper.readValue(decisionsJson,
                    new TypeReference<List<LiquiderUtilisationDouaneRequest.DecisionLigneRequest>>() {});
        } catch (Exception e) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Format JSON invalide pour le champ 'decisions': " + e.getMessage());
        }

        if (decisions == null || decisions.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "La liste des décisions par ligne est obligatoire");
        }

        // Construire maps : décision et valeur corrigée par ligneId
        Map<Long, AffectationTaxe> decisionMap = new java.util.HashMap<>();
        Map<Long, BigDecimal> valeurMap = new java.util.HashMap<>();
        for (LiquiderUtilisationDouaneRequest.DecisionLigneRequest dec : decisions) {
            if (dec.getLigneId() == null || dec.getAffectation() == null) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Chaque décision doit avoir un ligneId et une affectation");
            }
            decisionMap.put(dec.getLigneId(), dec.getAffectation());
            if (dec.getValeurTaxe() != null) {
                valeurMap.put(dec.getLigneId(), dec.getValeurTaxe());
            }
        }

        List<LigneBulletinLiquidation> lignes = ligneBulletinRepository
                .findByUtilisationDouaniere_IdOrderByTypeLigneAscIdAsc(d.getId());
        if (lignes.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune ligne de bulletin trouvée pour cette demande");
        }

        BigDecimal totalPrisEnCharge = BigDecimal.ZERO;
        BigDecimal totalAPayer = BigDecimal.ZERO;
        BigDecimal tvaAuCI = BigDecimal.ZERO;

        for (LigneBulletinLiquidation ligne : lignes) {
            // Appliquer la valeur corrigée par le DGD si présente
            if (valeurMap.containsKey(ligne.getId())) {
                ligne.setValeurTaxe(valeurMap.get(ligne.getId()));
            }
            BigDecimal val = ligne.getValeurTaxe() != null ? ligne.getValeurTaxe() : BigDecimal.ZERO;
            AffectationTaxe aff = decisionMap.get(ligne.getId());
            if (aff == null) {
                if (val.compareTo(BigDecimal.ZERO) == 0) {
                    aff = AffectationTaxe.A_PAYER;
                } else if (ligne.getAffectationEntreprise() != null) {
                    // Validation implicite : le DGD retient la proposition entreprise
                    aff = ligne.getAffectationEntreprise();
                } else {
                    throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                            "Décision manquante pour la ligne id=" + ligne.getId() + " (" + ligne.getCodeTaxe()
                                    + ") : aucune proposition entreprise");
                }
            }
            ligne.setAffectation(aff);
            if (aff == AffectationTaxe.AU_CI) {
                totalPrisEnCharge = totalPrisEnCharge.add(val);
                if ("TVA".equalsIgnoreCase(ligne.getCodeTaxe())) {
                    tvaAuCI = tvaAuCI.add(val);
                }
            } else {
                totalAPayer = totalAPayer.add(val);
            }
        }
        ligneBulletinRepository.saveAll(lignes);

        if (totalPrisEnCharge.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le montant total pris en charge par le CI doit être > 0");
        }

        // Stockage MinIO (bulletin optionnel) avant transition EN_CONTROLE_DGD
        if (file != null && !file.isEmpty()) {
            // Désactiver l'ancien bulletin annoté s'il existe
            documentUtilisationCreditRepository
                    .findByUtilisationCreditIdAndCodeDocumentAndActifTrue(id, "BULLETIN_ANNOTE")
                    .ifPresent(prev -> {
                        prev.setActif(false);
                        documentUtilisationCreditRepository.save(prev);
                    });
            String fileUrl = minioService.uploadFile(file);
            mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit doc =
                    mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit.builder()
                            .codeDocument("BULLETIN_ANNOTE")
                            .nomFichier(file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getName())
                            .chemin(fileUrl)
                            .dateUpload(Instant.now())
                            .taille(file.getSize())
                            .version(1)
                            .actif(true)
                            .utilisationCredit(entity)
                            .build();
            documentUtilisationCreditRepository.save(doc);
        }

        d.setTotalPrisEnCharge(totalPrisEnCharge);
        d.setTotalAPayer(totalAPayer);
        d.setMontantTVA(tvaAuCI);
        d.setMontantDroits(totalPrisEnCharge.subtract(tvaAuCI));
        d.setMontant(totalPrisEnCharge);
        d.setStatut(StatutUtilisation.VISE);

        entity = repository.save(d);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisationStatutChange(entity, StatutUtilisation.VISE, user);
        return result;
    }

    /**
     * Étape Entreprise : saisie du chèque certifié fourni à la douane après visa DGD.
     * Le fichier justificatif (scan du chèque) est obligatoire et enregistré dans le GED.
     * Statut résultant : {@link StatutUtilisation#CHEQUE_SAISI}.
     */
    @Transactional
    public UtilisationCreditDto saisirCheque(Long id, SaisirChequeRequest request, MultipartFile file, AuthenticatedUser user) throws IOException {
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation non trouvée: " + id));
        if (!(entity instanceof UtilisationDouaniere d)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Cette utilisation n'est pas de type Douane");
        }

        Role role = user != null ? user.getRole() : null;
        if (role != Role.ENTREPRISE && role != Role.SOUS_TRAITANT
                && role != Role.COMMISSION_RELAIS && role != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Le chèque certifié est saisi par l'entreprise titulaire, le sous-traitant demandeur "
                            + "ou la commission relais");
        }
        assertChequeSaisissable(entity);
        workflow.validateTransition(entity.getStatut(), StatutUtilisation.CHEQUE_SAISI);

        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Le justificatif du chèque certifié est obligatoire");
        }

        d.setBanqueNom(request.getBanqueNom());
        d.setNumeroCheque(request.getNumeroCheque());
        d.setMontantCheque(request.getMontantCheque());
        d.setDateCheque(request.getDateCheque() != null ? request.getDateCheque() : Instant.now());

        // Désactiver l'éventuel document précédent du même type
        documentUtilisationCreditRepository
                .findByUtilisationCreditIdAndCodeDocumentAndActifTrue(id, "CHEQUE_CERTIFIE")
                .ifPresent(prev -> {
                    prev.setActif(false);
                    documentUtilisationCreditRepository.save(prev);
                });

        // Stockage MinIO avant transition de statut (rollback si indisponible)
        String fileUrl = minioService.uploadFile(file);
        mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit doc =
                mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit.builder()
                        .codeDocument("CHEQUE_CERTIFIE")
                        .nomFichier(file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getName())
                        .chemin(fileUrl)
                        .dateUpload(Instant.now())
                        .taille(file.getSize())
                        .version(1)
                        .actif(true)
                        .utilisationCredit(entity)
                        .build();
        documentUtilisationCreditRepository.save(doc);

        d.setStatut(StatutUtilisation.CHEQUE_SAISI);
        entity = repository.save(d);

        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisationStatutChange(entity, StatutUtilisation.CHEQUE_SAISI, user);
        return result;
    }

    /**
     * Étape DGTCP : contrôle du dossier et transmission au Président.
     *
     * <p>La DGTCP vérifie que le dossier est complet — bulletin visé par la DGD, chèque certifié
     * couvrant la part à payer — avant de le présenter au Président, qui émettra le certificat.
     * Aucune opération financière ici : les soldes ne bougent qu'à la liquidation.
     *
     * <p>Statut résultant : {@link StatutUtilisation#TRANSMISE_AU_PRESIDENT}.
     */
    @Transactional
    public UtilisationCreditDto transmettreAuPresident(Long id, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Utilisation non trouvée: " + id));
        if (!(entity instanceof UtilisationDouaniere d)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cette utilisation n'est pas de type Douane");
        }
        // Idempotence : un double clic du front ne doit pas produire un 409.
        if (entity.getStatut() == StatutUtilisation.TRANSMISE_AU_PRESIDENT) {
            return toDto(entity);
        }
        if (entity.getStatut() != StatutUtilisation.CHEQUE_SAISI) {
            throw new ApiException(HttpStatus.CONFLICT.value(), ApiErrorCode.STATUT_INCOMPATIBLE,
                    "La transmission suppose le chèque certifié saisi par l'entreprise. Statut actuel : "
                            + entity.getStatut());
        }
        workflow.validateTransition(entity.getStatut(), StatutUtilisation.TRANSMISE_AU_PRESIDENT);
        assertActorCanTransition(entity, StatutUtilisation.TRANSMISE_AU_PRESIDENT, user);
        if (user == null || user.getRole() != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Seul DGTCP peut transmettre le dossier au Président");
        }

        // La substance du contrôle : c'est ce que la DGTCP atteste en transmettant.
        if (d.getNumeroCheque() == null || d.getNumeroCheque().isBlank()
                || d.getMontantCheque() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le chèque certifié doit être saisi avant la transmission au Président");
        }
        if (documentUtilisationCreditRepository
                .findByUtilisationCreditIdAndCodeDocumentAndActifTrue(id, "BULLETIN_ANNOTE").isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le bulletin annoté par la DGD doit être au dossier avant la transmission");
        }

        d.setStatut(StatutUtilisation.TRANSMISE_AU_PRESIDENT);
        entity = repository.save(d);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisationStatutChange(entity, StatutUtilisation.TRANSMISE_AU_PRESIDENT, user);
        return result;
    }

    /**
     * Étape DGTCP : validation du chèque reçu et envoi au Trésor.
     * Statut résultant : {@link StatutUtilisation#ENVOYEE_AU_TRESOR}.
     */
    @Transactional
    public UtilisationCreditDto envoyerAuTresor(Long id, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation non trouvée: " + id));
        if (!(entity instanceof UtilisationDouaniere d)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Cette utilisation n'est pas de type Douane");
        }

        // Le Trésor encaisse sur présentation du certificat : sans lui, l'envoi n'a pas d'objet.
        if (entity.getStatut() != StatutUtilisation.CERTIFICAT_EMIS) {
            throw new ApiException(HttpStatus.CONFLICT.value(), ApiErrorCode.CERTIFICAT_UTILISATION_NON_EMIS,
                    "Le certificat d'utilisation doit être émis par le Président avant l'envoi au Trésor. "
                            + "Statut actuel : " + entity.getStatut());
        }
        workflow.validateTransition(entity.getStatut(), StatutUtilisation.ENVOYEE_AU_TRESOR);
        if (user == null || user.getRole() != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut envoyer au Trésor");
        }
        if (d.getNumeroCheque() == null || d.getNumeroCheque().isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le chèque certifié doit être saisi avant envoi au Trésor");
        }

        d.setStatut(StatutUtilisation.ENVOYEE_AU_TRESOR);
        entity = repository.save(d);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisationStatutChange(entity, StatutUtilisation.ENVOYEE_AU_TRESOR, user);
        return result;
    }

    /**
     * Étape DGTCP : saisie des quittances Trésor + justificatifs (un fichier par quittance).
     * <p>
     * {@code quittancesJson} est un tableau JSON stringifié de {@link SaisirQuittancesRequest.QuittanceItem}.
     * {@code files} est une liste optionnelle de fichiers indexés sur les quittances :
     * {@code files.get(i)} correspond à {@code quittances.get(i)}.
     * Si un fichier est absent ou vide pour un index donné, la quittance est enregistrée sans justificatif.
     * </p>
     * Statut résultant : {@link StatutUtilisation#QUITTANCES_ENREGISTREES}.
     */
    @Transactional
    public UtilisationCreditDto saisirQuittances(Long id, String quittancesJson, List<MultipartFile> files, AuthenticatedUser user) throws IOException {
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation non trouvée: " + id));
        if (!(entity instanceof UtilisationDouaniere d)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Cette utilisation n'est pas de type Douane");
        }

        workflow.validateTransition(entity.getStatut(), StatutUtilisation.QUITTANCES_ENREGISTREES);
        if (user == null || user.getRole() != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut saisir les quittances Trésor");
        }

        List<SaisirQuittancesRequest.QuittanceItem> items;
        try {
            items = objectMapper.readValue(quittancesJson,
                    new TypeReference<List<SaisirQuittancesRequest.QuittanceItem>>() {});
        } catch (Exception e) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Format JSON invalide pour le champ 'quittances': " + e.getMessage());
        }

        if (items == null || items.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Au moins une quittance est obligatoire");
        }

        // Supprimer les anciennes quittances et recréer
        quittanceTresorRepository.deleteByUtilisationDouaniere_Id(d.getId());

        // Et désactiver les pièces GED correspondantes. Sans cela elles restent toutes actives et
        // s'accumulent : depuis que QUITTANCE_TRESOR est paramétré, un dépôt par la route normale
        // interrogerait un code à plusieurs lignes actives et tomberait en 500.
        List<mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit> quittancesPrecedentes =
                documentUtilisationCreditRepository
                        .findByUtilisationCreditIdAndCodeDocumentAndActifTrueOrderByVersionDescIdDesc(
                                d.getId(), "QUITTANCE_TRESOR");
        if (!quittancesPrecedentes.isEmpty()) {
            quittancesPrecedentes.forEach(doc -> doc.setActif(false));
            documentUtilisationCreditRepository.saveAll(quittancesPrecedentes);
        }

        for (int i = 0; i < items.size(); i++) {
            SaisirQuittancesRequest.QuittanceItem item = items.get(i);

            // Upload du justificatif si fourni pour cet index
            String documentChemin = null;
            String documentNomFichier = null;
            MultipartFile file = (files != null && i < files.size()) ? files.get(i) : null;
            if (file != null && !file.isEmpty()) {
                documentChemin = minioService.uploadFile(file);
                documentNomFichier = file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getName();

                // Enregistrer aussi dans le GED (document_utilisation_credit)
                mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit doc =
                        mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit.builder()
                                .codeDocument("QUITTANCE_TRESOR")
                                .nomFichier(documentNomFichier)
                                .chemin(documentChemin)
                                .dateUpload(Instant.now())
                                .taille(file.getSize())
                                .version(1)
                                .actif(true)
                                .utilisationCredit(entity)
                                .build();
                documentUtilisationCreditRepository.save(doc);
            }

            QuittanceTresor q = QuittanceTresor.builder()
                    .utilisationDouaniere(d)
                    .numeroQuittance(item.getNumeroQuittance())
                    .dateQuittance(item.getDateQuittance())
                    .montant(item.getMontant())
                    .referencePaiement(item.getReferencePaiement())
                    .documentChemin(documentChemin)
                    .documentNomFichier(documentNomFichier)
                    .build();
            quittanceTresorRepository.save(q);
        }

        d.setStatut(StatutUtilisation.QUITTANCES_ENREGISTREES);
        entity = repository.save(d);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisationStatutChange(entity, StatutUtilisation.QUITTANCES_ENREGISTREES, user);
        return result;
    }

    /**
     * Étape Entreprise : accusé de réception du certificat d'utilisation.
     * Statut résultant : {@link StatutUtilisation#CLOTUREE}.
     */
    @Transactional
    public UtilisationCreditDto cloturerReceptionEntreprise(Long id, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation non trouvée: " + id));

        workflow.validateTransition(entity.getStatut(), StatutUtilisation.CLOTUREE);
        Role role = user != null ? user.getRole() : null;
        if (role != Role.ENTREPRISE && role != Role.COMMISSION_RELAIS) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seule l'entreprise peut accuser réception");
        }
        assertCertificatEmisAvantCloture(entity);

        entity.setStatut(StatutUtilisation.CLOTUREE);
        entity = repository.save(entity);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisationStatutChange(entity, StatutUtilisation.CLOTUREE, user);
        return result;
    }

    /**
     * Étape DGTCP : exécution de la liquidation financière après le visa DGD.
     * <p>
     * <ol>
     *   <li>Débite le solde cordon de {@code totalPrisEnCharge - TVA_AU_CI}
     *       (la TVA étant traitée séparément).</li>
     *   <li>Décrémente le quota {@code tvaImportationDouane} du certificat de {@code TVA_AU_CI}.</li>
     *   <li>Alimente le stock TVA déductible de {@code TVA_AU_CI}.</li>
     * </ol>
     * Statut résultant : {@link StatutUtilisation#LIQUIDEE}.
     */
    @Transactional
    public UtilisationCreditDto liquiderDouane(Long id, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));
        if (!(entity instanceof UtilisationDouaniere d)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Cette utilisation n'est pas de type Douane");
        }
        // Accepter QUITTANCES_ENREGISTREES (nouveau workflow) ou VISE (rétrocompatibilité)
        StatutUtilisation statut = entity.getStatut();
        if (statut != StatutUtilisation.QUITTANCES_ENREGISTREES && statut != StatutUtilisation.VISE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "La liquidation requiert le statut QUITTANCES_ENREGISTREES (ou VISE pour l'ancien workflow). Statut actuel : " + statut);
        }

        workflow.validateTransition(entity.getStatut(), StatutUtilisation.LIQUIDEE);
        assertActorCanTransition(entity, StatutUtilisation.LIQUIDEE, user);

        // Les décisions DGD doivent avoir été enregistrées lors du visa
        if (d.getTotalPrisEnCharge() == null || d.getTotalPrisEnCharge().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Le visa DGD (avec les décisions AU_CI / A_PAYER) doit être enregistré avant la liquidation");
        }

        BigDecimal totalPrisEnCharge = d.getTotalPrisEnCharge();
        BigDecimal tvaAuCI = d.getMontantTVA() != null ? d.getMontantTVA() : BigDecimal.ZERO;

        // Montant imputé sur le solde cordon = partie hors TVA (la TVA est traitée via tvaImportationDouane)
        BigDecimal montantCordon = totalPrisEnCharge.subtract(tvaAuCI);

        CertificatCredit certificat = certificatRepository.findById(d.getCertificatCredit().getId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat de crédit non trouvé"));

        // ── Décrémenter le quota TVA importation douane ─────────────────────
        if (tvaAuCI.compareTo(BigDecimal.ZERO) > 0) {
            if (certificat.getTvaImportationDouaneAccordee() == null && certificat.getTvaImportationDouane() != null) {
                certificat.setTvaImportationDouaneAccordee(certificat.getTvaImportationDouane());
            }
            BigDecimal quotaTVA = certificat.getTvaImportationDouane() != null ? certificat.getTvaImportationDouane() : BigDecimal.ZERO;
            if (tvaAuCI.compareTo(quotaTVA) > 0) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "TVA AU_CI (" + tvaAuCI + ") dépasse le quota TVA importation douane disponible (" + quotaTVA + ")");
            }
            certificat.setTvaImportationDouane(quotaTVA.subtract(tvaAuCI));
        }

        // ── Débiter le solde cordon (hors TVA) ──────────────────────────────
        BigDecimal soldeCordonAvant = certificat.getSoldeCordon() != null ? certificat.getSoldeCordon() : BigDecimal.ZERO;
        d.setSoldeCordonAvant(soldeCordonAvant);

        if (montantCordon.compareTo(BigDecimal.ZERO) > 0) {
            if (soldeCordonAvant.compareTo(montantCordon) < 0) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Solde cordon insuffisant (disponible=" + soldeCordonAvant + ", requis=" + montantCordon + ")");
            }
            certificat.setSoldeCordon(soldeCordonAvant.subtract(montantCordon));
        }
        certificatRepository.save(certificat);

        BigDecimal soldeCordonApres = certificat.getSoldeCordon() != null ? certificat.getSoldeCordon() : BigDecimal.ZERO;
        d.setSoldeCordonApres(soldeCordonApres);

        // montant = montant total pris en charge (droits + TVA) — affiché comme "Montant" sur l'utilisation
        d.setMontant(totalPrisEnCharge);
        d.setCertificatCredit(certificat);
        d.setStatut(StatutUtilisation.LIQUIDEE);
        d.setDateLiquidation(Instant.now());
        d.setEmissionCertificatRequise(Boolean.TRUE);

        // ── Alimenter le stock TVA déductible ───────────────────────────────
        if (tvaAuCI.compareTo(BigDecimal.ZERO) > 0) {
            tvaStockRepository.save(mr.gov.finances.sgci.domain.entity.TvaDeductibleStock.builder()
                    .certificatCredit(d.getCertificatCredit())
                    .utilisationDouane(d)
                    .source(TvaDeductibleStockSource.UTILISATION_DOUANE)
                    .montantInitial(tvaAuCI)
                    .montantRestant(tvaAuCI)
                    .dateCreation(Instant.now())
                    .build());
        }

        entity = repository.save(d);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisationStatutChange(entity, StatutUtilisation.LIQUIDEE, user);
        return result;
    }

    /**
     * Étape Président : émission du certificat d'utilisation.
     *
     * <p>Acte réservé au Président, placé <b>avant</b> l'étape de paiement : le certificat est la
     * pièce que l'entreprise présente au Trésor (douane) ou à la DGI (TVA intérieure) pour obtenir
     * sa quittance. Le numéro ({@code CU-nnn/AAAA}) est attribué ici, et une seule fois.
     *
     * <p><b>Ne pas exiger ici la présence du document {@code CERTIFICAT_UTILISATION}.</b> Le
     * document doit porter le numéro, donc le numéro le précède nécessairement. Calquer le contrôle
     * de la lettre d'adoption ({@code DemandeCorrectionService}, qui exige la pièce au moment de
     * l'adoption) créerait ici une impasse circulaire. La séquence est : émission → composition du
     * document avec le numéro → dépôt de la pièce signée.
     *
     * <p>Statut résultant : {@link StatutUtilisation#CERTIFICAT_EMIS}, d'où le dossier repart vers
     * {@code ENVOYEE_AU_TRESOR} en douane ou {@code QUITTANCE_DGI_ENREGISTREE} en TVA intérieure.
     */
    @Transactional
    public UtilisationCreditDto emettreCertificatUtilisation(Long id, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Utilisation de crédit non trouvée: " + id));
        return appliquerEmissionCertificat(entity, user, Role.PRESIDENT);
    }

    /**
     * Substitution administrative de l'émission : l'ADMIN_SI agit à la place du Président.
     *
     * <p>Même pattern que {@code adminVisaPourRole} / {@code adminValiderPourPresident} — motif
     * obligatoire et trace {@link AuditAction#ADMIN_CORRECTION} — à une différence près : la pièce
     * signée ne peut pas être exigée au moment de l'émission, puisqu'elle doit porter le numéro que
     * l'émission attribue. Elle est donc acceptée ici de façon optionnelle, et déposée juste après
     * la numérotation. Le fichier passe par cette route parce que l'ADMIN_SI ne détient pas
     * {@code utilisation.*.document.upload} et ne peut donc pas utiliser {@code POST /{id}/documents}.
     */
    @Transactional
    public UtilisationCreditDto adminEmettreCertificatUtilisation(Long id, String motif,
                                                                  MultipartFile file,
                                                                  AuthenticatedUser user) throws IOException {
        if (user == null || user.getRole() != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Seul l'administrateur SI peut émettre le certificat à la place du Président");
        }
        if (motif == null || motif.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Le motif est obligatoire pour une émission par substitution administrative");
        }
        UtilisationCredit entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Utilisation de crédit non trouvée: " + id));

        UtilisationCreditDto result = appliquerEmissionCertificat(entity, user, Role.ADMIN_SI);

        // Le numéro existe désormais : la pièce signée peut être déposée dans la même requête.
        if (file != null && !file.isEmpty()) {
            documentService.adminReplace(id, TypeDocument.CERTIFICAT_UTILISATION.name(), motif, file, user);
        }

        Map<String, Object> trace = new HashMap<>();
        trace.put("motif", motif);
        trace.put("roleSubstitue", Role.PRESIDENT.name());
        trace.put("numeroCertificatUtilisation", result.getNumeroCertificatUtilisation());
        trace.put("documentDepose", file != null && !file.isEmpty());
        auditService.log(AuditAction.ADMIN_CORRECTION, "UtilisationCredit", String.valueOf(id), trace);
        return result;
    }

    /** Corps commun aux deux routes d'émission ; {@code roleAttendu} n'en change que la porte. */
    private UtilisationCreditDto appliquerEmissionCertificat(UtilisationCredit entity,
                                                             AuthenticatedUser user,
                                                             Role roleAttendu) {
        // Idempotence : un double clic du front ne doit pas produire un 409.
        if (entity.getStatut() == StatutUtilisation.CERTIFICAT_EMIS) {
            return toDto(entity);
        }

        String code = codeBlocageEmissionCertificat(entity, user, roleAttendu);
        if (code != null) {
            throw exceptionBlocageEmissionCertificat(code, entity, user, roleAttendu);
        }

        workflow.validateTransition(entity.getStatut(), StatutUtilisation.CERTIFICAT_EMIS);
        assertActorCanTransition(entity, StatutUtilisation.CERTIFICAT_EMIS, user);

        // nextSansMois est en REQUIRES_NEW : la séquence est consommée même si cette transaction
        // échoue ensuite. Toutes les validations doivent donc précéder cette affectation.
        // Et jamais de renumérotation : un dossier TVA apuré sous l'ancienne règle garde son numéro.
        if (entity.getNumeroCertificatUtilisation() == null) {
            entity.setNumeroCertificatUtilisation(referenceSequenceGenerator
                    .nextSansMois(ReferenceSequenceGenerator.PREFIX_CERTIFICAT_UTILISATION));
            entity.setDateCertificatUtilisation(Instant.now());
        }
        entity.setStatut(StatutUtilisation.CERTIFICAT_EMIS);

        entity = repository.save(entity);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(entity.getId()), result);
        notifyUtilisationStatutChange(entity, StatutUtilisation.CERTIFICAT_EMIS, user);
        return result;
    }

    /**
     * Cause du blocage de l'émission, {@code null} si rien ne s'y oppose.
     *
     * <p>Source de vérité unique : {@link #exceptionBlocageEmissionCertificat} lève l'exception
     * correspondante et {@link #etatEmissionCertificat} expose le même code. Aucun client n'a donc
     * à réécrire la règle.
     */
    private String codeBlocageEmissionCertificat(UtilisationCredit u, AuthenticatedUser user, Role roleAttendu) {
        Role role = user != null ? user.getRole() : null;
        if (role != roleAttendu) {
            return "ROLE_NON_HABILITE";
        }
        if (u.getStatut() != statutPrealableEmission(u)) {
            return "STATUT_INCOMPATIBLE";
        }
        return null;
    }

    /**
      * L'étape dont l'émission dépend : la transmission par la DGTCP en douane, la validation en
      * TVA intérieure.
      *
      * <p>Le certificat d'utilisation est la pièce que l'entreprise présente au Trésor ou à la DGI
      * pour obtenir sa quittance : il doit donc exister <b>avant</b> cette présentation, et non
      * après le calcul financier qui la suit.
      */
    private static StatutUtilisation statutPrealableEmission(UtilisationCredit u) {
        return u.getType() == TypeUtilisation.DOUANIER
                ? StatutUtilisation.TRANSMISE_AU_PRESIDENT
                : StatutUtilisation.VALIDEE;
    }

    /** Exception correspondant à un code de blocage : message et code restent solidaires. */
    private ApiException exceptionBlocageEmissionCertificat(String code, UtilisationCredit u,
                                                            AuthenticatedUser user, Role roleAttendu) {
        switch (code) {
            case "ROLE_NON_HABILITE":
                return ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                        "Émission du certificat d'utilisation réservée au rôle " + roleAttendu
                                + ". Rôle courant : " + (user != null ? user.getRole() : null));
            case "STATUT_INCOMPATIBLE":
                return ApiException.badRequest(ApiErrorCode.STATUT_INCOMPATIBLE,
                        "L'émission suppose le dossier instruit et transmis (statut "
                                + statutPrealableEmission(u) + "). Statut actuel : " + u.getStatut());
            default:
                return ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Émission du certificat impossible");
        }
    }

    /** État d'émission, pour piloter le bouton et son message côté front. */
    @Transactional(readOnly = true)
    public CertificatUtilisationEmissionDto etatEmissionCertificat(Long id, AuthenticatedUser user) {
        UtilisationCredit u = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Utilisation de crédit non trouvée: " + id));
        boolean dejaEmis = u.getStatut() == StatutUtilisation.CERTIFICAT_EMIS;
        String code = dejaEmis ? null : codeBlocageEmissionCertificat(u, user, Role.PRESIDENT);
        return CertificatUtilisationEmissionDto.builder()
                .emissible(code == null && !dejaEmis)
                .codeBlocage(code)
                .motifBlocage(code == null ? null
                        : exceptionBlocageEmissionCertificat(code, u, user, Role.PRESIDENT).getMessage())
                .statut(u.getStatut())
                .statutPrealableAttendu(statutPrealableEmission(u))
                .numeroCertificatUtilisation(u.getNumeroCertificatUtilisation())
                .dateCertificatUtilisation(u.getDateCertificatUtilisation())
                .certificatSigneDepose(documentService.findActiveDocumentTypes(id)
                        .contains(TypeDocument.CERTIFICAT_UTILISATION.name()))
                .build();
    }

    /**
     * Le certificat d'utilisation doit avoir été émis par le Président avant la clôture.
     *
     * <p>Trois exemptions, dans cet ordre. {@code emissionCertificatRequise = FALSE} marque les
     * dossiers arrivés à leur statut final avant l'introduction de l'étape — le contrôle de la base
     * de production en a dénombré 107, liquidés ou apurés entre 2023 et 2025 : les bloquer serait une
     * régression pure. {@code origineArchiveLibelle} couvre les reprises d'archive, et la présence
     * d'un numéro les dossiers TVA numérotés sous l'ancienne règle, quand l'apurement numérotait
     * lui-même.
     *
     * <p>Aucune de ces exemptions n'est une date de bascule codée en dur : le marqueur est posé par
     * le calcul de la DGTCP lui-même, de sorte que le rattrapage au démarrage ne peut pas déborder
     * sur les dossiers instruits ensuite.
     */
    private void assertCertificatEmisAvantCloture(UtilisationCredit u) {
        if (u.getNumeroCertificatUtilisation() != null) {
            return;                       // certificat émis : c'est le discriminant principal
        }
        if (Boolean.FALSE.equals(u.getEmissionCertificatRequise())) {
            return;                       // dossier historique, antérieur à l'étape d'émission
        }
        if (u.getOrigineArchiveLibelle() != null) {
            return;                       // reprise d'archive : jamais passée par le circuit
        }
        throw ApiException.conflict(ApiErrorCode.CERTIFICAT_UTILISATION_NON_EMIS,
                "Le certificat d'utilisation doit être émis par le Président avant la clôture");
    }

    @Transactional
    public UtilisationCreditDto updateStatut(Long id, StatutUtilisation statut, AuthenticatedUser user) {
        UtilisationCredit entity = repository.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + id));
        if (entity.getStatut() == StatutUtilisation.BROUILLON && statut == StatutUtilisation.DEMANDEE) {
            return soumettreBrouillon(id, user);
        }
        workflow.validateTransition(entity.getStatut(), statut);

        assertActorCanTransition(entity, statut, user);

        if (entity.getType() == TypeUtilisation.DOUANIER && statut == StatutUtilisation.LIQUIDEE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Liquidation Douane: veuillez utiliser POST /{id}/liquidation-douane avec les montants d'imputation");
        }
        if (entity.getType() == TypeUtilisation.TVA_INTERIEURE && statut == StatutUtilisation.APUREE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Apurement TVA: veuillez utiliser POST /{id}/apurement-tva (calcul FIFO automatique)");
        }
        // Le Président détient toutes les permissions énumérées par PATCH /{id}/statut : il
        // franchit l'annotation. Seul ce garde-fou applicatif force le passage par la route dédiée.
        if (statut == StatutUtilisation.CERTIFICAT_EMIS) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Émission du certificat d'utilisation : veuillez utiliser POST /{id}/certificat-utilisation");
        }
        // La DGTCP détient les permissions listées par PATCH /{id}/statut : sans ce garde-fou elle
        // atteindrait le statut en contournant les contrôles de transmettreAuPresident.
        if (statut == StatutUtilisation.TRANSMISE_AU_PRESIDENT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Transmission au Président : veuillez utiliser POST /{id}/transmission-president");
        }
        if (statut == StatutUtilisation.CLOTUREE) {
            assertCertificatEmisAvantCloture(entity);
        }

        if (statut == StatutUtilisation.EN_VERIFICATION) {
            assertRequiredDocumentsPresent(entity);
        }

        entity.setStatut(statut);
        entity = repository.save(entity);
        UtilisationCreditDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(id), result);
        notifyUtilisationStatutChange(entity, statut, user);
        return result;
    }

    @Transactional(readOnly = true)
    public List<QuittanceTresorDto> getQuittances(Long utilisationId, AuthenticatedUser user) {
        UtilisationCreditDto dossier = findById(utilisationId, user);
        // Les montants et références restent lisibles : c'est le justificatif scanné qui est
        // réservé, pas le fait qu'une quittance existe.
        boolean justificatifsVisibles = DocumentVisibilitePolicy.visiblePour(
                "QUITTANCE_TRESOR", dossier.getStatut(), user != null ? user.getRole() : null);
        return quittanceTresorRepository.findByUtilisationDouaniere_IdOrderByDateQuittanceAscIdAsc(utilisationId)
                .stream()
                .map(q -> QuittanceTresorDto.builder()
                        .id(q.getId())
                        .numeroQuittance(q.getNumeroQuittance())
                        .dateQuittance(q.getDateQuittance())
                        .montant(q.getMontant())
                        .referencePaiement(q.getReferencePaiement())
                        .utilisationDouaniereId(utilisationId)
                        .documentChemin(justificatifsVisibles ? q.getDocumentChemin() : null)
                        .documentNomFichier(justificatifsVisibles ? q.getDocumentNomFichier() : null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Retire du DTO les justificatifs que l'appelant n'a pas à voir.
     *
     * <p>Post-traitement, et non seconde branche de construction : {@code toDto} sert aussi à
     * alimenter le journal d'audit, qui doit conserver la forme complète. On construit donc une fois,
     * on journalise, puis on masque pour la réponse.
     */
    private UtilisationCreditDto appliquerVisibiliteDocuments(UtilisationCreditDto dto,
                                                              StatutUtilisation statut,
                                                              AuthenticatedUser user) {
        if (dto == null) {
            return null;
        }
        Role role = user != null ? user.getRole() : null;
        if (DocumentVisibilitePolicy.acteurVoitTout(role)) {
            return dto;
        }
        if (!DocumentVisibilitePolicy.visiblePour("QUITTANCE_TRESOR", statut, role)
                && dto.getQuittances() != null) {
            dto.getQuittances().forEach(q -> {
                q.setDocumentChemin(null);
                q.setDocumentNomFichier(null);
            });
        }
        if (!DocumentVisibilitePolicy.visiblePour("QUITTANCE_DGI", statut, role)
                && dto.getQuittanceDgi() != null) {
            dto.getQuittanceDgi().setDocumentChemin(null);
            dto.getQuittanceDgi().setDocumentNomFichier(null);
            dto.getQuittanceDgi().setDocumentId(null);
        }
        return dto;
    }

    private ProcessusDocument resolveProcessus(UtilisationCredit utilisation) {
        if (utilisation == null || utilisation.getType() == null) {
            return ProcessusDocument.UTILISATION_CI;
        }
        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            return ProcessusDocument.UTILISATION_CI_DOUANE;
        }
        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            return ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE;
        }
        return ProcessusDocument.UTILISATION_CI;
    }

    private void assertActorCanTransition(UtilisationCredit utilisation, StatutUtilisation to, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (utilisation == null || utilisation.getType() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Type d'utilisation manquant");
        }
        Role role = user.getRole();

        // Verrouillage métier par type (TDR)
        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            Set<StatutUtilisation> douaneAllowed = EnumSet.of(
                    StatutUtilisation.INCOMPLETE, StatutUtilisation.A_RECONTROLER,
                    StatutUtilisation.EN_VERIFICATION,
                    StatutUtilisation.VISE, StatutUtilisation.EN_CONTROLE_DGD,
                    StatutUtilisation.CHEQUE_SAISI, StatutUtilisation.TRANSMISE_AU_PRESIDENT,
                    StatutUtilisation.ENVOYEE_AU_TRESOR,
                    StatutUtilisation.QUITTANCES_ENREGISTREES,
                    StatutUtilisation.LIQUIDEE, StatutUtilisation.CERTIFICAT_EMIS,
                    StatutUtilisation.CLOTUREE,
                    StatutUtilisation.REJETEE);
            if (!douaneAllowed.contains(to)) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Transition non autorisée (Douane): vers " + to);
            }
        }
        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            if (to != StatutUtilisation.INCOMPLETE
                    && to != StatutUtilisation.A_RECONTROLER
                    && to != StatutUtilisation.EN_VERIFICATION
                    && to != StatutUtilisation.VALIDEE
                    && to != StatutUtilisation.APUREE
                    && to != StatutUtilisation.CERTIFICAT_EMIS
                    && to != StatutUtilisation.REJETEE) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Transition non autorisée (TVA intérieure): vers " + to);
            }
        }

        if (to == StatutUtilisation.INCOMPLETE || to == StatutUtilisation.A_RECONTROLER) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Transition " + to + " est gérée automatiquement par le système (rejet temporaire / résolution)");
        }

        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            // Étapes DGD
            if (to == StatutUtilisation.EN_VERIFICATION || to == StatutUtilisation.VISE
                    || to == StatutUtilisation.EN_CONTROLE_DGD) {
                if (role != Role.DGD) {
                    throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGD peut traiter cette étape Douane");
                }
                return;
            }
            // Étapes DGTCP
            if (to == StatutUtilisation.TRANSMISE_AU_PRESIDENT
                    || to == StatutUtilisation.ENVOYEE_AU_TRESOR
                    || to == StatutUtilisation.QUITTANCES_ENREGISTREES
                    || to == StatutUtilisation.LIQUIDEE) {
                if (role != Role.DGTCP) {
                    throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut traiter cette étape Douane");
                }
                return;
            }
            // Étape Président : l'émission du certificat, postérieure au calcul DGTCP.
            if (to == StatutUtilisation.CERTIFICAT_EMIS) {
                assertPeutEmettreCertificat(role);
                return;
            }
            // Étape Entreprise / Commission Relais : chèque et clôture
            if (to == StatutUtilisation.CHEQUE_SAISI || to == StatutUtilisation.CLOTUREE) {
                if (role != Role.ENTREPRISE && role != Role.COMMISSION_RELAIS) {
                    throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seule l'entreprise peut effectuer cette action");
                }
                return;
            }
            if (to == StatutUtilisation.REJETEE) {
                if (role != Role.DGD && role != Role.DGTCP) {
                    throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGD ou DGTCP peut rejeter l'utilisation Douane");
                }
                return;
            }
            return;
        }

        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            // L'émission du certificat se teste AVANT la porte DGTCP : ce bloc n'a pas de
            // return par cible, donc inverser l'ordre rendrait la porte présidentielle
            // inatteignable et reproduirait le 403 que cette étape corrige.
            if (to == StatutUtilisation.CERTIFICAT_EMIS) {
                assertPeutEmettreCertificat(role);
                return;
            }
            if (role != Role.DGTCP) {
                throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP peut traiter les utilisations TVA intérieure");
            }
        }
    }

    /**
     * Porte de rôle de l'émission du certificat d'utilisation, commune aux deux branches.
     *
     * <p>{@code ADMIN_SI} est admis ici pour la seule route de substitution
     * ({@code POST /{id}/certificat-utilisation/admin}) : la route ordinaire exige la permission
     * {@code utilisation.president.certificat.emettre}, qu'il ne détient pas, et
     * {@link #codeBlocageEmissionCertificat} y réclame explicitement le rôle PRESIDENT.
     */
    private void assertPeutEmettreCertificat(Role role) {
        if (role != Role.PRESIDENT && role != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Seul le Président peut émettre le certificat d'utilisation");
        }
    }

    private void debitSolde(UtilisationCredit utilisation) {
        if (utilisation == null || utilisation.getCertificatCredit() == null) {
            return;
        }
        CertificatCredit certificat = utilisation.getCertificatCredit();
        BigDecimal montant = utilisation.getMontant() != null ? utilisation.getMontant() : BigDecimal.ZERO;
        if (montant.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            BigDecimal solde = certificat.getSoldeCordon() != null ? certificat.getSoldeCordon() : BigDecimal.ZERO;
            if (solde.compareTo(montant) < 0) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Solde cordon insuffisant pour imputer l'utilisation (solde=" + solde + ", montant=" + montant + ")");
            }
            certificat.setSoldeCordon(solde.subtract(montant));
        } else if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            BigDecimal solde = certificat.getSoldeTVA() != null ? certificat.getSoldeTVA() : BigDecimal.ZERO;
            if (solde.compareTo(montant) < 0) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                        "Solde TVA insuffisant pour imputer l'utilisation (solde=" + solde + ", montant=" + montant + ")");
            }
            certificat.setSoldeTVA(solde.subtract(montant));
        }
        certificatRepository.save(certificat);
    }

    private UtilisationCreditDto toDto(UtilisationCredit u) {
        CertificatCredit cert = u.getCertificatCredit();
        Long titId = cert != null && cert.getEntreprise() != null ? cert.getEntreprise().getId() : null;
        String titRs = cert != null && cert.getEntreprise() != null ? cert.getEntreprise().getRaisonSociale() : null;
        Long demandeurId = u.getEntreprise() != null ? u.getEntreprise().getId() : null;
        boolean demandeurSt = titId != null && demandeurId != null && !titId.equals(demandeurId);

        UtilisationCreditDto.UtilisationCreditDtoBuilder b = UtilisationCreditDto.builder()
                // Portes par la classe mere : les deux branches ont un certificat d'utilisation.
                .numeroCertificatUtilisation(u.getNumeroCertificatUtilisation())
                .dateCertificatUtilisation(u.getDateCertificatUtilisation())
                .id(u.getId())
                .reference(u.getReference())
                .type(u.getType())
                .dateDemande(u.getDateDemande())
                .montant(u.getMontant())
                .statut(u.getStatut())
                .dateLiquidation(u.getDateLiquidation())
                .certificatCreditId(cert != null ? cert.getId() : null)
                .certificatNumero(cert != null ? cert.getNumero() : null)
                .certificatReference(cert != null ? cert.getReference() : null)
                .entrepriseId(demandeurId)
                .entrepriseNom(u.getEntreprise() != null ? u.getEntreprise().getRaisonSociale() : null)
                .certificatTitulaireEntrepriseId(titId)
                .certificatTitulaireRaisonSociale(titRs)
                .demandeurEstSousTraitant(demandeurSt);

        if (u instanceof UtilisationDouaniere d) {
            List<LigneBulletinDto> lignesDto = d.getLignes() == null ? List.of() :
                    d.getLignes().stream()
                            .map(l -> {
                                Boolean modifieeParDgd = null;
                                if (l.getAffectation() != null && l.getAffectationEntreprise() != null) {
                                    modifieeParDgd = l.getAffectation() != l.getAffectationEntreprise();
                                }
                                return LigneBulletinDto.builder()
                                        .id(l.getId())
                                        .codeTaxe(l.getCodeTaxe())
                                        .denominationTaxe(l.getDenominationTaxe())
                                        .typeLigne(l.getTypeLigne())
                                        .valeurTaxe(l.getValeurTaxe())
                                        .affectationEntreprise(l.getAffectationEntreprise())
                                        .affectation(l.getAffectation())
                                        .affectationModifieeParDgd(modifieeParDgd)
                                        .build();
                            })
                            .collect(Collectors.toList());
            List<QuittanceTresorDto> quittancesDto = d.getQuittances() == null ? List.of() :
                    d.getQuittances().stream()
                            .map(q -> QuittanceTresorDto.builder()
                                    .id(q.getId())
                                    .numeroQuittance(q.getNumeroQuittance())
                                    .dateQuittance(q.getDateQuittance())
                                    .montant(q.getMontant())
                                    .referencePaiement(q.getReferencePaiement())
                                    .utilisationDouaniereId(d.getId())
                                    .documentChemin(q.getDocumentChemin())
                                    .documentNomFichier(q.getDocumentNomFichier())
                                    .build())
                            .collect(Collectors.toList());
            b.numeroDeclaration(d.getNumeroDeclaration())
                    .numeroBulletin(d.getNumeroBulletin())
                    .dateDeclaration(d.getDateDeclaration())
                    .enregistreeSYDONIA(d.getEnregistreeSYDONIA())
                    .soldeCordonAvant(d.getSoldeCordonAvant())
                    .soldeCordonApres(d.getSoldeCordonApres())
                    .lignes(lignesDto)
                    .totalPrisEnCharge(d.getTotalPrisEnCharge())
                    .totalAPayer(d.getTotalAPayer())
                    .montantTVADouane(d.getMontantTVA())
                    .montantDroits(d.getMontantDroits())
                    .banqueNom(d.getBanqueNom())
                    .numeroCheque(d.getNumeroCheque())
                    .montantCheque(d.getMontantCheque())
                    .dateCheque(d.getDateCheque())
                    .quittances(quittancesDto);
        } else if (u instanceof UtilisationTVAInterieure t) {
            b.typeAchat(t.getTypeAchat())
                    .numeroFacture(t.getNumeroFacture())
                    .dateFacture(t.getDateFacture())
                    .montantTVAInterieure(t.getMontantTVA())
                    .numeroDecompte(t.getNumeroDecompte())
                    .tvaDeductibleUtilisee(t.getTvaDeductibleUtilisee())
                    .tvaNette(t.getTvaNette())
                    .creditInterieurUtilise(t.getCreditInterieurUtilise())
                    .paiementEntreprise(t.getPaiementEntreprise())
                    .reportANouveau(t.getReportANouveau())
                    .soldeTVAAvant(t.getSoldeTVAAvant())
                    .soldeTVAApres(t.getSoldeTVAApres())
                    .quittanceDgi(quittanceDgiRepository.findByUtilisationCreditId(t.getId())
                            .map(UtilisationCreditService::toQuittanceDgiDto)
                            .orElse(null));
        }

        return b.build();
    }

}
