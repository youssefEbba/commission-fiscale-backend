package mr.gov.finances.sgci.service.archive;

import mr.gov.finances.sgci.web.dto.archive.VerificationNifDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Lecture des réponses du service « Vérifier NIF » de la DGI, sur un serveur simulé. */
class DgiNifClientTest {

    private static final String URL = "https://dgi.test/DGI/verifier_NIF";

    private MockRestServiceServer serveur;
    private DgiNifClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        serveur = MockRestServiceServer.bindTo(builder).build();
        client = new DgiNifClient(builder.build(), URL);
    }

    @Test
    void unNifConnuDonneLaRaisonSocialeEtLeNomCommercial() {
        serveur.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("s_nif", "20602865")))
                .andRespond(withSuccess("{\"itemList\":[[\"HABITAT, CONSTRUCTION DES BATIMENT ET TRAVAUX PUBLICS - SARL\","
                        + "\"HCB - TP - SARL\",\"2\"]],\"itemList_date\":[\"02\\/11\\/2026 20:11:12\"]}",
                        MediaType.APPLICATION_JSON));

        VerificationNifDto dto = client.verifier(" 20602865 ");

        assertThat(dto.isTrouve()).isTrue();
        assertThat(dto.getRaisonSociale()).isEqualTo("HABITAT, CONSTRUCTION DES BATIMENT ET TRAVAUX PUBLICS - SARL");
        assertThat(dto.getNomCommercial()).isEqualTo("HCB - TP - SARL");
        assertThat(dto.getEtat()).isEqualTo("2");
        assertThat(dto.getDateMiseAJour()).isEqualTo("02/11/2026 20:11:12");
        serveur.verify();
    }

    @Test
    void unNifInconnuRendUneListeVide() {
        serveur.expect(requestTo(URL))
                .andRespond(withSuccess("{\"itemList\":[],\"itemList_date\":[\"02\\/11\\/2026 20:11:12\"]}",
                        MediaType.APPLICATION_JSON));

        VerificationNifDto dto = client.verifier("00000001");

        assertThat(dto.isTrouve()).isFalse();
        assertThat(dto.getRaisonSociale()).isNull();
    }

    @Test
    void uneDgiEnPanneDonne503() {
        serveur.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.verifier("20602865"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(503);
                    assertThat(e.getCode()).isEqualTo(ApiErrorCode.DGI_SERVICE_UNAVAILABLE);
                });
    }
}
