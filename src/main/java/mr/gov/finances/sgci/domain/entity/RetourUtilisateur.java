package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mr.gov.finances.sgci.domain.enums.CategorieRetour;
import mr.gov.finances.sgci.domain.enums.ProfilFeedback;
import mr.gov.finances.sgci.domain.enums.Role;

import java.time.Instant;

/**
 * Un avis spontané d'usager : une note de 1 à 5, un sujet et un commentaire, à tout moment depuis
 * n'importe quel écran. Le profil est figé au dépôt, pour que le tableau de bord ne change pas
 * quand le rôle de la personne change.
 */
@Entity
@Table(name = "retour_utilisateur", indexes = @Index(name = "idx_retour_date", columnList = "dateCreation"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetourUtilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long utilisateurId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProfilFeedback profil;

    @Column(nullable = false)
    private Integer note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CategorieRetour categorie;

    /** Le processus concerné (CORRECTION, MISE_EN_PLACE, UTILISATION…), facultatif. */
    @Column(length = 64)
    private String processus;

    /** L'écran d'où l'avis a été donné, facultatif. */
    @Column(length = 255)
    private String page;

    @Column(length = 2000)
    private String commentaire;

    @Column(nullable = false)
    private Instant dateCreation;
}
