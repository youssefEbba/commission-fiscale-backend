package mr.gov.finances.sgci.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutDemandeRattachement;

import java.time.Instant;

/**
 * Ce qu'un demandeur peut lire sur sa propre demande de création de compte, sans être connecté.
 *
 * <p><b>Volontairement pauvre.</b> La référence est séquentielle — {@code RT-002-10/2026} suit
 * {@code RT-001-10/2026} — donc quiconque en connaît une peut deviner les autres. Si cette réponse
 * portait le nom, le courriel ou le téléphone du demandeur, il suffirait de compter pour constituer
 * la liste des personnes ayant demandé un compte, avec leur entité. C'est pourquoi la route exige
 * aussi le courriel, et pourquoi ce DTO ne rend rien que le demandeur ne sache déjà.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuiviRattachementDto {

    private String reference;

    private StatutDemandeRattachement statut;

    /** La qualité demandée : entreprise, autorité contractante, sous-traitant… */
    private Role roleDemande;

    /** L'entité visée : celle choisie dans le référentiel, ou le nom de celle à créer. */
    private String entiteNom;

    private Instant dateDepot;

    /** Renseignée dès que la demande a été acceptée ou refusée. */
    private Instant dateTraitement;

    /** Le motif, en cas de refus. Il est adressé au demandeur : c'est à lui qu'il est rendu. */
    private String motifRefus;
}
