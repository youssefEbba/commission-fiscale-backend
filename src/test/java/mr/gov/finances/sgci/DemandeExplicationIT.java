package mr.gov.finances.sgci;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class DemandeExplicationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private static Long correctionDossierId;
    private static Long explicationId;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String loginToken(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("username", username, "password", "123456"), headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl() + "/api/auth/login", entity, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("token").toString();
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }

    private HttpEntity<Map<String, Object>> bearerJson(String token, Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    @Test
    @Order(1)
    void entreprise_ne_peut_pas_acceder_aux_explications() {
        String token = loginToken("entreprise");
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/demandes-explication?contexte=CORRECTION&dossierId=1",
                HttpMethod.GET,
                bearer(token),
                Map.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(2)
    void commission_ouvre_repond_et_ferme_fil_correction() {
        String dgiToken = loginToken("dgi");
        String dgdToken = loginToken("dgd");

        ResponseEntity<List> demandes = restTemplate.exchange(
                baseUrl() + "/api/demandes-correction/by-statut?statut=RECUE",
                HttpMethod.GET,
                bearer(dgiToken),
                List.class
        );
        assertThat(demandes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(demandes.getBody()).isNotEmpty();
        Map<String, Object> recue = null;
        for (Object item : demandes.getBody()) {
            Map<String, Object> m = (Map<String, Object>) item;
            if ("DC-TEST-EXPLICATION".equals(m.get("numero"))) {
                recue = m;
                break;
            }
        }
        if (recue == null) {
            recue = (Map<String, Object>) demandes.getBody().get(0);
        }
        correctionDossierId = ((Number) recue.get("id")).longValue();

        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("contexte", "CORRECTION");
        createBody.put("dossierId", correctionDossierId);
        createBody.put("roleDestinataire", "DGD");
        createBody.put("message", "Merci de préciser le calcul de la ligne (b) pour ce dossier.");

        ResponseEntity<Map> created = restTemplate.postForEntity(
                baseUrl() + "/api/demandes-explication",
                bearerJson(dgiToken, createBody),
                Map.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        explicationId = ((Number) created.getBody().get("id")).longValue();
        assertThat(created.getBody().get("statut")).isEqualTo("OUVERTE");
        assertThat(created.getBody().get("roleDestinataire")).isEqualTo("DGD");

        ResponseEntity<Map> reply = restTemplate.postForEntity(
                baseUrl() + "/api/demandes-explication/" + explicationId + "/messages",
                bearerJson(dgdToken, Map.of("message", "Le détail est dans la pièce jointe DGD-annexe.")),
                Map.class
        );
        assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<List> list = restTemplate.exchange(
                baseUrl() + "/api/demandes-explication?contexte=CORRECTION&dossierId=" + correctionDossierId,
                HttpMethod.GET,
                bearer(dgtcpToken()),
                List.class
        );
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).isNotEmpty();
        Map<String, Object> thread = (Map<String, Object>) list.getBody().get(0);
        List<Map<String, Object>> messages = (List<Map<String, Object>>) thread.get("messages");
        assertThat(messages).hasSize(1);

        ResponseEntity<Map> closed = restTemplate.exchange(
                baseUrl() + "/api/demandes-explication/" + explicationId + "/fermer",
                HttpMethod.PUT,
                bearer(dgiToken),
                Map.class
        );
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closed.getBody().get("statut")).isEqualTo("FERMEE");

        ResponseEntity<Map> replyAfterClose = restTemplate.postForEntity(
                baseUrl() + "/api/demandes-explication/" + explicationId + "/messages",
                bearerJson(dgdToken, Map.of("message", "Trop tard")),
                Map.class
        );
        assertThat(replyAfterClose.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String dgtcpToken() {
        return loginToken("dgtcp");
    }

    @Test
    @Order(3)
    void certificat_et_utilisation_smoke() {
        String dgdToken = loginToken("dgd");

        ResponseEntity<List> certs = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/by-statut?statut=EN_CONTROLE",
                HttpMethod.GET,
                bearer(dgdToken),
                List.class
        );
        assertThat(certs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(certs.getBody()).isNotEmpty();
        Map<String, Object> cert = null;
        for (Object item : certs.getBody()) {
            Map<String, Object> m = (Map<String, Object>) item;
            if ("CI-TEST-EN-CONTROLE".equals(m.get("numero"))) {
                cert = m;
                break;
            }
        }
        if (cert == null) {
            cert = (Map<String, Object>) certs.getBody().get(0);
        }
        Long certificatId = ((Number) cert.get("id")).longValue();

        Map<String, Object> certBody = Map.of(
                "contexte", "CERTIFICAT",
                "dossierId", certificatId,
                "roleDestinataire", "DGTCP",
                "message", "Confirmer la cohérence des montants DGTCP."
        );
        ResponseEntity<Map> certExplication = restTemplate.postForEntity(
                baseUrl() + "/api/demandes-explication",
                bearerJson(dgdToken, certBody),
                Map.class
        );
        assertThat(certExplication.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<List> utils = restTemplate.exchange(
                baseUrl() + "/api/utilisations-credit/by-certificat/" + certificatId,
                HttpMethod.GET,
                bearer(dgdToken),
                List.class
        );
        assertThat(utils.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(utils.getBody()).isNotEmpty();
        Long utilisationId = ((Number) ((Map<String, Object>) utils.getBody().get(0)).get("id")).longValue();

        Map<String, Object> utilBody = Map.of(
                "contexte", "UTILISATION",
                "dossierId", utilisationId,
                "roleDestinataire", "DGI",
                "message", "Point sur la déclaration douanière."
        );
        ResponseEntity<Map> utilExplication = restTemplate.postForEntity(
                baseUrl() + "/api/demandes-explication",
                bearerJson(dgdToken, utilBody),
                Map.class
        );
        assertThat(utilExplication.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
