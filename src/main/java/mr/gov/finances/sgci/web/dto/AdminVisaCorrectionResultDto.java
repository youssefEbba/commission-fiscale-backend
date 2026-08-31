package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Résultat d'un visa posé par l'administrateur système à la place d'un membre de la commission. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminVisaCorrectionResultDto {

    /** Demande après application du visa (statut éventuellement avancé). */
    private DemandeCorrectionDto demande;

    /** Visa créé ({@code null} lorsque l'administrateur a adopté à la place du Président). */
    private DecisionCorrectionDto decision;

    /** Document déposé par l'administrateur avec le visa ({@code null} si aucun envoi). */
    private DocumentDto document;

    /** État de tous les visas après l'opération. */
    @Builder.Default
    private List<VisaCorrectionStatutDto> visas = new ArrayList<>();
}
