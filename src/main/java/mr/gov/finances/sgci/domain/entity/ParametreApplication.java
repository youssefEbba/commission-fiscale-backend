package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Un réglage de l'application, modifiable sans redéploiement.
 *
 * <p>Créée pour les coordonnées de contact affichées en pied de page : elles étaient écrites en dur
 * dans l'écran, donc tout changement de numéro ou d'adresse exigeait une livraison. Le besoin est
 * général, d'où une table clé-valeur plutôt qu'une colonne par réglage.
 *
 * <p><b>Le référentiel des clés vit dans le code</b>, pas en base : {@code ParametreService} refuse
 * une clé qu'il ne connaît pas. Sans cette garde, la table deviendrait un dépotoir où n'importe
 * quel appel pourrait écrire n'importe quoi, et plus personne ne saurait ce qui est lu ni par qui.
 */
@Entity
@Table(name = "parametre_application")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParametreApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String cle;

    /** 512 caractères : large pour une adresse ou un texte court, borné pour rester un réglage. */
    @Column(length = 512)
    private String valeur;

    /** Ce que le réglage signifie, pour l'agent qui le modifie sans connaître la clé. */
    @Column(length = 255)
    private String description;

    private Instant dateModification;

    @PreUpdate
    protected void onUpdate() {
        dateModification = Instant.now();
    }
}
