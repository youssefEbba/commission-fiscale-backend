package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.ReclamationDemandeCorrection;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutReclamationCorrection;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.ReclamationDemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.ReclamationDemandeCorrectionDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import mr.gov.finances.sgci.workflow.DemandeCorrectionWorkflow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ReclamationDemandeCorrectionService {

    private static final int TEXTE_MAX = 4000;
    private static final int MOTIF_REJET_MAX = 2000;

    private final ReclamationDemandeCorrectionRepository reclamationRepository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DemandeCorrectionWorkflow workflow;
    private final DemandeCorrectionService demandeCorrectionService;
    private final DocumentService documentService;
    private final MinioService minioService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ReclamationDemandeCorrectionDto> listByDemande(Long demandeId, AuthenticatedUser user) {
        assertCanReadDemande(demandeId, user);
        return reclamationRepository.findByDemandeCorrection_IdOrderByDateCreationDesc(demandeId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReclamationDemandeCorrectionDto create(Long demandeId, String texte, MultipartFile file, AuthenticatedUser user) throws IOException {
        assertActorUser(user);
        Role role = loadRole(user);
        if (role != Role.AUTORITE_CONTRACTANTE && role != Role.AUTORITE_UPM && role != Role.AUTORITE_UEP && role != Role.ENTREPRISE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Seuls l'autorité contractante, les délégués UPM/UEP ou l'entreprise peuvent déposer une réclamation");
        }
        if (!demandeCorrectionService.userCanAccessDemandeCorrection(demandeId, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: demande hors périmètre");
        }
        if (texte == null || texte.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le texte de la réclamation est obligatoire");
        }
        String texteTrim = texte.trim();
        if (texteTrim.length() > TEXTE_MAX) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Texte trop long (max " + TEXTE_MAX + " caractères)");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Une pièce jointe est obligatoire pour chaque réclamation");
        }

        DemandeCorrection demande = demandeCorrectionRepository.findById(demandeId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + demandeId));

        workflow.assertDemandeStatutAllowsReclamation(demande.getStatut());

        if (reclamationRepository.existsByDemandeCorrection_IdAndStatut(demandeId, StatutReclamationCorrection.SOUMISE)) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Une réclamation est déjà en cours pour cette demande");
        }

        Utilisateur auteur = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        ReclamationDemandeCorrection entity = ReclamationDemandeCorrection.builder()
                .demandeCorrection(demande)
                .utilisateur(auteur)
                .texte(texteTrim)
                .statut(StatutReclamationCorrection.SOUMISE)
                .pieceJointeChemin(fileUrl)
                .pieceJointeNomFichier(originalFilename != null ? originalFilename : file.getName())
                .pieceJointeTaille(file.getSize())
                .pieceJointeDateUpload(Instant.now())
                .build();

        entity = reclamationRepository.save(entity);
        ReclamationDemandeCorrectionDto dto = toDto(entity);
        auditService.log(AuditAction.CREATE, "ReclamationDemandeCorrection", String.valueOf(entity.getId()), dto);

        notifyTreasuryOnNewReclamation(demande, entity.getId());
        return dto;
    }

    @Transactional
    public ReclamationDemandeCorrectionDto traiter(Long demandeId, Long reclamationId, boolean acceptee, String motifReponse, MultipartFile file, AuthenticatedUser user) throws IOException {
        assertActorUser(user);
        Role role = loadRole(user);
        if (role != Role.DGTCP && role != Role.PRESIDENT) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Seuls le DGTCP ou le Président peuvent traiter une réclamation");
        }
        if (!demandeCorrectionService.userCanAccessDemandeCorrection(demandeId, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: demande hors périmètre");
        }

        ReclamationDemandeCorrection rec = reclamationRepository.findByIdAndDemandeCorrection_Id(reclamationId, demandeId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Réclamation non trouvée"));

        if (rec.getStatut() != StatutReclamationCorrection.SOUMISE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Cette réclamation a déjà été traitée");
        }

        if (!acceptee) {
            if (motifReponse == null || motifReponse.isBlank()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de rejet est obligatoire");
            }
            String motifTrim = motifReponse.trim();
            if (motifTrim.length() > MOTIF_REJET_MAX) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Motif trop long (max " + MOTIF_REJET_MAX + " caractères)");
            }
            if (file == null || file.isEmpty()) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Un document de réponse est obligatoire pour rejeter une réclamation");
            }
            String originalFilename = file.getOriginalFilename();
            String fileUrl = minioService.uploadFile(file);
            rec.setStatut(StatutReclamationCorrection.REJETEE);
            rec.setMotifReponse(motifTrim);
            rec.setReponseRejetChemin(fileUrl);
            rec.setReponseRejetNomFichier(originalFilename != null ? originalFilename : file.getName());
            rec.setReponseRejetTaille(file.getSize());
            rec.setReponseRejetDateUpload(Instant.now());
            rec.setDateTraitement(Instant.now());
            rec.setTraiteParUserId(user.getUserId());
            rec = reclamationRepository.save(rec);
            ReclamationDemandeCorrectionDto dto = toDto(rec);
            auditService.log(AuditAction.UPDATE, "ReclamationDemandeCorrection", String.valueOf(rec.getId()), dto);
            notifyAuteursReclamationTraitee(rec, demandeId, false);
            return dto;
        }

        if (file != null && !file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Ne pas envoyer de fichier pour une acceptation");
        }

        if (role != Role.DGTCP) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Seul le DGTCP peut accepter une réclamation et rouvrir le dossier");
        }

        DemandeCorrection demande = rec.getDemandeCorrection();
        workflow.assertDemandeStatutAllowsReopenAfterReclamation(demande.getStatut());

        rec.setStatut(StatutReclamationCorrection.ACCEPTEE);
        rec.setMotifReponse(motifReponse != null ? motifReponse.trim() : null);
        rec.setDateTraitement(Instant.now());
        rec.setTraiteParUserId(user.getUserId());
        rec = reclamationRepository.save(rec);

        documentService.archiveAdoptionEtOffresCorrigePourRouverture(demande.getId());

        resetParallelValidations(demande);
        demande.setStatut(StatutDemande.RECUE);
        demande.setMotifRejet(null);
        demandeCorrectionRepository.save(demande);

        ReclamationDemandeCorrectionDto dto = toDto(rec);
        auditService.log(AuditAction.UPDATE, "ReclamationDemandeCorrection", String.valueOf(rec.getId()), dto);
        auditService.log(AuditAction.UPDATE, "DemandeCorrection", String.valueOf(demande.getId()),
                Map.of("statut", StatutDemande.RECUE.name(), "motif", "Réclamation acceptée (DGTCP)"));

        demandeCorrectionService.notifyCorrectionStatutChange(demande, StatutDemande.RECUE, user, null, false);
        notifyAuteursReclamationTraitee(rec, demandeId, true);
        return dto;
    }

    /**
     * Annule une réclamation encore {@link StatutReclamationCorrection#SOUMISE} avant traitement DGTCP.
     * La demande de correction conserve son statut et ses visas.
     * Autorisé : auteur de la réclamation, ou utilisateur {@link Role#AUTORITE_CONTRACTANTE} de la même autorité que la demande.
     */
    @Transactional
    public ReclamationDemandeCorrectionDto annuler(Long demandeId, Long reclamationId, AuthenticatedUser user) {
        assertActorUser(user);
        Role role = loadRole(user);
        if (role != Role.AUTORITE_CONTRACTANTE && role != Role.AUTORITE_UPM && role != Role.AUTORITE_UEP && role != Role.ENTREPRISE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Seuls l'autorité contractante, les délégués UPM/UEP ou l'entreprise peuvent annuler une réclamation");
        }
        if (!demandeCorrectionService.userCanAccessDemandeCorrection(demandeId, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: demande hors périmètre");
        }

        ReclamationDemandeCorrection rec = reclamationRepository.findByIdAndDemandeCorrection_Id(reclamationId, demandeId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Réclamation non trouvée"));

        if (rec.getStatut() != StatutReclamationCorrection.SOUMISE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Seule une réclamation en cours (SOUMISE) peut être annulée");
        }

        DemandeCorrection demande = rec.getDemandeCorrection();
        if (demande == null || !demande.getId().equals(demandeId)) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Réclamation incohérente avec la demande");
        }

        if (!peutAnnulerReclamation(rec, demande, user, role)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED,
                    "Seul l'auteur de la réclamation ou l'autorité contractante du dossier peut annuler");
        }

        rec.setStatut(StatutReclamationCorrection.ANNULEE);
        rec.setDateTraitement(Instant.now());
        rec.setTraiteParUserId(user.getUserId());
        rec = reclamationRepository.save(rec);

        ReclamationDemandeCorrectionDto dto = toDto(rec);
        auditService.log(AuditAction.UPDATE, "ReclamationDemandeCorrection", String.valueOf(rec.getId()), dto);

        notifyTreasuryReclamationAnnulee(demande, rec.getId());
        return dto;
    }

    private boolean peutAnnulerReclamation(ReclamationDemandeCorrection rec, DemandeCorrection demande, AuthenticatedUser user, Role role) {
        boolean estAuteur = rec.getUtilisateur() != null && user.getUserId().equals(rec.getUtilisateur().getId());
        if (estAuteur) {
            return true;
        }
        if (role != Role.AUTORITE_CONTRACTANTE) {
            return false;
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId()).orElse(null);
        if (u == null || u.getAutoriteContractante() == null || demande.getAutoriteContractante() == null) {
            return false;
        }
        return u.getAutoriteContractante().getId().equals(demande.getAutoriteContractante().getId());
    }

    private void notifyTreasuryReclamationAnnulee(DemandeCorrection demande, Long reclamationId) {
        List<Long> cibles = Stream.concat(
                utilisateurRepository.findByRole(Role.DGTCP).stream().map(Utilisateur::getId),
                utilisateurRepository.findByRole(Role.PRESIDENT).stream().map(Utilisateur::getId)
        ).distinct().collect(Collectors.toList());
        if (cibles.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("demandeCorrectionId", demande.getId());
        payload.put("reclamationId", reclamationId);
        payload.put("reclamationStatut", StatutReclamationCorrection.ANNULEE.name());
        if (demande.getStatut() != null) {
            payload.put("statutDemandeInchange", demande.getStatut().name());
        }
        String msg = "Réclamation retirée — dossier " + demande.getNumero() + " (statut de la demande inchangé)";
        notificationService.notifyUsers(cibles, NotificationType.CORRECTION_DECISION,
                "ReclamationDemandeCorrection", reclamationId, msg, payload);
    }

    private void assertCanReadDemande(Long demandeId, AuthenticatedUser user) {
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        if (!demandeCorrectionService.userCanAccessDemandeCorrection(demandeId, user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: demande hors périmètre");
        }
    }

    private void assertActorUser(AuthenticatedUser user) {
        if (user == null || user.getUserId() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
    }

    private Role loadRole(AuthenticatedUser user) {
        return utilisateurRepository.findById(user.getUserId())
                .map(Utilisateur::getRole)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
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

    private void notifyTreasuryOnNewReclamation(DemandeCorrection demande, Long reclamationId) {
        List<Long> cibles = Stream.concat(
                utilisateurRepository.findByRole(Role.DGTCP).stream().map(Utilisateur::getId),
                utilisateurRepository.findByRole(Role.PRESIDENT).stream().map(Utilisateur::getId)
        ).distinct().collect(Collectors.toList());
        if (cibles.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("demandeCorrectionId", demande.getId());
        payload.put("reclamationId", reclamationId);
        payload.put("numero", demande.getNumero());
        String msg = "Nouvelle réclamation sur la demande de correction " + demande.getNumero();
        notificationService.notifyUsers(cibles, NotificationType.CORRECTION_DECISION,
                "ReclamationDemandeCorrection", reclamationId, msg, payload);
    }

    private void notifyAuteursReclamationTraitee(ReclamationDemandeCorrection rec, Long demandeId, boolean acceptee) {
        DemandeCorrection d = demandeCorrectionRepository.findById(demandeId).orElse(null);
        if (d == null) {
            return;
        }
        List<Long> ids = Stream.concat(
                utilisateurRepository.findByAutoriteContractanteId(d.getAutoriteContractante().getId()).stream().map(Utilisateur::getId),
                utilisateurRepository.findByEntrepriseId(d.getEntreprise().getId()).stream().map(Utilisateur::getId)
        ).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("demandeCorrectionId", demandeId);
        payload.put("reclamationId", rec.getId());
        payload.put("acceptee", acceptee);
        if (acceptee) {
            payload.put("nouveauStatutDemande", StatutDemande.RECUE.name());
        } else if (d.getStatut() != null) {
            payload.put("statutDemandeInchange", d.getStatut().name());
        }
        String msg = acceptee
                ? "Réclamation acceptée (DGTCP) — dossier " + d.getNumero() + " renvoyé au statut REÇUE ; nouvelle lettre d'adoption et offre corrigée à déposer"
                : "Réclamation rejetée — dossier " + d.getNumero() + " (statut de la demande inchangé)";
        notificationService.notifyUsers(ids, NotificationType.CORRECTION_DECISION,
                "ReclamationDemandeCorrection", rec.getId(), msg, payload);
    }

    private ReclamationDemandeCorrectionDto toDto(ReclamationDemandeCorrection e) {
        return ReclamationDemandeCorrectionDto.builder()
                .id(e.getId())
                .demandeCorrectionId(e.getDemandeCorrection() != null ? e.getDemandeCorrection().getId() : null)
                .statut(e.getStatut())
                .texte(e.getTexte())
                .dateCreation(e.getDateCreation())
                .dateModification(e.getDateModification())
                .dateTraitement(e.getDateTraitement())
                .auteurUserId(e.getUtilisateur() != null ? e.getUtilisateur().getId() : null)
                .auteurNom(e.getUtilisateur() != null ? e.getUtilisateur().getNomComplet() : null)
                .traiteParUserId(e.getTraiteParUserId())
                .motifReponse(e.getMotifReponse())
                .pieceJointeChemin(e.getPieceJointeChemin())
                .pieceJointeNomFichier(e.getPieceJointeNomFichier())
                .pieceJointeTaille(e.getPieceJointeTaille())
                .pieceJointeDateUpload(e.getPieceJointeDateUpload())
                .reponseRejetChemin(e.getReponseRejetChemin())
                .reponseRejetNomFichier(e.getReponseRejetNomFichier())
                .reponseRejetTaille(e.getReponseRejetTaille())
                .reponseRejetDateUpload(e.getReponseRejetDateUpload())
                .build();
    }
}
