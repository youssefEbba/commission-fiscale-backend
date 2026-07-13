package mr.gov.finances.sgci.web.dto.provision;



import lombok.AllArgsConstructor;

import lombok.Builder;

import lombok.Data;

import lombok.NoArgsConstructor;



@Data

@NoArgsConstructor

@AllArgsConstructor

@Builder

public class ProvisionWorkflowStepDto {



    private String code;

    private String label;

    private String statut;

    private int ordre;

    private boolean termine;

}

