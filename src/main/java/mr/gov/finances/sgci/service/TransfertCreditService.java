package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.TransfertCredit;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.CreateTransfertCreditRequest;
import mr.gov.finances.sgci.web.dto.TransfertCreditDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
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

    @Transactional(readOnly = true)
    public List<TransfertCreditDto> findAll(AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
        }
        if (user.getRole() == Role.ENTREPRISE) {
            mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            if (u.getEntreprise() == null || u.getEntreprise().getId() == null) {
                return List.of();
            }
            return repository.findByCertificatCreditEntrepriseId(u.getEntreprise().getId())
                    .stream().map(this::toDto).collect(Collectors.toList());
        }
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TransfertCreditDto findById(Long id, AuthenticatedUser user) {
        TransfertCredit t = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transfert de crédit non trouvé: " + id));
        if (user != null && user.getRole() == Role.ENTREPRISE) {
            mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                    .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
            Long entId = u.getEntreprise() != null ? u.getEntreprise().getId() : null;
            Long sourceEntId = t.getCertificatCredit() != null && t.getCertificatCredit().getEntreprise() != null
                    ? t.getCertificatCredit().getEntreprise().getId()
                    : null;
            if (entId == null || sourceEntId == null || !entId.equals(sourceEntId)) {
                throw new RuntimeException("Accès refusé: transfert hors périmètre");
            }
        }
        return toDto(t);
    }

    @Transactional
    public TransfertCreditDto create(CreateTransfertCreditRequest request, AuthenticatedUser user) {
        if (request == null) {
            throw new RuntimeException("Requête invalide");
        }
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.ENTREPRISE) {
            throw new RuntimeException("Seule l'entreprise peut soumettre une demande de transfert");
        }

        CertificatCredit source = certificatRepository.findById(request.getCertificatCreditId())
                .orElseThrow(() -> new RuntimeException("Certificat source non trouvé"));
        if (source.getStatut() != StatutCertificat.OUVERT) {
            throw new RuntimeException("Le certificat source doit être OUVERT");
        }

        mr.gov.finances.sgci.domain.entity.Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        if (u.getEntreprise() == null || u.getEntreprise().getId() == null) {
            throw new RuntimeException("Aucune entreprise liée à l'utilisateur");
        }
        if (source.getEntreprise() == null || source.getEntreprise().getId() == null
                || !source.getEntreprise().getId().equals(u.getEntreprise().getId())) {
            throw new RuntimeException("Accès refusé: certificat source ne correspond pas à l'entreprise");
        }

        BigDecimal montant = request.getMontant() != null ? request.getMontant() : BigDecimal.ZERO;
        if (montant.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Montant de transfert invalide");
        }

        TransfertCredit entity = TransfertCredit.builder()
                .dateDemande(Instant.now())
                .certificatCredit(source)
                .montant(montant)
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

    @Transactional
    public TransfertCreditDto validateByDgtcp(Long id, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw new RuntimeException("Seul DGTCP peut valider un transfert");
        }

        TransfertCredit transfert = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transfert de crédit non trouvé: " + id));

        if (transfert.getStatut() != StatutTransfert.DEMANDE && transfert.getStatut() != StatutTransfert.EN_COURS) {
            throw new RuntimeException("Statut invalide pour validation: " + transfert.getStatut());
        }

        requirementValidator.assertRequiredDocumentsPresent(
                ProcessusDocument.TRANSFERT_CREDIT,
                documentService.findActiveDocumentTypes(transfert.getId())
        );

        // Règle métier: clôture douane obligatoire (au moins déclarative)
        if (!Boolean.TRUE.equals(transfert.getOperationsDouaneCloturees())) {
            throw new RuntimeException("Transfert impossible: opérations douane non clôturées");
        }

        CertificatCredit source = transfert.getCertificatCredit();
        if (source == null || source.getId() == null) {
            throw new RuntimeException("Certificat source manquant");
        }
        if (source.getStatut() != StatutCertificat.OUVERT) {
            throw new RuntimeException("Le certificat source doit être OUVERT");
        }

        BigDecimal montant = transfert.getMontant() != null ? transfert.getMontant() : BigDecimal.ZERO;
        if (montant.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Montant de transfert invalide");
        }

        // Transfert interne: solde Douane -> solde Intérieur (TVA)
        BigDecimal soldeDouane = source.getSoldeCordon() != null ? source.getSoldeCordon() : BigDecimal.ZERO;
        if (soldeDouane.compareTo(montant) < 0) {
            throw new RuntimeException("Solde douane insuffisant pour transfert (solde=" + soldeDouane + ", montant=" + montant + ")");
        }
        BigDecimal soldeInterieur = source.getSoldeTVA() != null ? source.getSoldeTVA() : BigDecimal.ZERO;

        source.setSoldeCordon(soldeDouane.subtract(montant));
        source.setSoldeTVA(soldeInterieur.add(montant));

        certificatRepository.save(source);

        transfert.setStatut(StatutTransfert.TRANSFERE);
        transfert = repository.save(transfert);

        TransfertCreditDto result = toDto(transfert);
        auditService.log(AuditAction.UPDATE, "TransfertCredit", String.valueOf(transfert.getId()), result);
        return result;
    }

    @Transactional
    public TransfertCreditDto rejectByDgtcp(Long id, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        if (user.getRole() != Role.DGTCP) {
            throw new RuntimeException("Seul DGTCP peut rejeter un transfert");
        }

        TransfertCredit transfert = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transfert de crédit non trouvé: " + id));

        if (transfert.getStatut() == StatutTransfert.TRANSFERE) {
            throw new RuntimeException("Transfert déjà exécuté");
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
