package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Un dossier que la Commission devrait regarder avant de le laisser dormir. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneAFermerDto {

    private String type;

    private Long id;

    private String reference;

    private String statut;

    private String entrepriseRaisonSociale;

    /** Pour un certificat : sa date de validité. {@code null} est une anomalie, pas un vide. */
    private Instant dateValidite;

    /** Pour une convention : sa date de fin, qui est une date civile et non un instant. */
    private LocalDate dateFin;

    /** Négatif si l'échéance est dépassée. */
    private Long joursAvantEcheance;

    /** Ce qui reste immobilisé sur le dossier. */
    private BigDecimal restantTotal;

    /** Jours écoulés depuis la dernière activité, pour les dossiers dormants. */
    private Long joursSansActivite;
}
