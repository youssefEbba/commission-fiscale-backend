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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rejets : REJET_TEMP + résolution (certificat, utilisation) et REJETEE définitif (utilisation).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class RejetWorkflowsIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private record LoginResult(String token, Long entrepriseId, Long autoriteContractanteId) {}

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
        Object aid = resp.getBody().get("autoriteContractanteId");
        Long autoriteId = aid == null ? null : ((Number) aid).longValue();
        return new LoginResult(token.toString(), entrepriseId, autoriteId);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private HttpEntity<Void> bearer(String token) {
        return new HttpEntity<>(bearerHeaders(token));
    }

    private HttpEntity<Map<String, Object>> bearerJson(String token, Map<String, Object> body) {
        HttpHeaders h = bearerHeaders(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    private static Long findCertificatIdByNumero(List<?> certs, String numero) {
        for (Object o : certs) {
            if (o instanceof Map m && numero.equals(String.valueOf(m.get("numero")))) {
                return ((Number) m.get("id")).longValue();
            }
        }
        throw new AssertionError("Certificat introuvable: " + numero);
    }

    private static Map<String, Object> findDemandeAdoptee(List<?> demandes) {
        for (Object o : demandes) {
            if (o instanceof Map m && "ADOPTEE".equals(String.valueOf(m.get("statut")))) {
                return m;
            }
        }
        throw new AssertionError("Aucune demande ADOPTEE dans la liste");
    }

    private static Long findDecisionIdForRole(List<?> decisions, String roleName) {
        for (Object o : decisions) {
            if (o instanceof Map m && roleName.equals(String.valueOf(m.get("role")))) {
                Object id = m.get("id");
                if (id instanceof Number n) {
                    return n.longValue();
                }
            }
        }
        throw new AssertionError("Décision introuvable pour le rôle: " + roleName);
    }

    /** Certificat EN_CONTROLE : REJET_TEMP DGD → INCOMPLETE → resolve → A_RECONTROLER. En dernier (annule le certificat seed). */
    @Test
    @Order(3)
    void certificat_rejet_temp_mis_en_place_puis_resolution() {
        LoginResult president = login("president", "123456");
        ResponseEntity<List> certsList = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(president.token()),
                List.class
        );
        Long seedCertId = findCertificatIdByNumero(certsList.getBody(), "CI-TEST-OUVERT");
        assertThat(restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + seedCertId + "/statut?statut=ANNULE",
                HttpMethod.PATCH,
                bearer(president.token()),
                Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        LoginResult ac = login("ac", "123456");
        ResponseEntity<List> demandesAc = restTemplate.exchange(
                baseUrl() + "/api/demandes-correction/by-autorite/" + ac.autoriteContractanteId(),
                HttpMethod.GET,
                bearer(ac.token()),
                List.class
        );
        Map<String, Object> demande = findDemandeAdoptee(demandesAc.getBody());
        Long demandeId = ((Number) demande.get("id")).longValue();
        Long entrepriseId = ((Number) demande.get("entrepriseId")).longValue();

        Map<String, Object> createCertBody = new LinkedHashMap<>();
        createCertBody.put("entrepriseId", entrepriseId);
        createCertBody.put("demandeCorrectionId", demandeId);
        ResponseEntity<Map> createdCert = restTemplate.postForEntity(
                baseUrl() + "/api/certificats-credit",
                bearerJson(ac.token(), createCertBody),
                Map.class
        );
        assertThat(createdCert.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long certId = ((Number) createdCert.getBody().get("id")).longValue();

        LoginResult dgdTake = login("dgd", "123456");
        assertThat(restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + certId + "/prendre-en-charge",
                HttpMethod.POST,
                bearer(dgdTake.token()),
                Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        LoginResult dgtcp = login("dgtcp", "123456");
        Map<String, Object> montants = new LinkedHashMap<>();
        montants.put("montantCordon", new BigDecimal("3500000"));
        montants.put("montantTVAInterieure", new BigDecimal("1500000"));
        assertThat(restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + certId + "/montants",
                HttpMethod.PATCH,
                bearerJson(dgtcp.token(), montants),
                Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        LoginResult dgd = login("dgd", "123456");
        Map<String, Object> rejetTemp = new LinkedHashMap<>();
        rejetTemp.put("decision", "REJET_TEMP");
        rejetTemp.put("motifRejet", "Compléments requis (test IT)");
        rejetTemp.put("documentsDemandes", List.of("CONTRAT", "CERTIFICAT_NIF"));
        ResponseEntity<Map> decCreated = restTemplate.postForEntity(
                baseUrl() + "/api/certificats-credit/" + certId + "/decisions",
                bearerJson(dgd.token(), rejetTemp),
                Map.class
        );
        assertThat(decCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> certIncomplete = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + certId,
                HttpMethod.GET,
                bearer(dgd.token()),
                Map.class
        );
        assertThat(certIncomplete.getBody().get("statut")).isEqualTo("INCOMPLETE");

        ResponseEntity<List> decisions = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + certId + "/decisions",
                HttpMethod.GET,
                bearer(dgd.token()),
                List.class
        );
        Long decisionId = findDecisionIdForRole(decisions.getBody(), "DGD");

        ResponseEntity<Map> resolved = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/decisions/" + decisionId + "/resolve",
                HttpMethod.PUT,
                bearer(dgd.token()),
                Map.class
        );
        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> certApres = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + certId,
                HttpMethod.GET,
                bearer(dgd.token()),
                Map.class
        );
        assertThat(certApres.getBody().get("statut")).isEqualTo("A_RECONTROLER");
    }

    /** Utilisation douanière : REJET_TEMP DGD → INCOMPLETE → resolve → A_RECONTROLER. */
    @Test
    @Order(2)
    void utilisation_rejet_temp_puis_resolution() {
        LoginResult ent = login("entreprise", "123456");
        LoginResult president = login("president", "123456");
        ResponseEntity<List> certsList = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(president.token()),
                List.class
        );
        Long certOuvertId = findCertificatIdByNumero(certsList.getBody(), "CI-TEST-OUVERT");
        assertThat(ent.entrepriseId()).isNotNull();

        Map<String, Object> utilBody = new LinkedHashMap<>();
        utilBody.put("type", "DOUANIER");
        utilBody.put("certificatCreditId", certOuvertId);
        utilBody.put("entrepriseId", ent.entrepriseId());
        utilBody.put("numeroDeclaration", "DEC-REJ-TEMP-" + System.currentTimeMillis());
        utilBody.put("numeroBulletin", "BUL-REJ-001");
        utilBody.put("montantDroits", new BigDecimal("5000"));
        utilBody.put("montantTVA", new BigDecimal("2000"));
        utilBody.put("enregistreeSYDONIA", true);
        ResponseEntity<Map> utilResp = restTemplate.postForEntity(
                baseUrl() + "/api/utilisations-credit",
                bearerJson(ent.token(), utilBody),
                Map.class
        );
        assertThat(utilResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long utilId = ((Number) utilResp.getBody().get("id")).longValue();

        LoginResult dgd = login("dgd", "123456");
        Map<String, Object> rejetTemp = new LinkedHashMap<>();
        rejetTemp.put("decision", "REJET_TEMP");
        rejetTemp.put("motifRejet", "Justificatifs insuffisants (test IT)");
        rejetTemp.put("documentsDemandes", List.of("FACTURE", "DECLARATION_DOUANE"));
        assertThat(restTemplate.postForEntity(
                baseUrl() + "/api/utilisations-credit/" + utilId + "/decisions",
                bearerJson(dgd.token(), rejetTemp),
                Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> utilIncomplete = restTemplate.exchange(
                baseUrl() + "/api/utilisations-credit/" + utilId,
                HttpMethod.GET,
                bearer(dgd.token()),
                Map.class
        );
        assertThat(utilIncomplete.getBody().get("statut")).isEqualTo("INCOMPLETE");

        ResponseEntity<List> decisions = restTemplate.exchange(
                baseUrl() + "/api/utilisations-credit/" + utilId + "/decisions",
                HttpMethod.GET,
                bearer(dgd.token()),
                List.class
        );
        Long decisionId = findDecisionIdForRole(decisions.getBody(), "DGD");

        assertThat(restTemplate.exchange(
                baseUrl() + "/api/utilisations-credit/decisions/" + decisionId + "/resolve",
                HttpMethod.PUT,
                bearer(dgd.token()),
                Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> utilApres = restTemplate.exchange(
                baseUrl() + "/api/utilisations-credit/" + utilId,
                HttpMethod.GET,
                bearer(dgd.token()),
                Map.class
        );
        assertThat(utilApres.getBody().get("statut")).isEqualTo("A_RECONTROLER");
    }

    /** Rejet définitif sans passage par EN_VERIFICATION (Douane). */
    @Test
    @Order(1)
    void utilisation_rejet_definitif_depuis_demandee() {
        LoginResult ent = login("entreprise", "123456");
        LoginResult president = login("president", "123456");
        ResponseEntity<List> certsList = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(president.token()),
                List.class
        );
        Long certOuvertId = findCertificatIdByNumero(certsList.getBody(), "CI-TEST-OUVERT");

        Map<String, Object> utilBody = new LinkedHashMap<>();
        utilBody.put("type", "DOUANIER");
        utilBody.put("certificatCreditId", certOuvertId);
        utilBody.put("entrepriseId", ent.entrepriseId());
        utilBody.put("numeroDeclaration", "DEC-REJ-DEF-" + System.currentTimeMillis());
        utilBody.put("numeroBulletin", "BUL-REJ-DEF");
        utilBody.put("montantDroits", new BigDecimal("3000"));
        utilBody.put("montantTVA", new BigDecimal("1000"));
        utilBody.put("enregistreeSYDONIA", false);
        ResponseEntity<Map> utilResp = restTemplate.postForEntity(
                baseUrl() + "/api/utilisations-credit",
                bearerJson(ent.token(), utilBody),
                Map.class
        );
        assertThat(utilResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long utilId = ((Number) utilResp.getBody().get("id")).longValue();

        LoginResult dgd = login("dgd", "123456");
        ResponseEntity<Map> rejet = restTemplate.exchange(
                baseUrl() + "/api/utilisations-credit/" + utilId + "/statut?statut=REJETEE",
                HttpMethod.PATCH,
                bearer(dgd.token()),
                Map.class
        );
        assertThat(rejet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejet.getBody().get("statut")).isEqualTo("REJETEE");
    }
}
