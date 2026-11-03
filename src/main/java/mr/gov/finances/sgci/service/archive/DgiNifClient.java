package mr.gov.finances.sgci.service.archive;

import lombok.extern.slf4j.Slf4j;
import mr.gov.finances.sgci.web.dto.archive.VerificationNifDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Service public « Vérifier NIF » de la DGI ({@code POST s_nif=…}). Il répond en JSON :
 * {@code {"itemList":[["<raison sociale>","<nom commercial>","<état>"]],"itemList_date":["…"]}},
 * avec une liste vide pour un NIF inconnu. Le troisième champ est rendu tel quel : la DGI n'en
 * publie pas la signification.
 */
@Component
@Slf4j
public class DgiNifClient {

    private final RestClient restClient;
    private final String url;
    private final boolean actif;

    @Autowired
    public DgiNifClient(RestClient.Builder builder,
                        @Value("${dgi.verification-nif.url:https://impots.gov.mr/DGI/verifier_NIF}") String url,
                        @Value("${dgi.verification-nif.actif:true}") boolean actif) {
        SimpleClientHttpRequestFactory delais = new SimpleClientHttpRequestFactory();
        delais.setConnectTimeout(Duration.ofSeconds(10));
        delais.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = builder.clone().requestFactory(delais).build();
        this.url = url;
        this.actif = actif;
    }

    /** Pour les tests : un client déjà construit (serveur simulé). */
    DgiNifClient(RestClient restClient, String url) {
        this.restClient = restClient;
        this.url = url;
        this.actif = true;
    }

    @SuppressWarnings("unchecked")
    public VerificationNifDto verifier(String nif) {
        if (!actif) {
            throw ApiException.serviceUnavailable(ApiErrorCode.FEATURE_DISABLED,
                    "La vérification du NIF auprès de la DGI est désactivée sur ce serveur.");
        }
        if (nif == null || nif.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le NIF est obligatoire");
        }
        String nifEnvoye = nif.trim();
        MultiValueMap<String, String> formulaire = new LinkedMultiValueMap<>();
        formulaire.add("s_nif", nifEnvoye);

        Map<String, Object> reponse;
        try {
            reponse = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(formulaire)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.warn("Vérification NIF DGI en échec pour {} : {}", nifEnvoye, e.getMessage());
            throw ApiException.serviceUnavailable(ApiErrorCode.DGI_SERVICE_UNAVAILABLE,
                    "Le service « Vérifier NIF » de la DGI est injoignable ou en erreur.", e);
        }

        VerificationNifDto dto = VerificationNifDto.builder().nif(nifEnvoye).trouve(false).build();
        if (reponse == null) {
            return dto;
        }
        if (reponse.get("itemList_date") instanceof List<?> dates && !dates.isEmpty() && dates.get(0) != null) {
            dto.setDateMiseAJour(String.valueOf(dates.get(0)));
        }
        if (reponse.get("itemList") instanceof List<?> items && !items.isEmpty()
                && items.get(0) instanceof List<?> ligne && !ligne.isEmpty()) {
            dto.setTrouve(true);
            dto.setRaisonSociale(champ(ligne, 0));
            dto.setNomCommercial(champ(ligne, 1));
            dto.setEtat(champ(ligne, 2));
        }
        return dto;
    }

    private static String champ(List<?> ligne, int i) {
        if (i >= ligne.size() || ligne.get(i) == null) {
            return null;
        }
        String v = String.valueOf(ligne.get(i)).trim();
        return v.isEmpty() ? null : v;
    }
}
