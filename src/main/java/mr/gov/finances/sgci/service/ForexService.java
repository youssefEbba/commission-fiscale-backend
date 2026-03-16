package mr.gov.finances.sgci.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class ForexService {

    private final RestClient restClient;

    @Value("${app.exchangerate.host.url:https://api.exchangerate.host}")
    private String baseUrl;

    @Value("${app.exchangerate.host.access-key:}")
    private String accessKey;

    public ForexService(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @SuppressWarnings("unchecked")
    public BigDecimal convert(String from, String to, BigDecimal amount) {
        if (!StringUtils.hasText(from) || !StringUtils.hasText(to)) {
            throw new RuntimeException("Les devises from/to sont obligatoires");
        }
        if (amount == null) {
            throw new RuntimeException("Le montant est obligatoire");
        }

        if (!StringUtils.hasText(accessKey)) {
            throw new RuntimeException("Clé API exchangerate.host manquante. Renseigner 'app.exchangerate.host.access-key' (ou variable env APP_EXCHANGERATE_HOST_ACCESS_KEY)");
        }

        String f = from.trim().toUpperCase();
        String t = to.trim().toUpperCase();
        String url = normalizeBaseUrl(baseUrl) + "/convert";

        String template = StringUtils.hasText(accessKey)
                ? (url + "?access_key={accessKey}&from={from}&to={to}&amount={amount}")
                : (url + "?from={from}&to={to}&amount={amount}");

        Map<String, Object> response = StringUtils.hasText(accessKey)
                ? restClient.get()
                .uri(template, accessKey, f, t, amount)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class)
                : restClient.get()
                .uri(template, f, t, amount)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new RuntimeException("Réponse vide du service de change");
        }

        Object successObj = response.get("success");
        if (successObj instanceof Boolean b && !b) {
            String errorMessage = resolveErrorMessage(response);
            throw new RuntimeException(errorMessage != null ? errorMessage : "Service de change: success=false");
        }

        Object resultObj = response.get("result");
        if (resultObj == null) {
            throw new RuntimeException("Résultat introuvable dans la réponse du service de change");
        }

        try {
            return new BigDecimal(resultObj.toString());
        } catch (Exception e) {
            throw new RuntimeException("Résultat invalide du service de change");
        }
    }

    public BigDecimal getRate(String from, String to) {
        return convert(from, to, BigDecimal.ONE);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    @SuppressWarnings("unchecked")
    private String resolveErrorMessage(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object errorObj = response.get("error");
        if (errorObj instanceof Map<?, ?> m) {
            Map<String, Object> error = (Map<String, Object>) m;
            Object info = error.get("info");
            if (info != null && StringUtils.hasText(info.toString())) {
                return info.toString();
            }
            Object message = error.get("message");
            if (message != null && StringUtils.hasText(message.toString())) {
                return message.toString();
            }
            Object type = error.get("type");
            if (type != null && StringUtils.hasText(type.toString())) {
                return type.toString();
            }
            return error.toString();
        }

        Object messageObj = response.get("message");
        if (messageObj != null && StringUtils.hasText(messageObj.toString())) {
            return messageObj.toString();
        }
        return null;
    }

    private String normalizeBaseUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "https://api.exchangerate.host";
        }
        String s = raw.trim();
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
