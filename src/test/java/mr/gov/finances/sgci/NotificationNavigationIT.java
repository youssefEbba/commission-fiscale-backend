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

import mr.gov.finances.sgci.domain.entity.DemandeCorrection;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.DemandeCorrectionRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.NotificationNavigationHelper;
import mr.gov.finances.sgci.service.WorkflowNotificationHelper;
import mr.gov.finances.sgci.web.dto.NotificationDto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings({"rawtypes", "unchecked"})
class NotificationNavigationIT {

    private static final String DEMO_PRESIDENT_DC = "DC-DEMO-PRESIDENT";
    private static final String TEST_EXPLICATION_DC = "DC-TEST-EXPLICATION";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private NotificationNavigationHelper navigationHelper;

    @Autowired
    private DemandeCorrectionRepository demandeCorrectionRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private WorkflowNotificationHelper workflowNotificationHelper;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private record LoginResult(String token, Long entrepriseId, Long autoriteContractanteId) {}

    private LoginResult login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("username", username, "password", password);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(body, headers),
                Map.class
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object token = resp.getBody().get("token");
        Object eid = resp.getBody().get("entrepriseId");
        Object aid = resp.getBody().get("autoriteContractanteId");
        return new LoginResult(
                token.toString(),
                eid == null ? null : ((Number) eid).longValue(),
                aid == null ? null : ((Number) aid).longValue()
        );
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


    private static Map<String, Object> findNotificationPayload(List<NotificationDto> notifications, String eventCode) {
        for (NotificationDto n : notifications) {
            Map<String, Object> p = n.getPayload();
            if (p != null && eventCode.equals(String.valueOf(p.get("eventCode")))) {
                return p;
            }
        }
        throw new AssertionError("Notification introuvable pour eventCode=" + eventCode);
    }

    @Test
    void navigationHelper_buildsKnownPaths() {
        assertThat(navigationHelper.buildRedirectPath("DemandeCorrection", 42L, null))
                .isEqualTo("/dashboard/demandes/42");
        assertThat(navigationHelper.buildRedirectPath("CertificatCredit", 7L, null))
                .isEqualTo("/dashboard/certificats/7");
        assertThat(navigationHelper.buildRedirectPath("UtilisationCredit", 9L, null))
                .isEqualTo("/dashboard/utilisations/9");
        assertThat(navigationHelper.buildRedirectPath("TransfertCredit", 3L, null))
                .isEqualTo("/dashboard/transferts/3");
        assertThat(navigationHelper.buildRedirectPath("ClotureCredit", 99L, 12L))
                .isEqualTo("/dashboard/certificats/12");
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

    @Test
    void correctionRejetTemp_notificationContainsNavigationPayload() {
        DemandeCorrection demande = demandeCorrectionRepository.findByNumero(TEST_EXPLICATION_DC)
                .orElseThrow(() -> new AssertionError("Seed " + TEST_EXPLICATION_DC + " absent"));
        LoginResult dgd = login("dgd", "123456");
        Map<String, Object> rejetTemp = new LinkedHashMap<>();
        rejetTemp.put("decision", "REJET_TEMP");
        rejetTemp.put("motifRejet", "Compléments correction (navigation IT)");
        rejetTemp.put("documentsDemandes", List.of("OFFRE_FISCALE_CORRIGEE"));
        ResponseEntity<Map> decCreated = restTemplate.postForEntity(
                baseUrl() + "/api/demandes-correction/" + demande.getId() + "/decisions",
                bearerJson(dgd.token(), rejetTemp),
                Map.class
        );
        assertThat(decCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long decisionId = ((Number) decCreated.getBody().get("id")).longValue();

        ResponseEntity<List> notificationsResp = restTemplate.exchange(
                baseUrl() + "/api/notifications",
                HttpMethod.GET,
                bearer(login("ac", "123456").token()),
                List.class
        );
        List<NotificationDto> notifications = notificationsResp.getBody().stream()
                .map(o -> {
                    Map m = (Map) o;
                    return NotificationDto.builder()
                            .payload((Map<String, Object>) m.get("payload"))
                            .build();
                })
                .toList();

        Map<String, Object> payload = findNotificationPayload(notifications, "CORRECTION_REJET_TEMP");
        assertThat(payload.get("redirectPath")).isEqualTo("/dashboard/demandes/" + demande.getId());
        assertThat(payload.get("entityType")).isEqualTo("DemandeCorrection");
        assertThat(((Number) payload.get("entityId")).longValue()).isEqualTo(demande.getId());
        assertThat(((Number) payload.get("decisionId")).longValue()).isEqualTo(decisionId);
        assertThat((List<String>) payload.get("documentsDemandes")).contains("OFFRE_FISCALE_CORRIGEE");
    }

    @Test
    void utilisationRejetTemp_notificationContainsNavigationPayload() {
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
        utilBody.put("numeroDeclaration", "DEC-NAV-" + System.currentTimeMillis());
        utilBody.put("numeroBulletin", "BUL-NAV-001");
        utilBody.put("montantDroits", new BigDecimal("5000"));
        utilBody.put("montantTVA", new BigDecimal("2000"));
        utilBody.put("enregistreeSYDONIA", true);
        utilBody.put("lignes", douaneLignes(new BigDecimal("5000"), new BigDecimal("2000")));
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
        rejetTemp.put("motifRejet", "Justificatifs insuffisants (navigation IT)");
        rejetTemp.put("documentsDemandes", List.of("FACTURE", "DECLARATION_DOUANE"));
        ResponseEntity<Map> decCreated = restTemplate.postForEntity(
                baseUrl() + "/api/utilisations-credit/" + utilId + "/decisions",
                bearerJson(dgd.token(), rejetTemp),
                Map.class
        );
        assertThat(decCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long decisionId = ((Number) decCreated.getBody().get("id")).longValue();

        ResponseEntity<List> notificationsResp = restTemplate.exchange(
                baseUrl() + "/api/notifications",
                HttpMethod.GET,
                bearer(ent.token()),
                List.class
        );
        List<NotificationDto> notifications = notificationsResp.getBody().stream()
                .map(o -> {
                    Map m = (Map) o;
                    return NotificationDto.builder()
                            .payload((Map<String, Object>) m.get("payload"))
                            .build();
                })
                .toList();

        Map<String, Object> payload = findNotificationPayload(notifications, "UTIL_DOUANE_REJET_TEMP");
        assertThat(payload.get("redirectPath")).isEqualTo("/dashboard/utilisations/" + utilId);
        assertThat(payload.get("entityType")).isEqualTo("UtilisationCredit");
        assertThat(((Number) payload.get("entityId")).longValue()).isEqualTo(utilId);
        assertThat(((Number) payload.get("decisionId")).longValue()).isEqualTo(decisionId);
        assertThat((List<String>) payload.get("documentsDemandes")).contains("FACTURE");
    }

    @Test
    void certificatRejetTemp_notificationContainsNavigationPayload() {
        LoginResult ac = login("ac", "123456");
        LoginResult president = login("president", "123456");
        ResponseEntity<List> certsList = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit",
                HttpMethod.GET,
                bearer(president.token()),
                List.class
        );
        Long certId = findCertificatIdByNumero(certsList.getBody(), "CI-DEMO-VISAS-DGD-DGI");

        LoginResult dgtcp = login("dgtcp", "123456");
        Map<String, Object> rejetTemp = new LinkedHashMap<>();
        rejetTemp.put("decision", "REJET_TEMP");
        rejetTemp.put("motifRejet", "Compléments certificat (navigation IT)");
        rejetTemp.put("documentsDemandes", List.of("CONTRAT"));
        ResponseEntity<Map> decCreated = restTemplate.postForEntity(
                baseUrl() + "/api/certificats-credit/" + certId + "/decisions",
                bearerJson(dgtcp.token(), rejetTemp),
                Map.class
        );
        assertThat(decCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long decisionId = ((Number) decCreated.getBody().get("id")).longValue();

        ResponseEntity<List> notificationsResp = restTemplate.exchange(
                baseUrl() + "/api/notifications",
                HttpMethod.GET,
                bearer(ac.token()),
                List.class
        );
        List<NotificationDto> notifications = notificationsResp.getBody().stream()
                .map(o -> {
                    Map m = (Map) o;
                    return NotificationDto.builder()
                            .payload((Map<String, Object>) m.get("payload"))
                            .build();
                })
                .toList();

        Map<String, Object> payload = findNotificationPayload(notifications, "CERTIFICAT_REJET_TEMP");
        assertThat(payload.get("redirectPath")).isEqualTo("/dashboard/certificats/" + certId);
        assertThat(((Number) payload.get("decisionId")).longValue()).isEqualTo(decisionId);
        assertThat((List<String>) payload.get("documentsDemandes")).contains("CONTRAT");
    }

    @Test
    void correctionStatutChange_notificationIncludesRedirectPath() {
        DemandeCorrection demande = demandeCorrectionRepository.findByNumero(DEMO_PRESIDENT_DC)
                .orElseThrow(() -> new AssertionError("Seed " + DEMO_PRESIDENT_DC + " absent"));
        Long acUserId = utilisateurRepository.findByUsername("ac")
                .orElseThrow(() -> new AssertionError("Utilisateur ac absent"))
                .getId();
        Long dgdUserId = utilisateurRepository.findByUsername("dgd")
                .orElseThrow(() -> new AssertionError("Utilisateur dgd absent"))
                .getId();

        AuthenticatedUser dgd = new AuthenticatedUser(dgdUserId, "dgd", Role.DGD);
        workflowNotificationHelper.correctionStatut(demande, StatutDemande.EN_VALIDATION, dgd, null, false);

        ResponseEntity<List> notificationsResp = restTemplate.exchange(
                baseUrl() + "/api/notifications",
                HttpMethod.GET,
                bearer(login("ac", "123456").token()),
                List.class
        );
        assertThat(notificationsResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        boolean found = false;
        for (Object o : notificationsResp.getBody()) {
            Object payloadObj = ((Map<?, ?>) o).get("payload");
            if (!(payloadObj instanceof Map<?, ?> rawPayload)) {
                continue;
            }
            Map<String, Object> payload = (Map<String, Object>) rawPayload;
            if ("CORRECTION_STATUT_CHANGE".equals(String.valueOf(payload.get("eventCode")))
                    && ("/dashboard/demandes/" + demande.getId()).equals(String.valueOf(payload.get("redirectPath")))) {
                found = true;
                break;
            }
        }
        assertThat(found).as("notification CORRECTION_STATUT_CHANGE avec redirectPath pour user ac=%s", acUserId).isTrue();
    }
}
