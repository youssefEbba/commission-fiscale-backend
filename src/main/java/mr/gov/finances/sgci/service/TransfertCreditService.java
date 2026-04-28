package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.workflow.UtilisationCreditWorkflow;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.EffectiveIdentityService;
import mr.gov.finances.sgci.web.dto.CreateTransfertCreditRequest;
import mr.gov.finances.sgci.web.dto.TransfertCreditDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransfertCreditService {

    private final TransfertCreditRepository repository;
    private final CertificatCreditRepository certificatRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentTransfertCreditService documentService;
    private final DocumentRequirementValidator requirementValidator;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final EffectiveIdentityService effectiveIdentityService;
    private final UtilisationCreditRepository utilisationCreditRepository;
    private final UtilisationCreditWorkflow utilisationCreditWorkflow;

    @Transactional(readOnly = true)
    public List<TransfertCreditDto> findAll(AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
        }
        if (user.getRole() == Role.ENTREPRISE) {
            mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
            Long entId = effectiveIdentityService.resolveEntrepriseId(user, u);
            if (entId == null) {
                return List.of();
            }
            return repository.findByCertificatCreditEntrepriseId(entId)
                    .stream().map(this::toDto).collect(Collectors.toList());
        }
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TransfertCreditDto findById(Long id, AuthenticatedUser user) {
        TransfertCredit t = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Transfert de crédit non trouvé: " + id));
        if (user != null && user.getRole() == Role.ENTREPRISE) {
            mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
            Long entId = effectiveIdentityService.resolveEntrepriseId(user, u);
            Long sourceEntId = t.getCertificatCredit() != null && t.getCertificatCredit().getEntreprise() != null
                    ? t.getCertificatCredit().getEntreprise().getId()
                    : null;
            if (entId == null || sourceEntId == null || !entId.equals(sourceEntId)) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: transfert hors périmètre");
            }
        }
        return toDto(t);
    }

    /**
     * Dernière ligne de transfert pour un certificat (s’il en existe une), ou {@code null}.
     */
    @Transactional(readOnly = true)
    public TransfertCreditDto findByCertificatCreditId(Long certificatCreditId, AuthenticatedUser user) {
        CertificatCredit cert = certificatRepository.findById(certificatCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat non trouvé"));
        assertTitulaireCanAccessCertificatForTransfert(cert, user);
        return repository.findFirstByCertificatCreditIdOrderByIdDesc(certificatCreditId)
                .map(this::toDto)
                .orElse(null);
    }

    private void assertTitulaireCanAccessCertificatForTransfert(CertificatCredit cert, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            return;
        }
        if (user.getRole() != Role.ENTREPRISE) {
            return;
        }
        mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Long entId = effectiveIdentityService.resolveEntrepriseId(user, u);
        Long titId = cert.getEntreprise() != null ? cert.getEntreprise().getId() : null;
        if (entId == null || titId == null || !entId.equals(titId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: certificat hors périmètre");
        }
    }

    @Transactional
    public TransfertCreditDto create(CreateTransfertCreditRequest request, AuthenticatedUser user) {
        if (request == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Requête invalide");
        }
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.ENTREPRISE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seule l'entreprise peut soumettre une demande de transfert");
        }

        CertificatCredit source = certificatRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat source non trouvé"));
        if (source.getStatut() != StatutCertificat.OUVERT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le certificat source doit être OUVERT");
        }

        mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Long myEnt = effectiveIdentityService.resolveEntrepriseId(user, u);
        if (myEnt == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune entreprise liée à l'utilisateur");
        }
        if (source.getEntreprise() == null || source.getEntreprise().getId() == null
                || !source.getEntreprise().getId().equals(myEnt)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Accès refusé: certificat source ne correspond pas à l'entreprise");
        }

        BigDecimal montantIndicatif = request.getMontant() != null
                ? request.getMontant()
                : (source.getTvaImportationDouane() != null ? source.getTvaImportationDouane() : BigDecimal.ZERO);
        if (montantIndicatif.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Montant de transfert invalide");
        }

        Optional<TransfertCredit> dejaOpt = repository.findFirstByCertificatCreditIdOrderByIdDesc(source.getId());
        if (dejaOpt.isPresent()) {
            TransfertCredit ex = dejaOpt.get();
            StatutTransfert prev = ex.getStatut();
            if (prev == StatutTransfert.TRANSFERE) {
                throw ApiException.conflict(ApiErrorCode.CONFLICT,
                        "Un transfert a déjà été exécuté pour ce certificat; aucun second transfert n'est prévu sur le même certificat.");
            }
            if (prev == StatutTransfert.DEMANDE || prev == StatutTransfert.EN_COURS || prev == StatutTransfert.VALIDE) {
                throw ApiException.conflict(ApiErrorCode.CONFLICT, "Une demande de transfert est déjà en cours pour ce certificat.");
            }
            if (prev == StatutTransfert.REJETE) {
                documentService.deactivateAllForTransfert(ex.getId());
                ex.setDateDemande(Instant.now());
                ex.setMontant(montantIndicatif);
                ex.setOperationsDouaneCloturees(Boolean.TRUE.equals(request.getOperationsDouaneCloturees()));
                ex.setStatut(StatutTransfert.DEMANDE);
                ex = repository.save(ex);
                TransfertCreditDto renewed = toDto(ex);
                auditService.log(AuditAction.UPDATE, "TransfertCredit", String.valueOf(ex.getId()), renewed);
                notificationService.notifyUsers(
                        utilisateurRepository.findByRole(Role.DGTCP).stream()
                                .map(mr.gov.finances.sgci.domain.entity.Utilisateur::getId)
                                .collect(Collectors.toList()),
                        NotificationType.TRANSFERT_CREDIT,
                        "TransfertCredit",
                        ex.getId(),
                        "Nouvelle demande de transfert de crédit (après rejet)",
                        Collections.singletonMap("id", ex.getId())
                );
                return renewed;
            }
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Statut de transfert incompatible pour une nouvelle demande: " + prev);
        }

        TransfertCredit entity = TransfertCredit.builder()
                .dateDemande(Instant.now())
                .certificatCredit(source)
                .montant(montantIndicatif)
                .operationsDouaneCloturees(Boolean.TRUE.equals(request.getOperationsDouaneCloturees()))
                .statut(StatutTransfert.DEMANDE)
                .build();

        entity = repository.save(entity);
        TransfertCreditDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "TransfertCredit", String.valueOf(entity.getId()), result);
        notificationService.notifyUsers(
                utilisateurRepository.findByRole(Role.DGTCP).stream().map(mr.gov.finances.sgci.domain.entity.Utilisateur::getId).collect(Collectors.toList()),
                NotificationType.TRANSFERT_CREDIT,
                "TransfertCredit",
                entity.getId(),
                "Nouvelle demande de transfert de crédit",
                Collections.singletonMap("id", entity.getId())
        );
        return result;
    }

    private void assertDgtcpOuPresidentPourDecision(AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP && user.getRole() != Role.PRESIDENT) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Seul DGTCP ou le Président peut valider ou rejeter un transfert");
        }
    }

    @Transactional
    public TransfertCreditDto validateByDgtcp(Long id, AuthenticatedUser user) {
        assertDgtcpOuPresidentPourDecision(user);

        TransfertCredit transfert = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Transfert de crédit non trouvé: " + id));

        StatutTransfert st = transfert.getStatut();
        if (st != StatutTransfert.DEMANDE && st != StatutTransfert.EN_COURS && st != StatutTransfert.VALIDE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Statut invalide pour validation: " + st);
        }

        requirementValidator.assertRequiredDocumentsPresent(
                ProcessusDocument.TRANSFERT_CREDIT,
                documentService.findActiveDocumentTypes(transfert.getId())
        );

        // Règle métier: clôture douane obligatoire (au moins déclarative)
        if (!Boolean.TRUE.equals(transfert.getOperationsDouaneCloturees())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Transfert impossible: opérations douane non clôturées");
        }

        CertificatCredit source = certificatRepository.findById(transfert.getCertificatCredit().getId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Certificat source non trouvé"));
        if (source.getStatut() != StatutCertificat.OUVERT) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le certificat source doit être OUVERT");
        }

        // Transfert du restant (d) — TVA à l'import — vers le solde TVA intérieure (soldeTVA). Le stock FIFO n'est pas modifié.
        BigDecimal dReste = source.getTvaImportationDouane() != null ? source.getTvaImportationDouane() : BigDecimal.ZERO;
        BigDecimal soldeInterieur = source.getSoldeTVA() != null ? source.getSoldeTVA() : BigDecimal.ZERO;

        source.setSoldeTVA(soldeInterieur.add(dReste));
        source.setTvaImportationDouane(BigDecimal.ZERO);

        certificatRepository.save(source);

        transfert.setMontant(dReste);
        cloturerUtilisationsDouanieresOuvertes(source.getId());

        transfert.setStatut(StatutTransfert.TRANSFERE);
        transfert = repository.save(transfert);

        TransfertCreditDto result = toDto(transfert);
        auditService.log(AuditAction.UPDATE, "TransfertCredit", String.valueOf(transfert.getId()), result);
        return result;
    }

    /**
     * Passe en {@link StatutUtilisation#CLOTUREE} les utilisations douanières non terminées (hors LIQUIDEE / REJETEE).
     */
    private void cloturerUtilisationsDouanieresOuvertes(Long certificatCreditId) {
        for (UtilisationCredit u : utilisationCreditRepository.findByCertificatCreditId(certificatCreditId)) {
            if (u.getType() != TypeUtilisation.DOUANIER) {
                continue;
            }
            StatutUtilisation s = u.getStatut();
            if (s == StatutUtilisation.LIQUIDEE || s == StatutUtilisation.REJETEE || s == StatutUtilisation.CLOTUREE) {
                continue;
            }
            utilisationCreditWorkflow.validateTransition(s, StatutUtilisation.CLOTUREE);
            u.setStatut(StatutUtilisation.CLOTUREE);
            utilisationCreditRepository.save(u);
            auditService.log(AuditAction.UPDATE, "UtilisationCredit", String.valueOf(u.getId()),
                    Map.of("action", "cloture_suite_transfert_credit", "certificatCreditId", certificatCreditId));
        }
    }

    @Transactional
    public TransfertCreditDto rejectByDgtcp(Long id, AuthenticatedUser user) {
        assertDgtcpOuPresidentPourDecision(user);

        TransfertCredit transfert = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Transfert de crédit non trouvé: " + id));

        if (transfert.getStatut() == StatutTransfert.TRANSFERE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Transfert déjà exécuté");
        }
        if (transfert.getStatut() == StatutTransfert.REJETE) {
            return toDto(transfert);
        }

        transfert.setStatut(StatutTransfert.REJETE);
        transfert = repository.save(transfert);
        TransfertCreditDto result = toDto(transfert);
        auditService.log(AuditAction.UPDATE, "TransfertCredit", String.valueOf(transfert.getId()), result);
        return result;
    }

    private TransfertCreditDto toDto(TransfertCredit t) {
        if (t == null) {
            return null;
        }
        CertificatCredit c = t.getCertificatCredit();
        Long certId = c != null ? c.getId() : null;
        String certNumero = c != null ? c.getNumero() : null;
        Long entrepriseSourceId = c != null && c.getEntreprise() != null ? c.getEntreprise().getId() : null;

        return TransfertCreditDto.builder()
                .id(t.getId())
                .dateDemande(t.getDateDemande())
                .certificatCreditId(certId)
                .certificatNumero(certNumero)
                .entrepriseSourceId(entrepriseSourceId)
                .montant(t.getMontant())
                .operationsDouaneCloturees(t.getOperationsDouaneCloturees())
                .statut(t.getStatut())
                .build();
    }
}
