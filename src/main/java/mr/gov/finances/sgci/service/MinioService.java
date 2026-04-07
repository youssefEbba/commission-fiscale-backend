package mr.gov.finances.sgci.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stockage des pièces jointes.
 * <ul>
 *   <li>{@code app.storage.backend=minio} (défaut) : MinIO/S3 ; panne ou mauvaise config → {@link ApiErrorCode#OBJECT_STORAGE_UNAVAILABLE} (503).</li>
 *   <li>{@code app.storage.backend=local} : fichiers sous {@code app.upload.dir}/documents, URL {@value #LOCAL_FILES_URL_PREFIX}{@code {nom}} servie par {@link mr.gov.finances.sgci.web.controller.LocalFileController}.</li>
 * </ul>
 */
@Service
public class MinioService {

    /** Préfixe d’URL retourné en mode local (GET authentifié). */
    public static final String LOCAL_FILES_URL_PREFIX = "/api/local-files/";

    private final MinioClient minioClient;
    private final String backend;
    private final String uploadDir;
    private final String bucket;
    private final String minioUrl;

    public MinioService(
            ObjectProvider<MinioClient> minioClientProvider,
            @Value("${app.storage.backend:minio}") String backend,
            @Value("${app.upload.dir:uploads}") String uploadDir,
            @Value("${minio.bucket:documents}") String bucket,
            @Value("${minio.url:http://localhost:9000}") String minioUrl
    ) {
        this.minioClient = minioClientProvider.getIfAvailable();
        this.backend = backend != null ? backend.trim() : "minio";
        this.uploadDir = uploadDir;
        this.bucket = bucket;
        this.minioUrl = minioUrl != null ? minioUrl.replaceAll("/+$", "") : "";
    }

    public String uploadFile(MultipartFile file) {
        if ("local".equalsIgnoreCase(backend)) {
            return uploadLocal(file);
        }
        if (minioClient == null) {
            throw ApiException.serviceUnavailable(ApiErrorCode.OBJECT_STORAGE_UNAVAILABLE,
                    "Client MinIO non configuré (app.storage.backend doit être « minio » avec un serveur MinIO joignable, ou « local » sans MinIO)");
        }
        try {
            String fileName = UUID.randomUUID() + "_" + safeOriginalName(file.getOriginalFilename());
            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(fileName)
                                .stream(in, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );
            }
            return minioUrl + "/" + bucket + "/" + fileName;
        } catch (Exception e) {
            throw ApiException.serviceUnavailable(ApiErrorCode.OBJECT_STORAGE_UNAVAILABLE,
                    "Stockage objet indisponible ou mal configuré", e);
        }
    }

    private String uploadLocal(MultipartFile file) {
        try {
            Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path dir = base.resolve("documents");
            Files.createDirectories(dir);
            String fileName = UUID.randomUUID() + "_" + safeOriginalName(file.getOriginalFilename());
            Path target = dir.resolve(fileName).normalize();
            if (!target.startsWith(dir)) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Nom de fichier invalide");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return LOCAL_FILES_URL_PREFIX + fileName;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.serviceUnavailable(ApiErrorCode.OBJECT_STORAGE_UNAVAILABLE,
                    "Échec écriture stockage local (vérifiez app.upload.dir)", e);
        }
    }

    private static String safeOriginalName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
