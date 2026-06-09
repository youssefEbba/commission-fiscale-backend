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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paramétrage GED : référentiel types + exigences par processus.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings({"rawtypes", "unchecked"})
class DocumentTypesParametrageIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String loginAdmin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("username", "admin", "password", "admin"), headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl() + "/api/auth/login", entity, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("token").toString();
    }

    private HttpEntity<?> bearerJson(String token, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }

    @Test
    void admin_peut_creer_type_et_exigence_pour_processus_utilisation_douane() {
        String token = loginAdmin();
        String code = ("NOUVEAU_DOC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)).toUpperCase();

        ResponseEntity<Map> createType = restTemplate.postForEntity(
                baseUrl() + "/api/referentiel/types-document",
                bearerJson(token, Map.of(
                        "code", code,
                        "libelle", "Document test paramétrable",
                        "actif", true
                )),
                Map.class
        );
        assertThat(createType.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createType.getBody().get("code")).isEqualTo(code);

        ResponseEntity<Map> createReq = restTemplate.postForEntity(
                baseUrl() + "/api/document-requirements",
                bearerJson(token, Map.of(
                        "processus", "UTILISATION_CI_DOUANE",
                        "codeDocument", code,
                        "obligatoire", false,
                        "description", "Pièce test IT",
                        "ordreAffichage", 99
                )),
                Map.class
        );
        assertThat(createReq.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createReq.getBody().get("codeDocument")).isEqualTo(code);

        ResponseEntity<List> listReq = restTemplate.exchange(
                baseUrl() + "/api/document-requirements?processus=UTILISATION_CI_DOUANE",
                HttpMethod.GET,
                bearer(token),
                List.class
        );
        assertThat(listReq.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listReq.getBody()).isNotNull();
        boolean found = false;
        for (Object o : listReq.getBody()) {
            Map<String, Object> m = (Map<String, Object>) o;
            if (code.equals(m.get("codeDocument"))) {
                found = true;
                break;
            }
        }
        assertThat(found).as("requirement for new code").isTrue();
    }
}
