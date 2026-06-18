package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings({"rawtypes", "unchecked"})
class UtilisationCreditEligibilityIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CertificatCreditRepository certificatCreditRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private record LoginResult(String token, Long entrepriseId) {}

    private LoginResult login(String username, String password) {
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(Map.of("username", username, "password", password), jsonHeaders()),
                Map.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object eid = resp.getBody().get("entrepriseId");
        return new LoginResult(resp.getBody().get("token").toString(),
                eid == null ? null : ((Number) eid).longValue());
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
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

    private static List<Map<String, Object>> douaneLignes(BigDecimal droits, BigDecimal tva) {
        Map<String, Object> ligneDd = new LinkedHashMap<>();
        ligneDd.put("codeTaxe", "DD");
        ligneDd.put("denominationTaxe", "Droits de douane");
        ligneDd.put("typeLigne", "ARTICLE");
        ligneDd.put("valeurTaxe", droits);
        ligneDd.put("affectation", "AU_CI");
        Map<String, Object> ligneTva = new LinkedHashMap<>();
        ligneTva.put("codeTaxe", "TVA");
        ligneTva.put("denominationTaxe", "TVA importation");
        ligneTva.put("typeLigne", "ARTICLE");
        ligneTva.put("valeurTaxe", tva);
        ligneTva.put("affectation", "AU_CI");
        return List.of(ligneDd, ligneTva);
    }

    private ResponseEntity<Map> createDouaneUtilisation(String token, Long entrepriseId, Long certId,
                                                        List<Map<String, Object>> lignes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "DOUANIER");
        body.put("certificatCreditId", certId);
        body.put("entrepriseId", entrepriseId);
        body.put("numeroDeclaration", "DEC-ELIG-" + System.currentTimeMillis());
        body.put("numeroBulletin", "BUL-ELIG-001");
        body.put("enregistreeSYDONIA", true);
        body.put("lignes", lignes);
        return restTemplate.postForEntity(
                baseUrl() + "/api/utilisations-credit",
                bearerJson(token, body),
                Map.class
        );
    }

    @Test
    void certificatOuvert_createUtilisationDouane_reussit() {
        LoginResult ent = login("entreprise", "123456");
        LoginResult president = login("president", "123456");
        ResponseEntity<List> certsList = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(president.token()),
                List.class
        );
        Long certId = findCertificatIdByNumero(certsList.getBody(), "CI-TEST-OUVERT");

        ResponseEntity<Map> created = createDouaneUtilisation(
                ent.token(), ent.entrepriseId(), certId,
                douaneLignes(new BigDecimal("5000"), new BigDecimal("2000")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void getEligibilite_certificatOuvert_eligible() {
        LoginResult ent = login("entreprise", "123456");
        LoginResult president = login("president", "123456");
        ResponseEntity<List> certsList = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(president.token()),
                List.class
        );
        Long certId = findCertificatIdByNumero(certsList.getBody(), "CI-TEST-OUVERT");

        ResponseEntity<Map> elig = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + certId + "/eligibilite-utilisation?type=DOUANIER",
                HttpMethod.GET,
                bearer(ent.token()),
                Map.class
        );
        assertThat(elig.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(elig.getBody().get("eligible")).isEqualTo(true);
        assertThat((List<?>) elig.getBody().get("motifs")).isEmpty();
    }

    @Test
    void certificatModifie_createUtilisationDouane_reussit() {
        LoginResult ent = login("entreprise", "123456");
        LoginResult president = login("president", "123456");
        ResponseEntity<List> certsList = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(president.token()),
                List.class
        );
        Long certId = findCertificatIdByNumero(certsList.getBody(), "CI-TEST-OUVERT");

        assertThat(restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + certId + "/statut?statut=MODIFIE",
                HttpMethod.PATCH,
                bearer(president.token()),
                Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> created = createDouaneUtilisation(
                ent.token(), ent.entrepriseId(), certId,
                douaneLignes(new BigDecimal("1000"), new BigDecimal("500")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void certificatCloture_createUtilisationDouane_refuse() {
        LoginResult ent = login("entreprise", "123456");
        LoginResult president = login("president", "123456");
        ResponseEntity<List> certsList = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(president.token()),
                List.class
        );
        Long certId = findCertificatIdByNumero(certsList.getBody(), "CI-DEMO-SCEN-E");

        assertThat(restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + certId + "/statut?statut=CLOTURE",
                HttpMethod.PATCH,
                bearer(president.token()),
                Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> created = createDouaneUtilisation(
                ent.token(), ent.entrepriseId(), certId,
                douaneLignes(new BigDecimal("10"), new BigDecimal("5")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void certificatExpire_createUtilisationDouane_refuse() {
        LoginResult ent = login("entreprise", "123456");
        CertificatCredit cert = certificatCreditRepository.findByNumero("CI-DEMO-SCEN-C")
                .orElseThrow(() -> new AssertionError("Seed CI-DEMO-SCEN-C absent"));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            CertificatCredit managed = certificatCreditRepository.findById(cert.getId()).orElseThrow();
            managed.setDateValidite(Instant.now().minus(1, ChronoUnit.DAYS));
            certificatCreditRepository.save(managed);
        });

        ResponseEntity<Map> created = createDouaneUtilisation(
                ent.token(), ent.entrepriseId(), cert.getId(),
                douaneLignes(new BigDecimal("10"), new BigDecimal("5")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void soldeCordonInsuffisant_createUtilisationDouane_refuse() {
        LoginResult ent = login("entreprise", "123456");
        LoginResult president = login("president", "123456");
        ResponseEntity<List> certsList = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(president.token()),
                List.class
        );
        Long certId = findCertificatIdByNumero(certsList.getBody(), "CI-DEMO-SCEN-E");

        ResponseEntity<Map> created = createDouaneUtilisation(
                ent.token(), ent.entrepriseId(), certId,
                douaneLignes(new BigDecimal("50000"), new BigDecimal("5")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void quotaTvaImportInsuffisant_createUtilisationDouane_refuse() {
        LoginResult ent = login("entreprise", "123456");
        LoginResult president = login("president", "123456");
        ResponseEntity<List> certsList = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(president.token()),
                List.class
        );
        Long certId = findCertificatIdByNumero(certsList.getBody(), "CI-DEMO-SCEN-E");

        ResponseEntity<Map> created = createDouaneUtilisation(
                ent.token(), ent.entrepriseId(), certId,
                douaneLignes(new BigDecimal("10"), new BigDecimal("50000")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getEligibilite_certificatCloture_nonEligible() {
        LoginResult ent = login("entreprise", "123456");
        LoginResult president = login("president", "123456");
        ResponseEntity<List> certsList = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(president.token()),
                List.class
        );
        Long certId = findCertificatIdByNumero(certsList.getBody(), "CI-DEMO-SCEN-F");

        assertThat(restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + certId + "/statut?statut=CLOTURE",
                HttpMethod.PATCH,
                bearer(president.token()),
                Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> elig = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + certId + "/eligibilite-utilisation?type=DOUANIER",
                HttpMethod.GET,
                bearer(ent.token()),
                Map.class
        );
        assertThat(elig.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(elig.getBody().get("eligible")).isEqualTo(false);
        assertThat(String.valueOf(((List<?>) elig.getBody().get("motifs")).get(0)))
                .contains("clôturé");
    }
}
