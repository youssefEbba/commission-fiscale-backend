package mr.gov.finances.sgci.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class TauxChangeService {

    private final RestClient restClient;

    @Value("${app.exchange-rate.base:MRU}")
    private String base;

    @Value("${app.exchange-rate.url:https://open.er-api.com/v6/latest/{base}}")
    private String url;

    public TauxChangeService(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @SuppressWarnings("unchecked")
    public BigDecimal getTaux(String devise) {
        if (devise == null || devise.isBlank()) {
            throw new RuntimeException("La devise est obligatoire");
        }
        String code = devise.trim().toUpperCase();

        Map<String, Object> response = restClient.get()
                .uri(url, base)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new RuntimeException("Réponse vide du service taux de change");
        }

        Object ratesObj = response.get("rates");
        if (!(ratesObj instanceof Map<?, ?> rates)) {
            throw new RuntimeException("Format inattendu (rates manquant) du service taux de change");
        }

        Object rateObj = rates.get(code);
        if (rateObj == null) {
            throw new RuntimeException("Taux introuvable pour devise: " + code);
        }

        try {
            return new BigDecimal(rateObj.toString());
        } catch (Exception e) {
            throw new RuntimeException("Taux invalide pour devise: " + code);
        }
    }

    public String getBase() {
        return base;
    }

    public String getUrl() {
        return url;
    }
}
