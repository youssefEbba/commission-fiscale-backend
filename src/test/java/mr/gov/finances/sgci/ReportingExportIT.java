package mr.gov.finances.sgci;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les exports du reporting sont envoyés en flux (StreamingResponseBody, envoi différé) : le
 * téléchargement doit aller jusqu'au bout. Sans autorisation des envois différés, la réponse
 * partait puis restait ouverte et le navigateur attendait sans fin.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:sgci_it_reporting_export;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        }
)
@ActiveProfiles("test")
class ReportingExportIT {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private String jeton() throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"president\",\"password\":\"123456\"}"))
                .timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        return String.valueOf(new ObjectMapper().readValue(r.body(), Map.class).get("token"));
    }

    @org.junit.jupiter.api.Test
    void sansJetonLExportResteRefuse() throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/reporting/export.csv?blocs=ENGAGEMENT"))
                .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isIn(401, 403);
    }

    @ParameterizedTest(name = "{0} — {1}")
    @CsvSource({
            "xlsx, ENGAGEMENT", "csv, ENGAGEMENT", "pdf, ENGAGEMENT",
            "xlsx, SATISFACTION", "csv, SATISFACTION", "pdf, SATISFACTION",
            "xlsx, BLOCAGES", "csv, A_FERMER", "pdf, PRODUCTION",
    })
    void lExportSeTelechargeEntierement(String format, String bloc) throws Exception {
        HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/reporting/export." + format + "?blocs=" + bloc))
                .header("Authorization", "Bearer " + jeton())
                // Le téléchargement doit se terminer : un envoi différé refusé laissait la réponse ouverte.
                .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(r.statusCode()).isEqualTo(200);
        byte[] corps = r.body();
        assertThat(corps.length).isGreaterThan(20);
        String debut = new String(corps, 0, 4, StandardCharsets.ISO_8859_1);
        switch (format) {
            case "pdf" -> assertThat(debut).isEqualTo("%PDF");
            case "xlsx" -> assertThat(debut).startsWith("PK");
            default -> assertThat(new String(corps, StandardCharsets.UTF_8)).contains(";");
        }
        // Le nom du fichier dit quel onglet a été exporté.
        assertThat(r.headers().firstValue("Content-Disposition").orElse(""))
                .contains("attachment").contains("reporting-" + bloc.toLowerCase().replace('_', '-') + "-");
    }
}
