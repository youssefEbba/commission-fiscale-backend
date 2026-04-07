package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DocumentProjet;
import mr.gov.finances.sgci.domain.entity.ReferentielProjet;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.StatutReferentielProjet;
import mr.gov.finances.sgci.domain.enums.TypeDocumentProjet;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DocumentProjetRepository;
import mr.gov.finances.sgci.repository.ReferentielProjetRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.web.dto.CreateReferentielProjetRequest;
import mr.gov.finances.sgci.web.dto.DocumentProjetDto;
import mr.gov.finances.sgci.web.dto.ReferentielProjetDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ReferentielProjetService {

    private final ReferentielProjetRepository referentielRepository;
    private final AutoriteContractanteRepository autoriteRepository;
    private final ConventionRepository conventionRepository;
    private final DocumentProjetRepository documentProjetRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ReferentielProjetDto> findAll() {
        return referentielRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReferentielProjetDto findById(Long id) {
        return referentielRepository.findById(id).map(this::toDto)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Référentiel projet non trouvé: " + id));
    }

    @Transactional(readOnly = true)
    public List<ReferentielProjetDto> findByAutoriteContractante(Long autoriteId) {
        return referentielRepository.findByAutoriteContractanteId(autoriteId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReferentielProjetDto> findByStatut(StatutReferentielProjet statut) {
        return referentielRepository.findByStatut(statut).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReferentielProjetDto create(CreateReferentielProjetRequest request, Long userId) {
        AutoriteContractante autorite = resolveAutorite(request.getAutoriteContractanteId(), userId);
        Convention convention = resolveConvention(request.getConventionId());
        ReferentielProjet entity = ReferentielProjet.builder()
                .numero(generateNumero())
                .nomProjet(request.getNomProjet())
                .administrateurProjet(request.getAdministrateurProjet())
                .referenceBciSecteur(request.getReferenceBciSecteur())
                .autoriteContractante(autorite)
                .convention(convention)
                .statut(StatutReferentielProjet.EN_ATTENTE)
                .build();
        entity = referentielRepository.save(entity);
        ReferentielProjetDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "ReferentielProjet", String.valueOf(entity.getId()), result);
        return result;
    }

    private AutoriteContractante resolveAutorite(Long autoriteContractanteId, Long userId) {
        if (autoriteContractanteId != null) {
            return autoriteRepository.findById(autoriteContractanteId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Autorité contractante non trouvée"));
        }
        if (userId == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Utilisateur user = utilisateurRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        if (user.getAutoriteContractante() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
        }
        return user.getAutoriteContractante();
    }

    private Convention resolveConvention(Long conventionId) {
        Convention convention = conventionRepository.findById(conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée"));
        if (convention.getStatut() != StatutConvention.VALIDE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La convention doit être validée par DGB");
        }
        return convention;
    }

    @Transactional
    public ReferentielProjetDto updateStatut(Long id, StatutReferentielProjet statut, Long userId, String motifRejet) {
        ReferentielProjet entity = referentielRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Référentiel projet non trouvé: " + id));
        if (statut == StatutReferentielProjet.ANNULE && entity.getStatut() == StatutReferentielProjet.VALIDE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Annulation impossible: le référentiel projet est déjà validé");
        }
        entity.setStatut(statut);
        if (statut == StatutReferentielProjet.VALIDE || statut == StatutReferentielProjet.REJETE || statut == StatutReferentielProjet.ANNULE) {
            entity.setValideParUserId(userId);
            entity.setDateValidation(Instant.now());
            entity.setMotifRejet((statut == StatutReferentielProjet.REJETE || statut == StatutReferentielProjet.ANNULE) ? motifRejet : null);
        }
        entity = referentielRepository.save(entity);
        ReferentielProjetDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "ReferentielProjet", String.valueOf(id), result);
        notifyReferentielProjet(entity, statut, motifRejet, userId);
        return result;
    }

    @Transactional
    public DocumentProjetDto uploadDocument(Long referentielProjetId, TypeDocumentProjet type, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        ReferentielProjet referentiel = referentielRepository.findById(referentielProjetId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Référentiel projet non trouvé: " + referentielProjetId));
        assertReferentielEditable(referentiel);
        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentProjet doc = DocumentProjet.builder()
                .type(type)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .referentielProjet(referentiel)
                .build();
        doc = documentProjetRepository.save(doc);
        DocumentProjetDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "DocumentProjet", String.valueOf(doc.getId()), result);
        return result;
    }

    @Transactional
    public DocumentProjetDto replaceDocument(Long referentielProjetId, Long documentId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        ReferentielProjet referentiel = referentielRepository.findById(referentielProjetId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Référentiel projet non trouvé: " + referentielProjetId));
        assertReferentielEditable(referentiel);
        DocumentProjet doc = documentProjetRepository.findByIdAndReferentielProjetId(documentId, referentielProjetId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document projet non trouvé: " + documentId));
        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);
        doc.setNomFichier(originalFilename != null ? originalFilename : file.getName());
        doc.setChemin(fileUrl);
        doc.setDateUpload(Instant.now());
        doc.setTaille(file.getSize());
        doc = documentProjetRepository.save(doc);
        DocumentProjetDto result = toDto(doc);
        auditService.log(AuditAction.UPDATE, "DocumentProjet", String.valueOf(doc.getId()), result);
        return result;
    }

    @Transactional
    public void deleteDocument(Long referentielProjetId, Long documentId) {
        ReferentielProjet referentiel = referentielRepository.findById(referentielProjetId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Référentiel projet non trouvé: " + referentielProjetId));
        assertReferentielEditable(referentiel);
        DocumentProjet doc = documentProjetRepository.findByIdAndReferentielProjetId(documentId, referentielProjetId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document projet non trouvé: " + documentId));
        documentProjetRepository.delete(doc);
        auditService.log(AuditAction.DELETE, "DocumentProjet", String.valueOf(documentId), null);
    }

    @Transactional(readOnly = true)
    public List<DocumentProjetDto> findDocuments(Long referentielProjetId) {
        return documentProjetRepository.findByReferentielProjetId(referentielProjetId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private String generateNumero() {
        return "RP-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private ReferentielProjetDto toDto(ReferentielProjet entity) {
        return ReferentielProjetDto.builder()
                .id(entity.getId())
                .numero(entity.getNumero())
                .nomProjet(entity.getNomProjet())
                .administrateurProjet(entity.getAdministrateurProjet())
                .referenceBciSecteur(entity.getReferenceBciSecteur())
                .dateDepot(entity.getDateDepot())
                .statut(entity.getStatut())
                .autoriteContractanteId(entity.getAutoriteContractante() != null ? entity.getAutoriteContractante().getId() : null)
                .autoriteContractanteNom(entity.getAutoriteContractante() != null ? entity.getAutoriteContractante().getNom() : null)
                .conventionId(entity.getConvention() != null ? entity.getConvention().getId() : null)
                .conventionReference(entity.getConvention() != null ? entity.getConvention().getReference() : null)
                .conventionIntitule(entity.getConvention() != null ? entity.getConvention().getIntitule() : null)
                .conventionBailleur(entity.getConvention() != null ? entity.getConvention().getBailleur() : null)
                .conventionBailleurDetails(entity.getConvention() != null ? entity.getConvention().getBailleurDetails() : null)
                .conventionDateSignature(entity.getConvention() != null ? entity.getConvention().getDateSignature() : null)
                .conventionDateFin(entity.getConvention() != null ? entity.getConvention().getDateFin() : null)
                .conventionMontantDevise(entity.getConvention() != null ? entity.getConvention().getMontantDevise() : null)
                .conventionMontantMru(entity.getConvention() != null ? entity.getConvention().getMontantMru() : null)
                .conventionDeviseOrigine(entity.getConvention() != null ? entity.getConvention().getDeviseOrigine() : null)
                .conventionTauxChange(entity.getConvention() != null ? entity.getConvention().getTauxChange() : null)
                .valideParUserId(entity.getValideParUserId())
                .dateValidation(entity.getDateValidation())
                .motifRejet(entity.getMotifRejet())
                .documents(entity.getDocuments() != null ? entity.getDocuments().stream().map(this::toDto).collect(Collectors.toList()) : List.of())
                .build();
    }

    private DocumentProjetDto toDto(DocumentProjet doc) {
        return DocumentProjetDto.builder()
                .id(doc.getId())
                .type(doc.getType())
                .nomFichier(doc.getNomFichier())
                .chemin(doc.getChemin())
                .dateUpload(doc.getDateUpload())
                .taille(doc.getTaille())
                .build();
    }

    private void notifyReferentielProjet(ReferentielProjet entity,
                                         StatutReferentielProjet statut,
                                         String motifRejet,
                                         Long userId) {
        if (entity == null || entity.getAutoriteContractante() == null) {
            return;
        }
        List<Long> userIds = utilisateurRepository.findByAutoriteContractanteId(entity.getAutoriteContractante().getId())
                .stream()
                .map(Utilisateur::getId)
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("statut", statut.name());
        if (motifRejet != null && !motifRejet.isBlank()) {
            payload.put("motifRejet", motifRejet);
        }
        if (userId != null) {
            payload.put("acteurUserId", userId);
        }
        String message = "Référentiel projet " + entity.getNumero() + " statut: " + statut;
        notificationService.notifyUsers(userIds, NotificationType.REFERENTIEL_STATUT_CHANGE,
                "ReferentielProjet", entity.getId(), message, payload);
    }

    private void assertReferentielEditable(ReferentielProjet referentiel) {
        if (referentiel.getStatut() == StatutReferentielProjet.VALIDE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Modification des documents interdite: référentiel projet déjà validé");
        }
        if (referentiel.getStatut() == StatutReferentielProjet.ANNULE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Modification des documents interdite: référentiel projet annulé");
        }
    }
}
