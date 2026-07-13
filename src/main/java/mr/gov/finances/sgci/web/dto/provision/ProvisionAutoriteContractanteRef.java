package mr.gov.finances.sgci.web.dto.provision;



import jakarta.validation.Valid;

import lombok.AllArgsConstructor;

import lombok.Builder;

import lombok.Data;

import lombok.NoArgsConstructor;

import mr.gov.finances.sgci.web.dto.AutoriteContractanteDto;



@Data

@NoArgsConstructor

@AllArgsConstructor

@Builder

public class ProvisionAutoriteContractanteRef {



    private Long id;



    @Valid

    private AutoriteContractanteDto create;

}

