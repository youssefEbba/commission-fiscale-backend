package mr.gov.finances.sgci.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Client MinIO actif uniquement si {@code app.storage.backend=minio} (défaut).
 * Pour un mode dégradé sans MinIO, utiliser {@code app.storage.backend=local} (fichiers sous {@code app.upload.dir}).
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.backend", havingValue = "minio", matchIfMissing = true)
public class MinioConfig {

    @Value("${minio.url}")
    private String url;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }
}
