package mr.gov.finances.sgci;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Visa administrateur sur la demande de mise en place : substitution à un membre de la commission
 * (DGI / DGD / DGTCP), validation à la place du Président, puis ouverture du crédit — deux actions
 * délibérément distinctes.
 * <p>
 * Couvre aussi la régression du passage automatique en validation présidentielle depuis
 * {@code A_RECONTROLER}, arête ajoutée au workflow avec l'activation du contrôle de transition.
 * <p>
 * Base H2 dédiée : le test consomme les certificats de démonstration partagés.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:sgci_it_admin_visa;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        }
)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class AdminVisaCertificatIT {

    private static final String CI_VISAS = "CI-DEMO-VISAS-DGD-DGI";
    private static final String CI_PRESIDENT = "CI-DEMO-PRESIDENT";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ------------------------------------------------------------------ helpers

    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(Map.of("username", username, "password", password), headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl() + "/api/auth/login", entity, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        return String.valueOf(resp.getBody().get("token"));
    }

    private String admin() {
        return login("admin", "admin");
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

    private Long certificatId(String token, String numero) {
        ResponseEntity<List> resp = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit", HttpMethod.GET, bearer(token), List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (Object o : resp.getBody()) {
            if (o instanceof Map m && numero.equals(String.valueOf(m.get("numero")))) {
                return ((Number) m.get("id")).longValue();
            }
        }
        throw new AssertionError("Certificat introuvable: " + numero);
    }

    private Map certificat(String token, Long id) {
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + id, HttpMethod.GET, bearer(token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private String statut(String token, Long id) {
        return String.valueOf(certificat(token, id).get("statut"));
    }

    /** Appel du visa administrateur, avec ou sans pièce jointe. */
    private ResponseEntity<Map> adminVisa(String token, Long id, String role, String motif, boolean avecFichier) {
        HttpHeaders h = bearerHeaders(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("role", role);
        form.add("motif", motif);
        if (avecFichier) {
            form.add("file", new ByteArrayResource("certificat signe".getBytes()) {
                @Override
                public String getFilename() {
                    return "certificat-signe.pdf";
                }
            });
        }
        return restTemplate.exchange(baseUrl() + "/api/certificats-credit/" + id + "/visas/admin",
                HttpMethod.POST, new HttpEntity<>(form, h), Map.class);
    }

    private ResponseEntity<Map> adminOuvrir(String token, Long id, String motif) {
        return restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + id + "/ouverture/admin?motif=" + motif,
                HttpMethod.POST, bearer(token), Map.class);
    }

    private List visas(String token, Long id) {
        ResponseEntity<List> resp = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + id + "/visas", HttpMethod.GET, bearer(token), List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private static Map ligneVisa(List visas, String role) {
        for (Object o : visas) {
            if (o instanceof Map m && role.equals(String.valueOf(m.get("role")))) {
                return m;
            }
        }
        throw new AssertionError("Ligne de visa absente: " + role);
    }

    // ------------------------------------------------------------------ garde-fous

    @Test
    @Order(1)
    void refuse_les_acteurs_non_habilites_et_les_parametres_invalides() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_VISAS);

        // Un membre de la commission n'a pas la permission de dérogation.
        assertThat(adminVisa(login("dgi", "123456"), id, "DGTCP", "test", false).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Le Président détient la permission (assignAllPermissions) mais pas le rôle : refus du service.
        assertThat(adminVisa(login("president", "123456"), id, "DGTCP", "test", false).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Motif obligatoire.
        assertThat(adminVisa(adminToken, id, "DGTCP", "", false).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // DGB dispose d'une file d'attente mais ne vise pas le certificat.
        assertThat(adminVisa(adminToken, id, "DGB", "motif", false).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(2)
    void la_derogation_ne_fuit_pas_dans_la_transition_de_statut_generique() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_PRESIDENT);

        // La règle « VALIDE_PRESIDENT réservé au Président » doit rester intacte sur PATCH /statut.
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + id + "/statut?statut=VALIDE_PRESIDENT",
                HttpMethod.PATCH, bearer(adminToken), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statut(adminToken, id)).isEqualTo("EN_VALIDATION_PRESIDENT");
    }

    // ------------------------------------------------------------------ visa d'un membre

    @Test
    @Order(3)
    void refuse_le_visa_dgtcp_tant_que_les_montants_ne_sont_pas_renseignes() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_VISAS);

        ResponseEntity<Map> resp = adminVisa(adminToken, id, "DGTCP", "directeur absent", false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // L'écran d'administration doit annoncer le même blocage que celui réellement appliqué.
        Map ligne = ligneVisa(visas(adminToken, id), "DGTCP");
        assertThat((Boolean) ligne.get("visableParAdmin")).isFalse();
        assertThat(String.valueOf(ligne.get("motifBlocage"))).contains("montant");
    }

    @Test
    @Order(4)
    void refuse_un_fichier_pour_un_visa_de_membre_sans_document_attendu() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_VISAS);

        ResponseEntity<Map> resp = adminVisa(adminToken, id, "DGD", "motif", true);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Aucun fichier orphelin ne doit subsister : les contrôles métier précèdent le téléversement.
        ResponseEntity<List> docs = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + id + "/documents",
                HttpMethod.GET, bearer(adminToken), List.class);
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody()).isEmpty();
    }

    @Test
    @Order(5)
    void vise_a_la_place_de_la_dgtcp_et_declenche_le_passage_en_validation_presidentielle() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_VISAS);

        // L'administrateur renseigne lui-même les montants : aucun retour vers la DGTCP.
        Map<String, Object> montants = new LinkedHashMap<>();
        montants.put("montantCordon", new BigDecimal("4000000"));
        montants.put("montantTVAInterieure", new BigDecimal("2000000"));
        ResponseEntity<Map> patch = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + id + "/montants/admin?motif=dgtcp+indisponible",
                HttpMethod.POST, bearerJson(adminToken, montants), Map.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Les soldes provisoires doivent être initialisés comme par le parcours DGTCP.
        assertThat(certificat(adminToken, id).get("soldeCordon")).isNotNull();

        ResponseEntity<Map> resp = adminVisa(adminToken, id, "DGTCP", "directeur en mission", false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map decision = (Map) resp.getBody().get("decision");
        assertThat(decision).isNotNull();
        assertThat(String.valueOf(decision.get("role"))).isEqualTo("DGTCP");
        assertThat((Boolean) decision.get("visaParAdmin")).isTrue();
        assertThat(String.valueOf(decision.get("motifAdmin"))).isEqualTo("directeur en mission");

        // Le troisième visa déclenche l'auto-transition, désormais validée par le workflow.
        assertThat(statut(adminToken, id)).isEqualTo("EN_VALIDATION_PRESIDENT");

        // La trace est visible dans l'historique des décisions.
        ResponseEntity<List> decisions = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + id + "/decisions",
                HttpMethod.GET, bearer(adminToken), List.class);
        assertThat(decisions.getBody()).anyMatch(o -> o instanceof Map m
                && "DGTCP".equals(String.valueOf(m.get("role")))
                && Boolean.TRUE.equals(m.get("visaParAdmin")));
    }

    @Test
    @Order(5)
    void refuse_la_prise_en_charge_sur_un_certificat_deja_en_controle() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_VISAS);

        // Le parcours administrateur complet expose la prise en charge, bornée au statut ENVOYEE.
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/certificats-credit/" + id + "/prise-en-charge/admin?motif=test",
                HttpMethod.POST, bearer(adminToken), Map.class);
        assertThat(resp.getStatusCode()).isIn(HttpStatus.CONFLICT, HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(6)
    void refuse_un_second_visa_pour_le_meme_role() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_VISAS);
        assertThat(adminVisa(adminToken, id, "DGTCP", "nouvelle tentative", false).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------ substitution présidentielle

    @Test
    @Order(7)
    void refuse_la_validation_presidentielle_sans_certificat_signe() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_PRESIDENT);

        ResponseEntity<Map> resp = adminVisa(adminToken, id, "PRESIDENT", "president empeche", false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statut(adminToken, id)).isEqualTo("EN_VALIDATION_PRESIDENT");

        Map ligne = ligneVisa(visas(adminToken, id), "PRESIDENT");
        assertThat(String.valueOf(ligne.get("codeDocumentRequis"))).isEqualTo("CERTIFICAT_CREDIT_IMPOTS");
        assertThat((Boolean) ligne.get("documentRequisPresent")).isFalse();
    }

    @Test
    @Order(8)
    void valide_a_la_place_du_president_avec_le_certificat_signe() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_PRESIDENT);

        ResponseEntity<Map> resp = adminVisa(adminToken, id, "PRESIDENT", "president empeche", true);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // La validation présidentielle n'est pas une décision de commission.
        assertThat(resp.getBody().get("decision")).isNull();
        assertThat(resp.getBody().get("document")).isNotNull();

        // Elle s'arrête à VALIDE_PRESIDENT : l'ouverture du crédit reste une action distincte.
        assertThat(statut(adminToken, id)).isEqualTo("VALIDE_PRESIDENT");
    }

    @Test
    @Order(9)
    void refuse_une_seconde_validation_presidentielle() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_PRESIDENT);
        assertThat(adminVisa(adminToken, id, "PRESIDENT", "nouvelle tentative", true).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------ ouverture du crédit

    @Test
    @Order(10)
    void refuse_l_ouverture_administrateur_avant_la_validation_presidentielle() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_VISAS);

        // Ce certificat est en EN_VALIDATION_PRESIDENT : les deux actions restent séparées.
        assertThat(statut(adminToken, id)).isEqualTo("EN_VALIDATION_PRESIDENT");
        assertThat(adminOuvrir(adminToken, id, "raccourci").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(statut(adminToken, id)).isEqualTo("EN_VALIDATION_PRESIDENT");
    }

    @Test
    @Order(11)
    void ouvre_le_credit_apres_validation_et_initialise_les_soldes() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_PRESIDENT);
        assertThat(statut(adminToken, id)).isEqualTo("VALIDE_PRESIDENT");

        ResponseEntity<Map> resp = adminOuvrir(adminToken, id, "president et dgtcp indisponibles");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map cert = certificat(adminToken, id);
        assertThat(String.valueOf(cert.get("statut"))).isEqualTo("OUVERT");
        assertThat(cert.get("dateMiseEnPlace")).isNotNull();
        assertThat(cert.get("soldeCordon")).isNotNull();
        assertThat(cert.get("soldeTVA")).isNotNull();

        // Idempotence : le crédit ne s'ouvre pas deux fois.
        assertThat(adminOuvrir(adminToken, id, "nouvelle tentative").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------ écran d'état

    @Test
    @Order(12)
    void expose_l_etat_complet_des_visas() {
        String adminToken = admin();
        Long id = certificatId(adminToken, CI_PRESIDENT);

        List lignes = visas(adminToken, id);
        assertThat(lignes).hasSize(4);
        assertThat(lignes.stream().map(o -> String.valueOf(((Map) o).get("role"))).toList())
                .containsExactly("DGI", "DGD", "DGTCP", "PRESIDENT");

        Map president = ligneVisa(lignes, "PRESIDENT");
        assertThat((Boolean) president.get("pose")).isTrue();
        assertThat((Boolean) president.get("visableParAdmin")).isFalse();
        assertThat(String.valueOf(president.get("motifBlocage"))).contains("déjà validé");

        // Un membre de la commission peut consulter l'état ; l'entreprise non.
        assertThat(restTemplate.exchange(baseUrl() + "/api/certificats-credit/" + id + "/visas",
                HttpMethod.GET, bearer(login("dgi", "123456")), List.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(baseUrl() + "/api/certificats-credit/" + id + "/visas",
                HttpMethod.GET, bearer(login("entreprise", "123456")), List.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
