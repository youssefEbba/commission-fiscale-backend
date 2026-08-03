package mr.gov.finances.sgci.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntrepriseDto {

    private Long id;
    @NotBlank(message = "La raison sociale est obligatoire")
    private String raisonSociale;
    private String nomCommercial;
    private String activite;
    private String autre;
    private String nif;
    private String adresse;
    private String situationFiscale;

    /** {@code true} si l'entreprise est étrangère (NIF facultatif, registre de commerce étranger requis). */
    private boolean entrepriseEtrangere;
    /** Registre de commerce étranger (obligatoire si {@link #entrepriseEtrangere}). */
    private String registreCommerceEtranger;
}
