package mr.gov.finances.sgci;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fume des workflows Correction / Mise en place / Utilisation sur données seedées
 * ({@link mr.gov.finances.sgci.config.DataInitializer}) avec H2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorkflowSmokeIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private record LoginResult(String token, Long entrepriseId) {}

    private LoginResult login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("username", username, "password", password);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                entity,
                Map.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        Object token = resp.getBody().get("token");
        assertThat(token).isNotNull();
        Object eid = resp.getBody().get("entrepriseId");
        Long entrepriseId = eid == null ? null : ((Number) eid).longValue();
        return new LoginResult(token.toString(), entrepriseId);
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }

    @Test
    void entreprise_peut_lister_demande_correction_certificat_et_utilisations() {
        LoginResult auth = login("entreprise", "123456");
        assertThat(auth.entrepriseId()).isNotNull();

        ResponseEntity<List> demandes = restTemplate.exchange(
                baseUrl() + "/api/demandes-correction/by-entreprise/" + auth.entrepriseId(),
                HttpMethod.GET,
                bearer(auth.token()),
                List.class
        );
        assertThat(demandes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(demandes.getBody()).isNotNull();
        assertThat(demandes.getBody()).isNotEmpty();

        ResponseEntity<List> demandesScoped = restTemplate.exchange(
                baseUrl() + "/api/demandes-correction",
                HttpMethod.GET,
                bearer(auth.token()),
                List.class
        );
        assertThat(demandesScoped.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(demandesScoped.getBody()).isNotNull();
        assertThat(demandesScoped.getBody().size()).isEqualTo(demandes.getBody().size());

        ResponseEntity<List> certs = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/by-entreprise/" + auth.entrepriseId(),
                HttpMethod.GET,
                bearer(auth.token()),
                List.class
        );
        assertThat(certs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(certs.getBody()).isNotNull();
        assertThat(certs.getBody()).isNotEmpty();

        ResponseEntity<List> certsScoped = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(auth.token()),
                List.class
        );
        assertThat(certsScoped.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(certsScoped.getBody()).isNotNull();
        assertThat(certsScoped.getBody().size()).isEqualTo(certs.getBody().size());

        boolean hasOuvert = false;
        for (Object o : certs.getBody()) {
            if (o instanceof Map<?, ?> m && "CI-TEST-OUVERT".equals(String.valueOf(m.get("numero")))) {
                hasOuvert = true;
                break;
            }
        }
        assertThat(hasOuvert).as("Certificat seed CI-TEST-OUVERT présent").isTrue();

        ResponseEntity<List> utils = restTemplate.exchange(
                baseUrl() + "/api/utilisations-credit",
                HttpMethod.GET,
                bearer(auth.token()),
                List.class
        );
        assertThat(utils.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(utils.getBody()).isNotNull();
        assertThat(utils.getBody().size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void dgd_peut_lister_demandes_certificats_et_utilisations() {
        LoginResult auth = login("dgd", "123456");

        assertThat(restTemplate.exchange(
                baseUrl() + "/api/demandes-correction",
                HttpMethod.GET,
                bearer(auth.token()),
                List.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(auth.token()),
                List.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(restTemplate.exchange(
                baseUrl() + "/api/utilisations-credit",
                HttpMethod.GET,
                bearer(auth.token()),
                List.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
