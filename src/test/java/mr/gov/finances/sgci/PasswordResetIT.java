package mr.gov.finances.sgci;

import jakarta.mail.internet.MimeMessage;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.StatutDemandeResetPassword;
import mr.gov.finances.sgci.repository.DemandeResetPasswordRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PasswordResetIT {

    private static final String TEST_EMAIL = "entreprise.reset@test.local";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private DemandeResetPasswordRepository demandeResetPasswordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void ensureEntrepriseEmail() {
        Utilisateur entreprise = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        entreprise.setEmail(TEST_EMAIL);
        utilisateurRepository.save(entreprise);
        demandeResetPasswordRepository.deleteAll();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String loginToken(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("username", username, "password", password);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(body, headers),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("token").toString();
    }

    private HttpEntity<?> jsonEntity(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }

    @Test
    void checkEmail_existingActiveUser_returnsTrue() {
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/password-reset/check-email",
                jsonEntity(Map.of("email", TEST_EMAIL), null),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsEntry("exists", true);
    }

    @Test
    void checkEmail_unknown_returnsFalse() {
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/password-reset/check-email",
                jsonEntity(Map.of("email", "nobody@test.local"), null),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsEntry("exists", false);
    }

    @Test
    void submitRequest_createsPendingDemand() {
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/password-reset/request",
                jsonEntity(Map.of("email", TEST_EMAIL), null),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(demandeResetPasswordRepository.findAll()).hasSize(1);
        assertThat(demandeResetPasswordRepository.findAll().get(0).getStatut())
                .isEqualTo(StatutDemandeResetPassword.EN_ATTENTE);
    }

    @Test
    void submitRequest_duplicatePending_returns409() {
        restTemplate.postForEntity(
                baseUrl() + "/api/auth/password-reset/request",
                jsonEntity(Map.of("email", TEST_EMAIL), null),
                Map.class);
        ResponseEntity<Map> second = restTemplate.postForEntity(
                baseUrl() + "/api/auth/password-reset/request",
                jsonEntity(Map.of("email", TEST_EMAIL), null),
                Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void adminApprove_generatesNewPassword() throws Exception {
        restTemplate.postForEntity(
                baseUrl() + "/api/auth/password-reset/request",
                jsonEntity(Map.of("email", TEST_EMAIL), null),
                Map.class);
        Long demandeId = demandeResetPasswordRepository.findAll().get(0).getId();

        String adminToken = loginToken("admin", "admin");
        ResponseEntity<Map> approve = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/password-reset-requests/" + demandeId + "/approve",
                HttpMethod.PATCH,
                jsonEntity(null, adminToken),
                Map.class);
        assertThat(approve.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approve.getBody()).containsEntry("statut", "APPROUVEE");

        Utilisateur user = utilisateurRepository.findByUsername("entreprise").orElseThrow();
        assertThat(passwordEncoder.matches("123456", user.getPasswordHash())).isFalse();
    }

    @Test
    void adminReject_marksRefused() {
        restTemplate.postForEntity(
                baseUrl() + "/api/auth/password-reset/request",
                jsonEntity(Map.of("email", TEST_EMAIL), null),
                Map.class);
        Long demandeId = demandeResetPasswordRepository.findAll().get(0).getId();

        String adminToken = loginToken("admin", "admin");
        ResponseEntity<Map> reject = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/password-reset-requests/" + demandeId + "/reject",
                HttpMethod.PATCH,
                jsonEntity(Map.of("motif", "Identité non vérifiée"), adminToken),
                Map.class);
        assertThat(reject.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reject.getBody()).containsEntry("statut", "REFUSEE");
        assertThat(reject.getBody()).containsEntry("motifRefus", "Identité non vérifiée");
    }

    @Test
    void adminListPending_returnsRequests() {
        restTemplate.postForEntity(
                baseUrl() + "/api/auth/password-reset/request",
                jsonEntity(Map.of("email", TEST_EMAIL), null),
                Map.class);

        String adminToken = loginToken("admin", "admin");
        ResponseEntity<List> list = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/password-reset-requests?statut=EN_ATTENTE",
                HttpMethod.GET,
                jsonEntity(null, adminToken),
                List.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).isNotEmpty();
    }
}
