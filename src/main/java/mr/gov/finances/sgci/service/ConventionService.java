package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.AutoriteContractante;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DocumentConvention;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.NotificationType;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.TypeDocumentConvention;
import mr.gov.finances.sgci.repository.AutoriteContractanteRepository;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DocumentConventionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.ConventionDto;
import mr.gov.finances.sgci.web.dto.CreateConventionRequest;
import mr.gov.finances.sgci.web.dto.DocumentConventionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConventionService {

    private final ConventionRepository conventionRepository;
    private final AutoriteContractanteRepository autoriteRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentConventionRepository documentConventionRepository;
    private final MinioService minioService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ConventionDto> findAll(AuthenticatedUser user) {
        List<Convention> list = resolveConventionList(user, null);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ConventionDto findById(Long id, AuthenticatedUser user) {
        Convention c = conventionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + id));
        if (!canAccessConvention(c.getId(), user)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: convention hors périmètre");
        }
        return toDto(c);
    }

    @Transactional(readOnly = true)
    public List<ConventionDto> findByStatut(StatutConvention statut, AuthenticatedUser user) {
        List<Convention> list = resolveConventionList(user, statut);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    private List<Convention> resolveConventionList(AuthenticatedUser user, StatutConvention statut) {
        if (user == null || user.getUserId() == null) {
            if (statut == null) {
                return conventionRepository.findAll();
            }
            return conventionRepository.findByStatut(statut);
        }

        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        Role role = u.getRole();

        // Référentiel partagé : conventions (et entreprises côté API dédiée) visibles par toutes les AC / délégués.
        if (role == Role.AUTORITE_CONTRACTANTE
                || role == Role.AUTORITE_UPM
                || role == Role.AUTORITE_UEP) {
            if (u.getAutoriteContractante() == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
            }
            if (statut == null) {
                return conventionRepository.findAll();
            }
            return conventionRepository.findByStatut(statut);
        }

        if (statut == null) {
            return conventionRepository.findAll();
        }
        return conventionRepository.findByStatut(statut);
    }

    private boolean canAccessConvention(Long conventionId, AuthenticatedUser user) {
        if (conventionId == null) {
            return false;
        }
        if (user == null || user.getUserId() == null) {
            return true;
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId()).orElse(null);
        if (u == null || u.getRole() == null) {
            return false;
        }
        if (u.getRole() == Role.AUTORITE_CONTRACTANTE
                || u.getRole() == Role.AUTORITE_UPM
                || u.getRole() == Role.AUTORITE_UEP) {
            return u.getAutoriteContractante() != null && conventionRepository.findById(conventionId).isPresent();
        }
        return true;
    }

    @Transactional
    public ConventionDto create(CreateConventionRequest request, Long userId) {
        if (conventionRepository.findByReference(request.getReference()).isPresent()) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Référence de convention déjà utilisée");
        }
        AutoriteContractante autorite = resolveAutorite(request.getAutoriteContractanteId(), userId);
        Convention convention = Convention.builder()
                .reference(request.getReference())
                .intitule(request.getIntitule())
                .bailleur(request.getBailleur())
                .bailleurDetails(request.getBailleurDetails())
                .dateSignature(request.getDateSignature())
                .dateFin(request.getDateFin())
                .montantDevise(request.getMontantDevise())
                .montantMru(request.getMontantMru())
                .deviseOrigine(request.getDeviseOrigine())
                .tauxChange(request.getTauxChange())
                .statut(StatutConvention.EN_ATTENTE)
                .autoriteContractante(autorite)
                .build();
        convention = conventionRepository.save(convention);
        ConventionDto result = toDto(convention);
        auditService.log(AuditAction.CREATE, "Convention", String.valueOf(convention.getId()), result);
        return result;
    }

    @Transactional
    public DocumentConventionDto uploadDocument(Long conventionId, TypeDocumentConvention type, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        Convention convention = conventionRepository.findById(conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + conventionId));
        assertConventionEditable(convention);
        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentConvention doc = DocumentConvention.builder()
                .type(type)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .convention(convention)
                .build();
        doc = documentConventionRepository.save(doc);
        DocumentConventionDto result = toDto(doc);
        auditService.log(AuditAction.CREATE, "DocumentConvention", String.valueOf(doc.getId()), result);
        return result;
    }

    @Transactional
    public DocumentConventionDto replaceDocument(Long conventionId, Long documentId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        Convention convention = conventionRepository.findById(conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + conventionId));
        assertConventionEditable(convention);
        DocumentConvention doc = documentConventionRepository.findByIdAndConventionId(documentId, conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document convention non trouvé: " + documentId));

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);
        doc.setNomFichier(originalFilename != null ? originalFilename : file.getName());
        doc.setChemin(fileUrl);
        doc.setDateUpload(Instant.now());
        doc.setTaille(file.getSize());
        doc = documentConventionRepository.save(doc);
        DocumentConventionDto result = toDto(doc);
        auditService.log(AuditAction.UPDATE, "DocumentConvention", String.valueOf(doc.getId()), result);
        return result;
    }

    @Transactional
    public void deleteDocument(Long conventionId, Long documentId) {
        Convention convention = conventionRepository.findById(conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + conventionId));
        assertConventionEditable(convention);
        DocumentConvention doc = documentConventionRepository.findByIdAndConventionId(documentId, conventionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document convention non trouvé: " + documentId));
        documentConventionRepository.delete(doc);
        auditService.log(AuditAction.DELETE, "DocumentConvention", String.valueOf(documentId), null);
    }

    @Transactional(readOnly = true)
    public List<DocumentConventionDto> findDocuments(Long conventionId) {
        return documentConventionRepository.findByConventionId(conventionId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ConventionDto updateStatut(Long id, StatutConvention statut, Long userId, String motifRejet) {
        Convention convention = conventionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + id));
        if (statut == StatutConvention.ANNULEE && convention.getStatut() == StatutConvention.VALIDE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Annulation impossible: la convention est déjà validée");
        }
        convention.setStatut(statut);
        if (statut == StatutConvention.VALIDE || statut == StatutConvention.REJETE || statut == StatutConvention.ANNULEE) {
            convention.setValideParUserId(userId);
            convention.setDateValidation(java.time.Instant.now());
            convention.setMotifRejet((statut == StatutConvention.REJETE || statut == StatutConvention.ANNULEE) ? motifRejet : null);
        }
        convention = conventionRepository.save(convention);
        ConventionDto result = toDto(convention);
        auditService.log(AuditAction.UPDATE, "Convention", String.valueOf(id), result);
        notifyConvention(convention, statut, motifRejet, userId);
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

    private ConventionDto toDto(Convention convention) {
        return ConventionDto.builder()
                .id(convention.getId())
                .reference(convention.getReference())
                .intitule(convention.getIntitule())
                .bailleur(convention.getBailleur())
                .bailleurDetails(convention.getBailleurDetails())
                .dateSignature(convention.getDateSignature())
                .dateFin(convention.getDateFin())
                .montantDevise(convention.getMontantDevise())
                .montantMru(convention.getMontantMru())
                .deviseOrigine(convention.getDeviseOrigine())
                .tauxChange(convention.getTauxChange())
                .statut(convention.getStatut())
                .autoriteContractanteId(convention.getAutoriteContractante() != null ? convention.getAutoriteContractante().getId() : null)
                .autoriteContractanteNom(convention.getAutoriteContractante() != null ? convention.getAutoriteContractante().getNom() : null)
                .dateCreation(convention.getDateCreation())
                .valideParUserId(convention.getValideParUserId())
                .dateValidation(convention.getDateValidation())
                .motifRejet(convention.getMotifRejet())
                .documents(convention.getDocuments() != null
                        ? convention.getDocuments().stream().map(this::toDto).collect(Collectors.toList())
                        : List.of())
                .build();
    }

    private DocumentConventionDto toDto(DocumentConvention doc) {
        return DocumentConventionDto.builder()
                .id(doc.getId())
                .type(doc.getType())
                .nomFichier(doc.getNomFichier())
                .chemin(doc.getChemin())
                .dateUpload(doc.getDateUpload())
                .taille(doc.getTaille())
                .build();
    }

    private void notifyConvention(Convention convention, StatutConvention statut, String motifRejet, Long userId) {
        if (convention == null || convention.getAutoriteContractante() == null) {
            return;
        }
        List<Long> userIds = utilisateurRepository.findByAutoriteContractanteId(convention.getAutoriteContractante().getId())
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
        String message = "Convention " + convention.getReference() + " statut: " + statut;
        notificationService.notifyUsers(userIds, NotificationType.CONVENTION_STATUT_CHANGE,
                "Convention", convention.getId(), message, payload);
    }

    private void assertConventionEditable(Convention convention) {
        if (convention == null) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée");
        }
        if (convention.getStatut() == StatutConvention.VALIDE) {
            throw ApiException.conflict(ApiErrorCode.CONFLICT, "Modification des documents interdite: convention déjà validée");
        }
        if (convention.getStatut() == StatutConvention.ANNULEE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Modification des documents interdite: convention annulée");
        }
    }
}
