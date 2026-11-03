package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Référentiel des secteurs d'activité des entreprises bénéficiaires.
 *
 * <p>Le champ libre {@code Entreprise.activite} ne permettait aucun regroupement (orthographes
 * multiples) : le secteur est désormais choisi dans cette liste, administrable sans livraison.
 */
@Entity
@Table(name = "secteur_activite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecteurActivite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String libelle;

    private String libelleAr;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;
}
