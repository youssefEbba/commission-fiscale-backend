package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.document.DocumentVisibilitePolicy;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.entity.DocumentUtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.entity.UtilisationTVAInterieure;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.ProcessusDocument;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.ModeApposition;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeDocument;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.DecisionUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.DocumentUtilisationCreditRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.DocumentUtilisationCreditDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentUtilisationCreditService {

    private final DocumentUtilisationCreditRepository repository;
    private final UtilisationCreditRepository utilisationRepository;
    private final DecisionUtilisationCreditRepository decisionRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final DocumentRequirementValidator requirementValidator;
    private final RejetTempResponseService rejetTempResponseService;

    @Transactional
    public DocumentUtilisationCreditDto upload(Long utilisationCreditId, String codeDocument, String message, MultipartFile file, AuthenticatedUser user) throws IOException {
        return upload(utilisationCreditId, codeDocument, message, file, null, user);
    }

    /**
     * Dépôt avec déclaration du mode d'apposition de la signature.
     *
     * <p>{@code modeApposition} nul est le cas courant : la pièce ne porte pas de signature, ou le
     * client ne le déclare pas. Le signataire n'est retenu que pour un dépôt du Président — c'est
     * sa signature qui est en jeu, pas celle de l'agent qui téléverse.
     */
    @Transactional
    public DocumentUtilisationCreditDto upload(Long utilisationCreditId, String codeDocument, String message, MultipartFile file,
                                ModeApposition modeApposition, AuthenticatedUser user) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        UtilisationCredit utilisation = utilisationRepository.findById(utilisationCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + utilisationCreditId));

        ProcessusDocument processus = resolveProcessus(utilisation);
        requirementValidator.validateUpload(processus, codeDocument, file);

        int nextVersion = 1;
        DocumentUtilisationCredit previous = repository.findByUtilisationCreditIdAndCodeDocumentAndActifTrue(utilisationCreditId, codeDocument)
                .orElse(null);
        if (previous != null && !isCertificatUtilisationReplacement(utilisation, codeDocument, user)) {
            assertReplacementAllowed(utilisation, codeDocument, user);
        }
        if (previous != null) {
            previous.setActif(false);
            nextVersion = previous.getVersion() != null ? previous.getVersion() + 1 : 1;
        }

        boolean askedByOpenRejetTemp = decisionRepository.findByUtilisationCreditIdAndDecisionAndRejetTempStatus(
                        utilisation.getId(),
                        DecisionCorrectionType.REJET_TEMP,
                        RejetTempStatus.OUVERT
                ).stream().anyMatch(d -> d.getDocumentsDemandes() != null && d.getDocumentsDemandes().contains(codeDocument));

        if (askedByOpenRejetTemp && (message == null || message.isBlank())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le message de réponse est obligatoire");
        }

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentUtilisationCredit doc = DocumentUtilisationCredit.builder()
                .codeDocument(codeDocument)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .modeApposition(modeApposition)
                .signataireUtilisateurId(modeApposition != null && user != null
                        && user.getRole() == Role.PRESIDENT ? user.getUserId() : null)
                .utilisationCredit(utilisation)
                .build();
        doc = repository.save(doc);
        DocumentUtilisationCreditDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "DocumentUtilisationCredit", String.valueOf(doc.getId()), result);

        if (askedByOpenRejetTemp) {
            rejetTempResponseService.recordUtilisationUploadResponse(utilisation.getId(), codeDocument, message, doc, user);
        }

        return result;
    }

    /**
     * Remplacement d'un document par un administrateur (ADMIN_SI), à tout moment quel que soit le
     * statut de l'utilisation. Motif obligatoire, journalisé dans l'audit sous
     * {@link AuditAction#ADMIN_CORRECTION}. L'ancienne version est désactivée (historique
     * conservé), pas supprimée.
     */
    @Transactional
    public DocumentUtilisationCreditDto adminReplace(Long utilisationCreditId, String codeDocument, String motif, MultipartFile file, AuthenticatedUser user) throws IOException {
        if (user == null || user.getRole() != Role.ADMIN_SI) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Correction administrateur réservée à l'administrateur système");
        }
        if (motif == null || motif.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le motif de la correction administrateur est obligatoire");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }

        UtilisationCredit utilisation = utilisationRepository.findById(utilisationCreditId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisation de crédit non trouvée: " + utilisationCreditId));

        DocumentUtilisationCredit previous = repository.findByUtilisationCreditIdAndCodeDocumentAndActifTrue(utilisationCreditId, codeDocument)
                .orElse(null);
        int nextVersion = 1;
        if (previous != null) {
            previous.setActif(false);
            repository.save(previous);
            nextVersion = previous.getVersion() != null ? previous.getVersion() + 1 : 1;
        }

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentUtilisationCredit doc = DocumentUtilisationCredit.builder()
                .codeDocument(codeDocument)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .version(nextVersion)
                .actif(true)
                .utilisationCredit(utilisation)
                .build();
        doc = repository.save(doc);
        DocumentUtilisationCreditDto result = toDto(doc);
        auditService.log(AuditAction.ADMIN_CORRECTION, "DocumentUtilisationCredit", String.valueOf(doc.getId()), result, motif);
        return result;
    }

    /**
     * Le Président — ou l'ADMIN_SI qui s'y substitue — peut re-déposer le certificat d'utilisation.
     *
     * <p>Sans cette échappatoire, {@link #assertReplacementAllowed} l'enfermerait : ce contrôle
     * réserve tout remplacement à l'ENTREPRISE, en statut INCOMPLETE, sur un rejet temporaire ouvert.
     * Le Président déposerait donc son certificat une fois et ne pourrait plus jamais corriger un
     * scan illisible ni une signature à refaire. Symétrique de
     * {@code DocumentService.isPresidentLettreAdoptionReplacement} pour la lettre d'adoption.
     */
    private static boolean isCertificatUtilisationReplacement(UtilisationCredit utilisation,
                                                              String codeDocument,
                                                              AuthenticatedUser user) {
        if (user == null || user.getRole() == null || utilisation == null) {
            return false;
        }
        if (user.getRole() != Role.PRESIDENT && user.getRole() != Role.ADMIN_SI) {
            return false;
        }
        if (!TypeDocument.CERTIFICAT_UTILISATION.name().equals(codeDocument)) {
            return false;
        }
        // Toute la portion du circuit postérieure à l'émission : un scan illisible peut n'être
        // remarqué qu'au Trésor ou à la DGI, bien après que le Président a déposé sa pièce.
        StatutUtilisation st = utilisation.getStatut();
        return st == StatutUtilisation.CERTIFICAT_EMIS
                || st == StatutUtilisation.ENVOYEE_AU_TRESOR
                || st == StatutUtilisation.QUITTANCES_ENREGISTREES
                || st == StatutUtilisation.QUITTANCE_DGI_ENREGISTREE
                || st == StatutUtilisation.LIQUIDEE
                || st == StatutUtilisation.APUREE;
    }

    private void assertReplacementAllowed(UtilisationCredit utilisation, String codeDocument, AuthenticatedUser user) {
        if (utilisation == null || utilisation.getId() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Utilisation invalide");
        }
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        // Le sous-traitant dépose et soumet, et la commission relais agit pour l'entreprise :
        // les exclure du remplacement était une incohérence, pas une règle.
        if (user.getRole() != Role.ENTREPRISE
                && user.getRole() != Role.SOUS_TRAITANT
                && user.getRole() != Role.COMMISSION_RELAIS) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Remplacement interdit: réservé au déposant");
        }
        if (utilisation.getStatut() != StatutUtilisation.INCOMPLETE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Remplacement interdit: l'utilisation n'est pas en statut INCOMPLETE");
        }
        // Tant qu'un rejet est ouvert, toute pièce est corrigeable. Restreindre aux seuls codes
        // demandés empêchait l'entreprise de corriger une erreur qu'elle est seule à avoir vue.
        boolean rejetOuvert = decisionRepository.findByUtilisationCreditId(utilisation.getId()).stream()
                .anyMatch(d -> d.getDecision() == DecisionCorrectionType.REJET_TEMP
                        && d.getRejetTempStatus() == RejetTempStatus.OUVERT);
        if (!rejetOuvert) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Remplacement interdit: aucun rejet temporaire n'est ouvert");
        }
    }

    private ProcessusDocument resolveProcessus(UtilisationCredit utilisation) {
        if (utilisation == null || utilisation.getType() == null) {
            return ProcessusDocument.UTILISATION_CI;
        }
        if (utilisation.getType() == TypeUtilisation.DOUANIER) {
            return ProcessusDocument.UTILISATION_CI_DOUANE;
        }
        if (utilisation.getType() == TypeUtilisation.TVA_INTERIEURE) {
            if (utilisation instanceof UtilisationTVAInterieure t && t.getTypeAchat() != null) {
                return ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE;
            }
            return ProcessusDocument.UTILISATION_CI_TVA_INTERIEURE;
        }
        return ProcessusDocument.UTILISATION_CI;
    }

    /**
     * Les documents d'un dossier, filtrés selon ce que l'appelant a le droit de voir.
     *
     * <p>La signature exige l'acteur à dessein : la version précédente n'en prenait pas, et c'est
     * exactement ce qui rendait le masquage impossible à poser ici.
     */
    @Transactional(readOnly = true)
    public List<DocumentUtilisationCreditDto> findByUtilisationCreditId(Long utilisationCreditId,
                                                                        AuthenticatedUser user) {
        StatutUtilisation statut = utilisationRepository.findById(utilisationCreditId)
                .map(UtilisationCredit::getStatut)
                .orElse(null);
        Role role = user != null ? user.getRole() : null;
        return repository.findByUtilisationCreditId(utilisationCreditId).stream()
                .filter(d -> DocumentVisibilitePolicy.visiblePour(d.getCodeDocument(), statut, role))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> findActiveDocumentTypes(Long utilisationCreditId) {
        return repository.findByUtilisationCreditIdAndActifTrue(utilisationCreditId)
                .stream()
                .map(DocumentUtilisationCredit::getCodeDocument)
                .distinct()
                .collect(Collectors.toList());
    }

    private DocumentUtilisationCreditDto toDto(DocumentUtilisationCredit d) {
        return DocumentUtilisationCreditDto.builder()
                .id(d.getId())
                .codeDocument(d.getCodeDocument())
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .version(d.getVersion())
                .actif(d.getActif())
                .modeApposition(d.getModeApposition())
                .signataireUtilisateurId(d.getSignataireUtilisateurId())
                .build();
    }
}
