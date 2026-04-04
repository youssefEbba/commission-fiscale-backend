package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.DecisionCertificatCredit;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.entity.RejetTempResponse;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.DecisionCertificatCreditRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.DecisionCreditDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionCertificatCreditService {

    private static final Set<Role> VISA_REQUIRED_ROLES = EnumSet.of(Role.DGI, Role.DGD, Role.DGTCP);
    private static final Set<StatutCertificat> DECISION_ALLOWED_STATUTS = EnumSet.of(
            StatutCertificat.EN_CONTROLE, StatutCertificat.INCOMPLETE, StatutCertificat.A_RECONTROLER
    );

    private final DecisionCertificatCreditRepository decisionRepository;
    private final CertificatCreditRepository certificatRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional
    public DecisionCreditDto resolveRejetTemp(Long decisionId, AuthenticatedUser user) {
        if (decisionId == null) {
            throw new RuntimeException("Décision invalide");
        }
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }

        DecisionCertificatCredit decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new RuntimeException("Décision certificat non trouvée: " + decisionId));

        if (decision.getDecision() != DecisionCorrectionType.REJET_TEMP || decision.getRejetTempStatus() != RejetTempStatus.OUVERT) {
            throw new RuntimeException("Résolution interdite: la décision n'est pas un REJET_TEMP OUVERT");
        }
        if (decision.getRole() == null || decision.getRole() != user.getRole()) {
            throw new RuntimeException("Résolution interdite: rôle non autorisé");
        }

        decision.setRejetTempStatus(RejetTempStatus.RESOLU);
        decision.setRejetTempResolvedAt(Instant.now());
        decision = decisionRepository.save(decision);

        Long certificatId = decision.getCertificatCredit() != null ? decision.getCertificatCredit().getId() : null;
        if (certificatId != null) {
            boolean anyOpen = !decisionRepository.findByCertificatCreditIdAndDecisionAndRejetTempStatus(
                    certificatId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT
            ).isEmpty();

            if (!anyOpen) {
                CertificatCredit certificat = certificatRepository.findById(certificatId).orElse(null);
                if (certificat != null && certificat.getStatut() == StatutCertificat.INCOMPLETE) {
                    certificat.setStatut(StatutCertificat.A_RECONTROLER);
                    certificatRepository.save(certificat);
                }
            }
        }

        return toDto(decision);
    }

    @Transactional
    public DecisionCreditDto saveDecision(Long certificatCreditId,
                                         DecisionCorrectionType decision,
                                         String motifRejet,
                                         Set<TypeDocument> documentsDemandes,
                                         AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw new RuntimeException("Utilisateur non authentifié");
        }
        Role role = user.getRole();
        if (!VISA_REQUIRED_ROLES.contains(role)) {
            throw new RuntimeException("Rôle non autorisé pour la décision mise en place: " + role
                    + ". Seuls DGI, DGD et DGTCP peuvent apposer un visa ou rejet temporaire.");
        }

        CertificatCredit certificat = certificatRepository.findById(certificatCreditId)
                .orElseThrow(() -> new RuntimeException("Certificat de crédit non trouvé: " + certificatCreditId));

        if (!DECISION_ALLOWED_STATUTS.contains(certificat.getStatut())) {
            throw new RuntimeException("Le certificat doit être en statut EN_CONTROLE, INCOMPLETE ou A_RECONTROLER "
                    + "pour recevoir un visa ou rejet. Statut actuel: " + certificat.getStatut());
        }

        if (decision == DecisionCorrectionType.REJET_TEMP) {
            if (motifRejet == null || motifRejet.isBlank()) {
                throw new RuntimeException("Le motif de rejet est obligatoire");
            }
            if (documentsDemandes == null || documentsDemandes.isEmpty()) {
                throw new RuntimeException("La liste des documents demandés est obligatoire");
            }
        }

        Utilisateur utilisateur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        DecisionCertificatCredit existingDecision = decisionRepository
                .findByCertificatCreditIdAndRole(certificatCreditId, role)
                .orElse(null);

        if (existingDecision != null) {
            if (existingDecision.getDecision() == DecisionCorrectionType.VISA) {
                throw new RuntimeException("Décision impossible: un visa a déjà été accordé par " + role
                        + ". Le visa clôture les interactions sur cette demande.");
            }
            if (decision == DecisionCorrectionType.VISA
                    && existingDecision.getDecision() == DecisionCorrectionType.REJET_TEMP
                    && existingDecision.getRejetTempStatus() == RejetTempStatus.OUVERT) {
                throw new RuntimeException("VISA impossible: un rejet temporaire est en cours pour " + role
                        + ". Résolvez d'abord le rejet via l'endpoint /resolve.");
            }
        }

        if (decision == DecisionCorrectionType.VISA && role == Role.DGTCP) {
            assertMontantsRenseignes(certificat);
        }

        DecisionCertificatCredit entity = existingDecision;
        if (entity == null) {
            entity = DecisionCertificatCredit.builder()
                    .certificatCredit(certificat)
                    .role(role)
                    .build();
        }

        entity.setDecision(decision);
        entity.setMotifRejet(decision == DecisionCorrectionType.REJET_TEMP ? motifRejet : null);
        if (decision == DecisionCorrectionType.REJET_TEMP) {
            entity.setDocumentsDemandes(new HashSet<>(documentsDemandes));
            entity.setRejetTempStatus(RejetTempStatus.OUVERT);
            entity.setRejetTempResolvedAt(null);
            certificat.setStatut(StatutCertificat.INCOMPLETE);
        } else {
            entity.getDocumentsDemandes().clear();
            entity.setRejetTempStatus(RejetTempStatus.RESOLU);
            entity.setRejetTempResolvedAt(Instant.now());
        }
        entity.setDateDecision(Instant.now());
        entity.setUtilisateur(utilisateur);

        entity = decisionRepository.save(entity);
        certificatRepository.save(certificat);

        if (decision == DecisionCorrectionType.VISA) {
            tryAutoTransitionToPresident(certificat);
        }

        auditService.log(AuditAction.UPDATE, "DecisionCertificatCredit",
                String.valueOf(entity.getId()), toDto(entity));

        return toDto(entity);
    }

    /**
     * Vérifie si les 3 visas (DGI, DGD, DGTCP) sont présents et qu'aucun
     * rejet temporaire n'est ouvert. Si oui, auto-transition vers EN_VALIDATION_PRESIDENT.
     */
    private void tryAutoTransitionToPresident(CertificatCredit certificat) {
        Long certId = certificat.getId();

        boolean anyOpenRejet = !decisionRepository.findByCertificatCreditIdAndDecisionAndRejetTempStatus(
                certId, DecisionCorrectionType.REJET_TEMP, RejetTempStatus.OUVERT).isEmpty();
        if (anyOpenRejet) {
            return;
        }

        boolean allVisas = VISA_REQUIRED_ROLES.stream().allMatch(r ->
                decisionRepository.existsByCertificatCreditIdAndRoleAndDecision(
                        certId, r, DecisionCorrectionType.VISA));

        if (allVisas) {
            certificat.setStatut(StatutCertificat.EN_VALIDATION_PRESIDENT);
            certificatRepository.save(certificat);

            notifyPresidentForValidation(certificat);
        }
    }

    private void assertMontantsRenseignes(CertificatCredit entity) {
        if (entity.getMontantCordon() == null || entity.getMontantTVAInterieure() == null) {
            throw new RuntimeException("DGTCP doit renseigner les montants (cordon et TVA intérieure) avant d'apposer le visa");
        }
        if (entity.getMontantCordon().compareTo(BigDecimal.ZERO) <= 0
                || entity.getMontantTVAInterieure().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Les montants (cordon et TVA intérieure) doivent être strictement supérieurs à zéro");
        }
    }

    private void notifyPresidentForValidation(CertificatCredit certificat) {
        List<Long> presidentIds = utilisateurRepository.findByRole(Role.PRESIDENT)
                .stream().map(Utilisateur::getId).collect(Collectors.toList());
        if (presidentIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("statut", StatutCertificat.EN_VALIDATION_PRESIDENT.name());
        payload.put("numero", certificat.getNumero());
        String message = "Le certificat " + certificat.getNumero()
                + " a reçu les 3 visas (DGI, DGD, DGTCP) et attend votre validation.";
        notificationService.notifyUsers(presidentIds, NotificationType.CERTIFICAT_STATUT_CHANGE,
                "CertificatCredit", certificat.getId(), message, payload);
    }

    @Transactional(readOnly = true)
    public List<DecisionCreditDto> findByCertificat(Long certificatCreditId) {
        return decisionRepository.findByCertificatCreditId(certificatCreditId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private DecisionCreditDto toDto(DecisionCertificatCredit entity) {
        return DecisionCreditDto.builder()
                .id(entity.getId())
                .role(entity.getRole())
                .decision(entity.getDecision())
                .motifRejet(entity.getMotifRejet())
                .documentsDemandes(entity.getDocumentsDemandes())
                .dateDecision(entity.getDateDecision())
                .rejetTempStatus(entity.getRejetTempStatus())
                .rejetTempResolvedAt(entity.getRejetTempResolvedAt())
                .utilisateurId(entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null)
                .utilisateurNom(entity.getUtilisateur() != null ? entity.getUtilisateur().getNomComplet() : null)
                .rejetTempResponses(entity.getRejetTempResponses() != null
                        ? entity.getRejetTempResponses().stream().map(this::toResponseDto).collect(Collectors.toList())
                        : java.util.List.of())
                .build();
    }

    private mr.gov.finances.sgci.web.dto.RejetTempResponseDto toResponseDto(RejetTempResponse entity) {
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
}
