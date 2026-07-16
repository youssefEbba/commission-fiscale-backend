package mr.gov.finances.sgci.web.dto.provision;



import jakarta.validation.Valid;

import lombok.AllArgsConstructor;

import lombok.Builder;

import lombok.Data;

import lombok.NoArgsConstructor;

import mr.gov.finances.sgci.web.dto.CreateMarcheRequest;



@Data

@NoArgsConstructor

@AllArgsConstructor

@Builder

public class ProvisionMarcheRef {



    private Long id;



    @Valid

    private CreateMarcheRequest create;

}

