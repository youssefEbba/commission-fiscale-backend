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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings({"rawtypes", "unchecked"})
class CertificatVerificationIT {

    private static final String CERT_OUVERT = "CI-DEMO-SCEN-E";
    private static final String CERT_EN_COURS = "CI-DEMO-PRESIDENT";

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

    private String loginToken(String username) {
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(Map.of("username", username, "password", "123456"), jsonHeaders()),
                Map.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("token").toString();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }

    private ResponseEntity<Map> verify(String token, String numero) {
        return restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/verification?numero=" + numero,
                HttpMethod.GET,
                bearer(token),
                Map.class
        );
    }

    @Test
    void verify_numeroInconnu_retourneInconnu() {
        String token = loginToken("dgd");
        ResponseEntity<Map> resp = verify(token, "CERT-INEXISTANT-XYZ");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("trouve")).isEqualTo(false);
        assertThat(resp.getBody().get("etatVerification")).isEqualTo("INCONNU");
        assertThat(resp.getBody().get("severiteUi")).isEqualTo("destructive");
    }

    @Test
    void verify_certificatOuvert_retourneValide() {
        String token = loginToken("entreprise");
        ResponseEntity<Map> resp = verify(token, CERT_OUVERT);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("trouve")).isEqualTo(true);
        assertThat(resp.getBody().get("etatVerification")).isEqualTo("VALIDE");
        assertThat(resp.getBody().get("statutCertificat")).isEqualTo("OUVERT");
        assertThat(resp.getBody().get("severiteUi")).isEqualTo("success");
        assertThat(resp.getBody().get("utilisableDouane")).isEqualTo(true);
    }

    @Test
    void verify_normaliseCasseEtEspaces() {
        String token = loginToken("dgd");
        ResponseEntity<Map> resp = verify(token, "  " + CERT_OUVERT.toLowerCase() + "  ");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("trouve")).isEqualTo(true);
        assertThat(resp.getBody().get("numero")).isEqualTo(CERT_OUVERT);
    }

    @Test
    void verify_certificatCloture_retourneCloture() {
        String presidentToken = loginToken("president");
        String dgdToken = loginToken("dgd");
        CertificatCredit cert = certificatCreditRepository.findByNumero("CI-DEMO-SCEN-F")
                .orElseThrow(() -> new AssertionError("Seed CI-DEMO-SCEN-F absent"));

        assertThat(restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + cert.getId() + "/statut?statut=CLOTURE",
                HttpMethod.PATCH,
                bearer(presidentToken),
                Map.class
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> resp = verify(dgdToken, "CI-DEMO-SCEN-F");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("etatVerification")).isEqualTo("CLOTURE");
        assertThat(resp.getBody().get("severiteUi")).isEqualTo("muted");
        assertThat(resp.getBody().get("utilisableDouane")).isEqualTo(false);
    }

    @Test
    void verify_certificatExpire_retourneExpire() {
        String token = loginToken("dgd");
        CertificatCredit cert = certificatCreditRepository.findByNumero("CI-DEMO-SCEN-C")
                .orElseThrow(() -> new AssertionError("Seed CI-DEMO-SCEN-C absent"));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            CertificatCredit managed = certificatCreditRepository.findById(cert.getId()).orElseThrow();
            managed.setDateValidite(Instant.now().minus(1, ChronoUnit.DAYS));
            certificatCreditRepository.save(managed);
        });

        ResponseEntity<Map> resp = verify(token, "CI-DEMO-SCEN-C");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("etatVerification")).isEqualTo("EXPIRE");
        assertThat(resp.getBody().get("expire")).isEqualTo(true);
        assertThat(resp.getBody().get("severiteUi")).isEqualTo("warning");
    }

    @Test
    void verify_certificatEnCoursMiseEnPlace_retourneEnCours() {
        String token = loginToken("president");
        ResponseEntity<Map> resp = verify(token, CERT_EN_COURS);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("trouve")).isEqualTo(true);
        assertThat(resp.getBody().get("etatVerification")).isEqualTo("EN_COURS");
        assertThat(resp.getBody().get("severiteUi")).isEqualTo("warning");
    }

    @Test
    void verify_sansNumero_retourne400() {
        String token = loginToken("dgd");
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/verification",
                HttpMethod.GET,
                bearer(token),
                Map.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void verify_sansAuth_retourne401() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/verification?numero=" + CERT_OUVERT,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                Map.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
