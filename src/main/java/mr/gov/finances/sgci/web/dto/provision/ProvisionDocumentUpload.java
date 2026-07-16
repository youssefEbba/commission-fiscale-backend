package mr.gov.finances.sgci.web.dto.provision;



import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;

import lombok.Builder;

import lombok.Data;

import lombok.NoArgsConstructor;



/**

 * Référence document dans une requête multipart admin provision ({@code fileKey} → part fichier).

 */

@Data

@NoArgsConstructor

@AllArgsConstructor

@Builder

public class ProvisionDocumentUpload {



    @NotBlank

    private String codeDocument;



    /** Clé du fichier dans le formulaire multipart (ex. {@code offreCorrigee}). */

    @NotBlank

    private String fileKey;

}

