package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.controller.LocalFileController;
import mr.gov.finances.sgci.web.exception.ApiException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un fichier stocké dans MinIO a pour chemin l'adresse interne du serveur MinIO, injoignable depuis
 * le navigateur : il se lit par /api/fichiers/{nom}, côté serveur. (Profil de test : stockage local.)
 */
@SpringBootTest
@ActiveProfiles("test")
class FichiersStockesIT {

    @Autowired private LocalFileController controller;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Value("${app.upload.dir:uploads}") private String uploadDir;

    @Test
    void unFichierStockeSeLitParLaRouteDuServeur() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken("president", null, "ROLE_PRESIDENT"));
        String nom = "abc" + System.nanoTime() + "_recu-depot.pdf";
        Path base = Path.of(uploadDir).toAbsolutePath();
        Files.createDirectories(base);
        Files.write(base.resolve(nom), "%PDF-test".getBytes());
        Utilisateur u = utilisateurRepository.findByUsername("president").orElseThrow();

        ResponseEntity<Resource> r = controller.downloadStocke(nom, new AuthenticatedUser(u.getId(), u.getUsername(), Role.PRESIDENT));

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody().getContentAsByteArray()).isEqualTo("%PDF-test".getBytes());
        assertThatThrownBy(() -> controller.downloadStocke("absent_" + nom,
                new AuthenticatedUser(u.getId(), u.getUsername(), Role.PRESIDENT)))
                .isInstanceOf(ApiException.class);
        Files.deleteIfExists(base.resolve(nom));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
