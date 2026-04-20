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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaîne complète sur données seed ({@link mr.gov.finances.sgci.config.DataInitializer}) :
 * annulation du certificat seed → mise en place (montants + 3 visas + ouverture président) →
 * notification demande → utilisation douanière → rejet DGD.
 * <p>
 * Préfixe {@code Z} : en général après {@link RejetWorkflowsIT} (ordre alphabétique des classes).
 * URL H2 dédiée : avec {@code DB_CLOSE_DELAY=-1} la mémoire H2 est partagée entre rechargements de
 * contexte ; une base isolée garantit le seed {@code CI-TEST-OUVERT} + une demande ADOPTEE libre.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:sgci_it_zall;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        }
)
@ActiveProfiles("test")
@SuppressWarnings({"rawtypes", "unchecked"})
class ZAllWorkflowsIT {

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

    private static Long findCertificatIdByNumero(List<Map<String, Object>> certs, String numero) {
        for (Object o : certs) {
            if (o instanceof Map m && numero.equals(String.valueOf(m.get("numero")))) {
                return ((Number) m.get("id")).longValue();
            }
        }
        throw new AssertionError("Certificat introuvable: " + numero);
    }

    private static Map<String, Object> findDemandeAdoptee(List<Map<String, Object>> demandes) {
        for (Object o : demandes) {
            if (o instanceof Map m && "ADOPTEE".equals(String.valueOf(m.get("statut")))) {
                return m;
            }
        }
        throw new AssertionError("Aucune demande ADOPTEE dans la liste");
    }

    @Test
    void chaine_mise_en_place_demande_utilisation() {
        LoginResult president = login("president", "123456");
        ResponseEntity<List> certsList = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(president.token()),
                List.class
        );
        assertThat(certsList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(certsList.getBody()).isNotNull();
        Long seedCertId = findCertificatIdByNumero(certsList.getBody(), "CI-TEST-OUVERT");
        // Libère la demande ADOPTEE seed (TestWorkflowDataSeed : un seul certificat non annulé par demande).
        ResponseEntity<Map> annule = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + seedCertId + "/statut?statut=ANNULE",
                HttpMethod.PATCH,
                bearer(president.token()),
                Map.class
        );
        assertThat(annule.getStatusCode()).isEqualTo(HttpStatus.OK);

        LoginResult ac = login("ac", "123456");
        assertThat(ac.autoriteContractanteId()).isNotNull();
        ResponseEntity<List> demandesAc = restTemplate.exchange(
                baseUrl() + "/api/demandes-correction/by-autorite/" + ac.autoriteContractanteId(),
                HttpMethod.GET,
                bearer(ac.token()),
                List.class
        );
        assertThat(demandesAc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(demandesAc.getBody()).isNotNull();
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
        assertThat(createdCert.getBody()).isNotNull();
        Long newCertId = ((Number) createdCert.getBody().get("id")).longValue();
        assertThat(createdCert.getBody().get("statut")).isEqualTo("ENVOYEE");

        LoginResult dgdTake = login("dgd", "123456");
        assertThat(restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + newCertId + "/prendre-en-charge",
                HttpMethod.POST,
                bearer(dgdTake.token()),
                Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        LoginResult dgtcp = login("dgtcp", "123456");
        Map<String, Object> montants = new LinkedHashMap<>();
        montants.put("montantCordon", new BigDecimal("4000000"));
        montants.put("montantTVAInterieure", new BigDecimal("2000000"));
        ResponseEntity<Map> patchMontants = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + newCertId + "/montants",
                HttpMethod.PATCH,
                bearerJson(dgtcp.token(), montants),
                Map.class
        );
        assertThat(patchMontants.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> visaBody = Map.of("decision", "VISA");
        for (String user : List.of("dgi", "dgd", "dgtcp")) {
            LoginResult lr = login(user, "123456");
            ResponseEntity<Map> dec = restTemplate.postForEntity(
                    baseUrl() + "/api/certificats-credit/" + newCertId + "/decisions",
                    bearerJson(lr.token(), visaBody),
                    Map.class
            );
            assertThat(dec.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        ResponseEntity<Map> certApresVisas = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + newCertId,
                HttpMethod.GET,
                bearer(president.token()),
                Map.class
        );
        assertThat(certApresVisas.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(certApresVisas.getBody()).isNotNull();
        assertThat(certApresVisas.getBody().get("statut")).isEqualTo("EN_VALIDATION_PRESIDENT");

        ResponseEntity<Map> ouvert = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + newCertId + "/statut?statut=OUVERT",
                HttpMethod.PATCH,
                bearer(president.token()),
                Map.class
        );
        assertThat(ouvert.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ouvert.getBody().get("statut")).isEqualTo("OUVERT");

        ResponseEntity<Map> notifiee = restTemplate.exchange(
                baseUrl() + "/api/demandes-correction/" + demandeId + "/statut?statut=NOTIFIEE",
                HttpMethod.PATCH,
                bearer(president.token()),
                Map.class
        );
        assertThat(notifiee.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notifiee.getBody().get("statut")).isEqualTo("NOTIFIEE");

        LoginResult ent = login("entreprise", "123456");
        Map<String, Object> utilBody = new LinkedHashMap<>();
        utilBody.put("type", "DOUANIER");
        utilBody.put("certificatCreditId", newCertId);
        utilBody.put("entrepriseId", entrepriseId);
        utilBody.put("numeroDeclaration", "DEC-IT-" + System.currentTimeMillis());
        utilBody.put("numeroBulletin", "BUL-IT-001");
        utilBody.put("montantDroits", new BigDecimal("10000"));
        utilBody.put("montantTVA", new BigDecimal("5000"));
        utilBody.put("enregistreeSYDONIA", true);
        ResponseEntity<Map> util = restTemplate.postForEntity(
                baseUrl() + "/api/utilisations-credit",
                bearerJson(ent.token(), utilBody),
                Map.class
        );
        assertThat(util.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(util.getBody()).isNotNull();
        Long utilId = ((Number) util.getBody().get("id")).longValue();
        assertThat(util.getBody().get("statut")).isEqualTo("DEMANDEE");

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
