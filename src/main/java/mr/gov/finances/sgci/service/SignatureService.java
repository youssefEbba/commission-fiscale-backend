package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Signature;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.AuditAction;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.SignatureRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.SignatureBase64Dto;
import mr.gov.finances.sgci.web.dto.SignatureDto;
import mr.gov.finances.sgci.web.dto.UpdateSignatureRequest;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SignatureService {

    private static final long MAX_SIZE_BYTES = 1_048_576L; // 1 Mo
    private static final int MAX_WIDTH_PX = 2000;
    private static final int MAX_HEIGHT_PX = 1000;
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final SignatureRepository repository;
    private final UtilisateurRepository utilisateurRepository;
    private final MinioService minioService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<SignatureDto> list(Role role, Long utilisateurId, Boolean activeOnly) {
        List<Signature> all;
        if (role != null && utilisateurId != null) {
            all = repository.findByRoleAndUtilisateur_Id(role, utilisateurId);
        } else if (role != null) {
            all = repository.findByRole(role);
        } else if (utilisateurId != null) {
            all = repository.findByUtilisateur_Id(utilisateurId);
        } else {
            all = repository.findAll();
        }
        return all.stream()
                .filter(s -> !Boolean.TRUE.equals(activeOnly) || Boolean.TRUE.equals(s.getActive()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SignatureDto getActive(Role role, Long utilisateurId) {
        Signature entity = (utilisateurId != null
                ? repository.findByRoleAndUtilisateur_IdAndActiveTrue(role, utilisateurId)
                : repository.findByRoleAndUtilisateurIsNullAndActiveTrue(role))
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND,
                        "Aucune signature active pour ce rôle" + (utilisateurId != null ? "/utilisateur" : "")));
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public byte[] getContent(Long id) {
        Signature entity = findEntity(id);
        return minioService.downloadFile(entity.getObjetMinio());
    }

    @Transactional(readOnly = true)
    public String getContentType(Long id) {
        return findEntity(id).getContentType();
    }

    @Transactional(readOnly = true)
    public SignatureBase64Dto getBase64(Long id) {
        Signature entity = findEntity(id);
        byte[] content = minioService.downloadFile(entity.getObjetMinio());
        String type = entity.getContentType() != null ? entity.getContentType() : "image/png";
        String encoded = Base64.getEncoder().encodeToString(content);
        return SignatureBase64Dto.builder()
                .dataUrl("data:" + type + ";base64," + encoded)
                .build();
    }

    @Transactional
    public SignatureDto create(MultipartFile file, Role role, Long utilisateurId, String nomAffiche,
                                Boolean activer, AuthenticatedUser user) {
        if (role == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Le rôle est obligatoire");
        }
        assertCanManage(role, utilisateurId, user);

        Utilisateur utilisateur = null;
        if (utilisateurId != null) {
            utilisateur = utilisateurRepository.findById(utilisateurId)
                    .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Utilisateur non trouvé: " + utilisateurId));
        }

        PngInfo info = validateAndInspectPng(file);
        boolean shouldActivate = !Boolean.FALSE.equals(activer);

        // Fail-fast : l'upload objet se fait AVANT toute écriture en base ; si MinIO échoue,
        // rien n'est persisté (MinioService lève une ApiException 503, la transaction ne démarre
        // aucune écriture DB avant ce point).
        String objectKey = minioService.uploadBytes(info.content, "image/png", safeFileName(file, "signature.png"));

        Signature entity = Signature.builder()
                .utilisateur(utilisateur)
                .role(role)
                .nomAffiche(nomAffiche)
                .objetMinio(objectKey)
                .contentType("image/png")
                .taille((long) info.content.length)
                .largeurPx(info.width)
                .hauteurPx(info.height)
                .checksumSha256(info.checksum)
                .active(shouldActivate)
                .version(1)
                .dateCreation(Instant.now())
                .creePar(usernameOf(user))
                .build();

        if (shouldActivate) {
            deactivateExistingActive(role, utilisateurId, null);
        }

        entity = repository.save(entity);
        SignatureDto result = toDto(entity);
        auditService.log(AuditAction.CREATE, "Signature", String.valueOf(entity.getId()), result);
        return result;
    }

    @Transactional
    public SignatureDto updateMetadata(Long id, UpdateSignatureRequest request, AuthenticatedUser user) {
        Signature entity = findEntity(id);
        assertCanManage(entity.getRole(), entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null, user);

        if (request.getNomAffiche() != null) {
            entity.setNomAffiche(request.getNomAffiche());
        }
        if (request.getActive() != null) {
            applyActiveFlag(entity, request.getActive());
        }
        entity = repository.save(entity);
        SignatureDto result = toDto(entity);
        auditService.log(AuditAction.UPDATE, "Signature", String.valueOf(id), result);
        return result;
    }

    @Transactional
    public SignatureDto remplacer(Long id, MultipartFile file, AuthenticatedUser user) {
        Signature previous = findEntity(id);
        Long utilisateurId = previous.getUtilisateur() != null ? previous.getUtilisateur().getId() : null;
        assertCanManage(previous.getRole(), utilisateurId, user);

        PngInfo info = validateAndInspectPng(file);

        // Fail-fast : upload objet avant toute écriture DB.
        String objectKey = minioService.uploadBytes(info.content, "image/png", safeFileName(file, "signature.png"));

        boolean previousWasActive = Boolean.TRUE.equals(previous.getActive());
        if (previousWasActive) {
            previous.setActive(false);
            previous.setDateDesactivation(Instant.now());
            repository.save(previous);
        }

        Signature next = Signature.builder()
                .utilisateur(previous.getUtilisateur())
                .role(previous.getRole())
                .nomAffiche(previous.getNomAffiche())
                .objetMinio(objectKey)
                .contentType("image/png")
                .taille((long) info.content.length)
                .largeurPx(info.width)
                .hauteurPx(info.height)
                .checksumSha256(info.checksum)
                .active(previousWasActive)
                .version(previous.getVersion() != null ? previous.getVersion() + 1 : 1)
                .dateCreation(Instant.now())
                .creePar(usernameOf(user))
                .build();

        if (previousWasActive) {
            deactivateExistingActive(previous.getRole(), utilisateurId, previous.getId());
        }

        next = repository.save(next);
        SignatureDto result = toDto(next);
        auditService.log(AuditAction.UPDATE, "Signature", String.valueOf(next.getId()), result);
        return result;
    }

    @Transactional
    public void deactivate(Long id, AuthenticatedUser user) {
        Signature entity = findEntity(id);
        assertCanManage(entity.getRole(), entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null, user);
        if (Boolean.TRUE.equals(entity.getActive())) {
            entity.setActive(false);
            entity.setDateDesactivation(Instant.now());
            repository.save(entity);
        }
        auditService.log(AuditAction.DELETE, "Signature", String.valueOf(id), null);
    }

    private void applyActiveFlag(Signature entity, boolean active) {
        if (active) {
            if (!Boolean.TRUE.equals(entity.getActive())) {
                Long utilisateurId = entity.getUtilisateur() != null ? entity.getUtilisateur().getId() : null;
                deactivateExistingActive(entity.getRole(), utilisateurId, entity.getId());
                entity.setActive(true);
                entity.setDateDesactivation(null);
            }
        } else if (Boolean.TRUE.equals(entity.getActive())) {
            entity.setActive(false);
            entity.setDateDesactivation(Instant.now());
        }
    }

    /** Garantit « au plus une signature active par couple (role, utilisateurId) » avant d'en activer une nouvelle. */
    private void deactivateExistingActive(Role role, Long utilisateurId, Long excludeId) {
        List<Signature> actives = utilisateurId != null
                ? repository.findByRoleAndUtilisateur_Id(role, utilisateurId)
                : repository.findByRoleAndUtilisateurIsNull(role);
        for (Signature s : actives) {
            if (Boolean.TRUE.equals(s.getActive()) && !Objects.equals(s.getId(), excludeId)) {
                s.setActive(false);
                s.setDateDesactivation(Instant.now());
                repository.save(s);
            }
        }
    }

    private void assertCanManage(Role targetRole, Long targetUtilisateurId, AuthenticatedUser user) {
        if (user == null || user.getRole() == null) {
            throw ApiException.unauthorized(ApiErrorCode.AUTH_REQUIRED, "Authentification requise");
        }
        if (user.getRole() == Role.ADMIN_SI) {
            return;
        }
        if (targetUtilisateurId == null || !targetUtilisateurId.equals(user.getUserId())) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Vous ne pouvez gérer que votre propre signature");
        }
        if (targetRole != user.getRole()) {
            throw ApiException.forbidden(ApiErrorCode.ROLE_FORBIDDEN,
                    "Le rôle de la signature doit correspondre à votre rôle");
        }
    }

    private Signature findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND, "Signature non trouvée: " + id));
    }

    private String usernameOf(AuthenticatedUser user) {
        return user != null && user.getUsername() != null ? user.getUsername() : "system";
    }

    private static String safeFileName(MultipartFile file, String fallback) {
        String name = file != null ? file.getOriginalFilename() : null;
        return name != null && !name.isBlank() ? name : fallback;
    }

    private record PngInfo(byte[] content, int width, int height, String checksum) {
    }

    private PngInfo validateAndInspectPng(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Le fichier est vide");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw ApiException.badRequest(ApiErrorCode.FILE_TOO_LARGE, "Fichier trop volumineux (max 1 Mo)");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Impossible de lire le fichier");
        }
        if (content.length < 24 || !matchesPngMagicNumber(content)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Le fichier n'est pas un PNG valide (contrôle du magic number, indépendant du Content-Type déclaré)");
        }
        int width = readBigEndianInt(content, 16);
        int height = readBigEndianInt(content, 20);
        if (width <= 0 || height <= 0) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Dimensions PNG invalides");
        }
        if (width > MAX_WIDTH_PX || height > MAX_HEIGHT_PX) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED,
                    "Dimensions maximales dépassées (max " + MAX_WIDTH_PX + "x" + MAX_HEIGHT_PX + " px, reçu " + width + "x" + height + ")");
        }
        return new PngInfo(content, width, height, sha256Hex(content));
    }

    private static boolean matchesPngMagicNumber(byte[] content) {
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (content[i] != PNG_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /** IHDR : largeur (offset 16, 4 octets) puis hauteur (offset 20, 4 octets), big-endian — spec PNG. */
    private static int readBigEndianInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24)
                | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8)
                | (b[offset + 3] & 0xFF);
    }

    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw ApiException.internal(ApiErrorCode.INTERNAL_ERROR, "SHA-256 indisponible", e);
        }
    }

    private SignatureDto toDto(Signature s) {
        return SignatureDto.builder()
                .id(s.getId())
                .utilisateurId(s.getUtilisateur() != null ? s.getUtilisateur().getId() : null)
                .utilisateurNom(s.getUtilisateur() != null ? s.getUtilisateur().getNomComplet() : null)
                .role(s.getRole())
                .nomAffiche(s.getNomAffiche())
                .contentType(s.getContentType())
                .taille(s.getTaille())
                .largeurPx(s.getLargeurPx())
                .hauteurPx(s.getHauteurPx())
                .checksumSha256(s.getChecksumSha256())
                .active(s.getActive())
                .version(s.getVersion())
                .dateCreation(s.getDateCreation())
                .creePar(s.getCreePar())
                .dateDesactivation(s.getDateDesactivation())
                .build();
    }
}
