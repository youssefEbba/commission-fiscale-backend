package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.ContexteExplication;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutExplication;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeExplicationDto {

    private Long id;
    private ContexteExplication contexte;
    private Long dossierId;
    private Role roleDestinataire;
    private String messageInitial;
    private StatutExplication statut;
    private Long auteurId;
    private String auteurNom;
    private Role roleAuteur;
    private Instant dateOuverture;
    private Instant dateFermeture;
    private List<DemandeExplicationMessageDto> messages;
}
