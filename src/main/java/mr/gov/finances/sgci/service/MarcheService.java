package mr.gov.finances.sgci.service;

import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Convention;
import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.entity.DocumentMarche;
import mr.gov.finances.sgci.domain.entity.Marche;
import mr.gov.finances.sgci.domain.entity.MarcheDelegue;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutMarche;
import mr.gov.finances.sgci.domain.enums.TypeDocumentMarche;
import mr.gov.finances.sgci.repository.ConventionRepository;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.DocumentMarcheRepository;
import mr.gov.finances.sgci.repository.MarcheDelegueRepository;
import mr.gov.finances.sgci.repository.MarcheRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.CreateMarcheRequest;
import mr.gov.finances.sgci.web.dto.DocumentMarcheDto;
import mr.gov.finances.sgci.web.dto.MarcheDto;
import mr.gov.finances.sgci.web.dto.UpdateMarcheRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.time.Instant;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarcheService {

    private final MarcheRepository marcheRepository;
    private final MarcheDelegueRepository marcheDelegueRepository;
    private final DemandeCorrectionRepository demandeRepository;
    private final ConventionRepository conventionRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentMarcheRepository documentMarcheRepository;
    private final MinioService minioService;

    @Transactional(readOnly = true)
    public MarcheDto findById(Long id, AuthenticatedUser user) {
        return marcheRepository.findById(id)
                .filter(m -> canAccessMarche(m, user))
                .map(this::toDto)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Marché non trouvé: " + id));
    }

    @Transactional(readOnly = true)
    public MarcheDto findByDemandeCorrection(Long demandeCorrectionId, AuthenticatedUser user) {
        return marcheRepository.findByDemandeCorrectionId(demandeCorrectionId)
                .filter(m -> canAccessMarche(m, user))
                .map(this::toDto)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Marché non trouvé pour la correction: " + demandeCorrectionId));
    }

    @Transactional(readOnly = true)
    public List<MarcheDto> findAll(AuthenticatedUser user) {
        if (user == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }

        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        if (u.getAutoriteContractante() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
        }

        if (u.getRole() == Role.AUTORITE_CONTRACTANTE) {
            return marcheRepository.findAllByConventionAutoriteContractanteId(u.getAutoriteContractante().getId())
                    .stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }

        if (u.getRole() == Role.AUTORITE_UPM || u.getRole() == Role.AUTORITE_UEP) {
            return marcheRepository.findAllByConventionAutoriteContractanteIdAndDelegueId(
                            u.getAutoriteContractante().getId(), u.getId())
                    .stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }

        return marcheRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Recherche par numéro ou intitulé (périmètre AC pour AC/délégués ; sinon filtré par {@link #canAccessMarche}).
     */
    @Transactional(readOnly = true)
    public List<MarcheDto> searchMarches(String q, AuthenticatedUser user) {
        if (user == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        if (q == null || q.isBlank()) {
            return findAll(user);
        }
        String trimmed = q.trim();
        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        List<Marche> candidates;
        if ((u.getRole() == Role.AUTORITE_CONTRACTANTE || u.getRole() == Role.AUTORITE_UPM || u.getRole() == Role.AUTORITE_UEP)
                && u.getAutoriteContractante() != null) {
            candidates = marcheRepository.searchByAcAndNumeroOrIntitule(u.getAutoriteContractante().getId(), trimmed);
        } else {
            candidates = marcheRepository.searchByNumeroOrIntitule(trimmed);
        }

        return candidates.stream()
                .filter(m -> canAccessMarche(m, user))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MarcheDto> findMarchesForDelegue(Long delegueId, AuthenticatedUser user) {
        Utilisateur acPrincipal = requireAutoriteContractantePrincipal(user);
        Utilisateur delegue = utilisateurRepository.findById(delegueId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Délégué non trouvé: " + delegueId));
        assertDelegueForAutoriteContractante(delegue, acPrincipal);
        Long acId = acPrincipal.getAutoriteContractante().getId();
        return marcheRepository.findAllByDelegueIdAndAutoriteContractanteId(delegueId, acId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Remplace l'ensemble des rattachements du délégué aux marchés de l'AC par la liste fournie.
     */
    @Transactional
    public List<MarcheDto> syncDelegueMarches(Long delegueId, List<Long> marcheIds, AuthenticatedUser user) {
        Set<Long> desired = marcheIds == null ? Set.of() : marcheIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> initialIds = findMarchesForDelegue(delegueId, user).stream()
                .map(MarcheDto::getId)
                .collect(Collectors.toSet());
        for (Long mid : new ArrayList<>(initialIds)) {
            if (!desired.contains(mid)) {
                removeDelegue(mid, delegueId, user);
            }
        }
        for (Long mid : desired) {
            if (!initialIds.contains(mid)) {
                addDelegue(mid, delegueId, user);
            }
        }
        return findMarchesForDelegue(delegueId, user);
    }

    private Utilisateur requireAutoriteContractantePrincipal(AuthenticatedUser user) {
        if (user == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Utilisateur ac = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));
        if (ac.getRole() != Role.AUTORITE_CONTRACTANTE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Action réservée à l'autorité contractante");
        }
        if (ac.getAutoriteContractante() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
        }
        return ac;
    }

    private void assertDelegueForAutoriteContractante(Utilisateur delegue, Utilisateur acPrincipal) {
        if (delegue.getAutoriteContractante() == null
                || !delegue.getAutoriteContractante().getId().equals(acPrincipal.getAutoriteContractante().getId())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le délégué n'appartient pas à votre autorité contractante");
        }
        if (delegue.getRole() != Role.AUTORITE_UPM && delegue.getRole() != Role.AUTORITE_UEP) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "L'utilisateur ciblé n'est pas un délégué");
        }
    }

    @Transactional
    public MarcheDto assignDelegue(Long marcheId, Long delegueId, AuthenticatedUser user) {
        if (user == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Utilisateur ac = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        if (ac.getRole() != Role.AUTORITE_CONTRACTANTE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Action réservée à l'autorité contractante");
        }
        if (ac.getAutoriteContractante() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
        }

        Marche marche = marcheRepository.findById(marcheId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Marché non trouvé: " + marcheId));

        Long marcheAcId = marche.getConvention() != null && marche.getConvention().getAutoriteContractante() != null
                ? marche.getConvention().getAutoriteContractante().getId()
                : null;

        if (marcheAcId == null || !marcheAcId.equals(ac.getAutoriteContractante().getId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: marché hors périmètre de votre autorité contractante",
                    Map.of("marcheId", marcheId, "code", "MARCHE_HORS_PERIMETRE_AC"));
        }

        Utilisateur delegue = null;
        if (delegueId != null) {
            delegue = utilisateurRepository.findById(delegueId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Délégué non trouvé: " + delegueId));
            if (delegue.getAutoriteContractante() == null
                    || !delegue.getAutoriteContractante().getId().equals(ac.getAutoriteContractante().getId())) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le délégué n'appartient pas à votre autorité contractante");
            }
            if (delegue.getRole() != Role.AUTORITE_UPM && delegue.getRole() != Role.AUTORITE_UEP) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le rôle du délégué est invalide");
            }
        }

        if (delegue == null) {
            clearDelegues(marche);
            marche = marcheRepository.save(marche);
            return toDto(marche);
        }

        addDelegue(marche, delegue);
        marche = marcheRepository.save(marche);
        return toDto(marche);
    }

    @Transactional
    public DocumentMarcheDto uploadDocument(Long marcheId, TypeDocumentMarche type, MultipartFile file) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        Marche marche = marcheRepository.findById(marcheId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Marché non trouvé: " + marcheId));
        assertMarcheEditableForDocuments(marche);

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);

        DocumentMarche doc = DocumentMarche.builder()
                .type(type)
                .nomFichier(originalFilename != null ? originalFilename : file.getName())
                .chemin(fileUrl)
                .dateUpload(Instant.now())
                .taille(file.getSize())
                .marche(marche)
                .build();
        doc = documentMarcheRepository.save(doc);
        return toDto(doc);
    }

    @Transactional
    public DocumentMarcheDto replaceDocument(Long marcheId, Long documentId, MultipartFile file) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est vide");
        }
        Marche marche = marcheRepository.findById(marcheId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Marché non trouvé: " + marcheId));
        assertMarcheEditableForDocuments(marche);
        DocumentMarche doc = documentMarcheRepository.findByIdAndMarcheId(documentId, marcheId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document marché non trouvé: " + documentId));

        String originalFilename = file.getOriginalFilename();
        String fileUrl = minioService.uploadFile(file);
        doc.setNomFichier(originalFilename != null ? originalFilename : file.getName());
        doc.setChemin(fileUrl);
        doc.setDateUpload(Instant.now());
        doc.setTaille(file.getSize());
        doc = documentMarcheRepository.save(doc);
        return toDto(doc);
    }

    @Transactional
    public void deleteDocument(Long marcheId, Long documentId) {
        Marche marche = marcheRepository.findById(marcheId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Marché non trouvé: " + marcheId));
        assertMarcheEditableForDocuments(marche);
        DocumentMarche doc = documentMarcheRepository.findByIdAndMarcheId(documentId, marcheId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Document marché non trouvé: " + documentId));
        documentMarcheRepository.delete(doc);
    }

    @Transactional(readOnly = true)
    public List<DocumentMarcheDto> findDocuments(Long marcheId) {
        return documentMarcheRepository.findByMarcheId(marcheId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public MarcheDto addDelegue(Long marcheId, Long delegueId, AuthenticatedUser user) {
        if (user == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Utilisateur ac = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        if (ac.getRole() != Role.AUTORITE_CONTRACTANTE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Action réservée à l'autorité contractante");
        }
        if (ac.getAutoriteContractante() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
        }

        Marche marche = marcheRepository.findById(marcheId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Marché non trouvé: " + marcheId));

        Long marcheAcId = marche.getConvention() != null && marche.getConvention().getAutoriteContractante() != null
                ? marche.getConvention().getAutoriteContractante().getId()
                : null;

        if (marcheAcId == null || !marcheAcId.equals(ac.getAutoriteContractante().getId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: marché hors périmètre de votre autorité contractante");
        }

        Utilisateur delegue = utilisateurRepository.findById(delegueId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Délégué non trouvé: " + delegueId));
        if (delegue.getAutoriteContractante() == null
                || !delegue.getAutoriteContractante().getId().equals(ac.getAutoriteContractante().getId())) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le délégué n'appartient pas à votre autorité contractante");
        }
        if (delegue.getRole() != Role.AUTORITE_UPM && delegue.getRole() != Role.AUTORITE_UEP) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le rôle du délégué est invalide");
        }

        addDelegue(marche, delegue);
        marche = marcheRepository.save(marche);
        return toDto(marche);
    }

    @Transactional
    public MarcheDto removeDelegue(Long marcheId, Long delegueId, AuthenticatedUser user) {
        if (user == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }
        Utilisateur ac = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        if (ac.getRole() != Role.AUTORITE_CONTRACTANTE) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN, "Action réservée à l'autorité contractante");
        }
        if (ac.getAutoriteContractante() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
        }

        Marche marche = marcheRepository.findById(marcheId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Marché non trouvé: " + marcheId));

        Long marcheAcId = marche.getConvention() != null && marche.getConvention().getAutoriteContractante() != null
                ? marche.getConvention().getAutoriteContractante().getId()
                : null;

        if (marcheAcId == null || !marcheAcId.equals(ac.getAutoriteContractante().getId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: marché hors périmètre de votre autorité contractante");
        }

        MarcheDelegue link = marcheDelegueRepository.findByMarcheIdAndDelegueId(marcheId, delegueId)
                .orElseThrow(() -> ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Délégué non affecté à ce marché"));
        marche.getDelegues().removeIf(md -> Objects.equals(md.getId(), link.getId()));
        marche = marcheRepository.save(marche);
        return toDto(marche);
    }

    private void addDelegue(Marche marche, Utilisateur delegue) {
        if (marche == null || marche.getId() == null || delegue == null || delegue.getId() == null) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Données invalides pour l'affectation du délégué");
        }
        boolean exists = marcheDelegueRepository.existsByMarcheIdAndDelegueId(marche.getId(), delegue.getId());
        if (exists) {
            return;
        }
        MarcheDelegue link = MarcheDelegue.builder()
                .marche(marche)
                .delegue(delegue)
                .build();
        marche.getDelegues().add(link);
    }

    private void clearDelegues(Marche marche) {
        if (marche == null) {
            return;
        }
        marche.getDelegues().clear();
    }

    @Transactional
    public MarcheDto create(CreateMarcheRequest request, AuthenticatedUser user) {
        if (user == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Utilisateur non authentifié");
        }

        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé"));

        Convention convention = conventionRepository.findById(request.getConventionId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Convention non trouvée: " + request.getConventionId()));

        DemandeCorrection demande = null;
        if (request.getDemandeCorrectionId() != null) {
            demande = demandeRepository.findById(request.getDemandeCorrectionId())
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Demande de correction non trouvée: " + request.getDemandeCorrectionId()));

            if (demande.getConvention() == null || demande.getConvention().getId() == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La correction n'est rattachée à aucune convention");
            }
            if (!demande.getConvention().getId().equals(convention.getId())) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "La correction n'appartient pas à la convention sélectionnée");
            }

            if (marcheRepository.findByDemandeCorrectionId(request.getDemandeCorrectionId()).isPresent()) {
                throw ApiException.conflict(ApiErrorCode.CONFLICT, "Un marché existe déjà pour cette correction",
                        Map.of("demandeCorrectionId", request.getDemandeCorrectionId(), "code", "MARCHE_DEJA_LIE_CORRECTION"));
            }
        }

        if (u.getRole() == Role.AUTORITE_CONTRACTANTE || u.getRole() == Role.AUTORITE_UPM || u.getRole() == Role.AUTORITE_UEP) {
            if (u.getAutoriteContractante() == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Aucune autorité contractante liée à l'utilisateur");
            }
            Long conventionAcId = convention.getAutoriteContractante() != null ? convention.getAutoriteContractante().getId() : null;
            if (conventionAcId == null || !conventionAcId.equals(u.getAutoriteContractante().getId())) {
                throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED, "Accès refusé: convention hors périmètre de votre autorité contractante");
            }
        }

        Marche marche = Marche.builder()
                .numeroMarche(request.getNumeroMarche())
                .intitule(request.getIntitule())
                .dateSignature(request.getDateSignature())
                .montantContratHt(request.getMontantContratHt())
                .statut(request.getStatut())
                .convention(convention)
                .demandeCorrection(demande)
                .build();

        if (u.getRole() == Role.AUTORITE_UPM || u.getRole() == Role.AUTORITE_UEP) {
            MarcheDelegue link = MarcheDelegue.builder()
                    .marche(marche)
                    .delegue(u)
                    .build();
            marche.getDelegues().add(link);
        }
        marche = marcheRepository.save(marche);
        return toDto(marche);
    }

    @Transactional
    public MarcheDto update(Long id, UpdateMarcheRequest request) {
        Marche marche = marcheRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Marché non trouvé: " + id));
        if (marche.getStatut() == StatutMarche.CLOTURE && request.getStatut() != StatutMarche.CLOTURE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Marché clôturé: changement de statut interdit");
        }
        if (marche.getStatut() == StatutMarche.ANNULE && request.getStatut() != StatutMarche.ANNULE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Marché annulé: changement de statut interdit");
        }
        marche.setNumeroMarche(request.getNumeroMarche());
        marche.setIntitule(request.getIntitule());
        marche.setDateSignature(request.getDateSignature());
        marche.setMontantContratHt(request.getMontantContratHt());
        marche.setStatut(request.getStatut());
        marche = marcheRepository.save(marche);
        return toDto(marche);
    }

    private MarcheDto toDto(Marche marche) {
        return MarcheDto.builder()
                .id(marche.getId())
                .conventionId(marche.getConvention() != null ? marche.getConvention().getId() : null)
                .demandeCorrectionId(marche.getDemandeCorrection() != null ? marche.getDemandeCorrection().getId() : null)
                .numeroMarche(marche.getNumeroMarche())
                .intitule(marche.getIntitule())
                .dateSignature(marche.getDateSignature())
                .montantContratHt(marche.getMontantContratHt())
                .statut(marche.getStatut())
                .delegueIds(marche.getDelegues() != null
                        ? marche.getDelegues().stream()
                        .map(md -> md.getDelegue() != null ? md.getDelegue().getId() : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList())
                        : List.of())
                .build();
    }

    private boolean canAccessMarche(Marche marche, AuthenticatedUser user) {
        if (user == null) {
            return false;
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId()).orElse(null);
        if (u == null) {
            return false;
        }

        if (u.getRole() == Role.AUTORITE_UPM || u.getRole() == Role.AUTORITE_UEP) {
            return marche.getDelegues() != null && marche.getDelegues().stream()
                    .anyMatch(md -> md.getDelegue() != null && Objects.equals(md.getDelegue().getId(), u.getId()));
        }

        if (u.getRole() == Role.AUTORITE_CONTRACTANTE) {
            if (u.getAutoriteContractante() == null) {
                return false;
            }

            if (marche.getConvention() == null || marche.getConvention().getAutoriteContractante() == null) {
                return false;
            }
            return marche.getConvention().getAutoriteContractante().getId().equals(u.getAutoriteContractante().getId());
        }

        return true;
    }

    private DocumentMarcheDto toDto(DocumentMarche d) {
        return DocumentMarcheDto.builder()
                .id(d.getId())
                .type(d.getType())
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .build();
    }

    private void assertMarcheEditableForDocuments(Marche marche) {
        if (marche.getStatut() == StatutMarche.CLOTURE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Modification des documents interdite: marché clôturé");
        }
        if (marche.getStatut() == StatutMarche.ANNULE) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Modification des documents interdite: marché annulé");
        }
    }
}
