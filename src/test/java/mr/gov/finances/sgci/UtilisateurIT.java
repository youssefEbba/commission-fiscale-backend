package mr.gov.finances.sgci;

import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings({"rawtypes", "unchecked"})
class UtilisateurIT {

    private static final String ENTREPRISE_PASSWORD = "12345678";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

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

    @BeforeEach
    void resetEntreprisePassword() {
        String adminToken = loginToken("admin", "admin");
        ResponseEntity<List> users = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs",
                HttpMethod.GET,
                jsonEntity(null, adminToken),
                List.class);
        assertThat(users.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long entrepriseId = null;
        for (Object row : users.getBody()) {
            Map<?, ?> u = (Map<?, ?>) row;
            if ("entreprise".equals(u.get("username"))) {
                entrepriseId = ((Number) u.get("id")).longValue();
                break;
            }
        }
        assertThat(entrepriseId).isNotNull();
        ResponseEntity<Map> updated = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/" + entrepriseId,
                HttpMethod.PUT,
                jsonEntity(Map.of("newPassword", ENTREPRISE_PASSWORD), adminToken),
                Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void utilisateur_connecte_peut_consulter_et_modifier_son_profil() {
        String token = loginToken("entreprise", ENTREPRISE_PASSWORD);

        ResponseEntity<Map> me = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/me",
                HttpMethod.GET,
                jsonEntity(null, token),
                Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).containsEntry("username", "entreprise");
        assertThat(me.getBody()).containsKey("entrepriseId");

        ResponseEntity<Map> updated = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/me",
                HttpMethod.PATCH,
                jsonEntity(Map.of(
                        "nomComplet", "Entreprise QA",
                        "email", "entreprise.qa@test.local"
                ), token),
                Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("nomComplet", "Entreprise QA");
        assertThat(updated.getBody()).containsEntry("email", "entreprise.qa@test.local");
    }

    @Test
    void utilisateur_connecte_peut_changer_son_mot_de_passe() {
        String token = loginToken("entreprise", ENTREPRISE_PASSWORD);

        ResponseEntity<Void> changed = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/me/password",
                HttpMethod.PATCH,
                jsonEntity(Map.of(
                        "currentPassword", ENTREPRISE_PASSWORD,
                        "newPassword", "NewPass789"
                ), token),
                Void.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> loginOld = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                jsonEntity(Map.of("username", "entreprise", "password", ENTREPRISE_PASSWORD), null),
                Map.class);
        assertThat(loginOld.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String newToken = loginToken("entreprise", "NewPass789");
        assertThat(newToken).isNotBlank();

        ResponseEntity<Void> restored = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/me/password",
                HttpMethod.PATCH,
                jsonEntity(Map.of(
                        "currentPassword", "NewPass789",
                        "newPassword", ENTREPRISE_PASSWORD
                ), newToken),
                Void.class);
        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void change_password_rejette_mot_de_passe_actuel_incorrect() {
        String token = loginToken("entreprise", ENTREPRISE_PASSWORD);

        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/me/password",
                HttpMethod.PATCH,
                jsonEntity(Map.of(
                        "currentPassword", "wrong",
                        "newPassword", "NewPass789"
                ), token),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void admin_peut_consulter_et_modifier_un_compte() {
        String adminToken = loginToken("admin", "admin");
        String entrepriseToken = loginToken("entreprise", ENTREPRISE_PASSWORD);

        ResponseEntity<Map> me = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/me",
                HttpMethod.GET,
                jsonEntity(null, entrepriseToken),
                Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long userId = ((Number) me.getBody().get("id")).longValue();

        ResponseEntity<Map> detail = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/" + userId,
                HttpMethod.GET,
                jsonEntity(null, adminToken),
                Map.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).containsEntry("username", "entreprise");

        ResponseEntity<Map> updated = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/" + userId,
                HttpMethod.PUT,
                jsonEntity(Map.of("nomComplet", "Entreprise modifiée admin"), adminToken),
                Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("nomComplet", "Entreprise modifiée admin");
    }

    @Test
    void entreprise_ne_peut_pas_modifier_un_autre_compte() {
        String entrepriseToken = loginToken("entreprise", ENTREPRISE_PASSWORD);

        ResponseEntity<Map> users = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs",
                HttpMethod.GET,
                jsonEntity(null, entrepriseToken),
                Map.class);
        assertThat(users.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> updated = restTemplate.exchange(
                baseUrl() + "/api/utilisateurs/1",
                HttpMethod.PUT,
                jsonEntity(Map.of("nomComplet", "Hack"), entrepriseToken),
                Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
