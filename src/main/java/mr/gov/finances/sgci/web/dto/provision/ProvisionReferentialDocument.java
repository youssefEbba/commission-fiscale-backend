package mr.gov.finances.sgci.web.dto.provision;



import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;

import lombok.Builder;

import lombok.Data;

import lombok.NoArgsConstructor;



/**

 * Document référentiel (convention, etc.) dans une requête multipart admin provision.

 */

@Data

@NoArgsConstructor

@AllArgsConstructor

@Builder

public class ProvisionReferentialDocument {



    @NotBlank

    private String codeDocument;



    @NotBlank

    private String fileKey;

}

