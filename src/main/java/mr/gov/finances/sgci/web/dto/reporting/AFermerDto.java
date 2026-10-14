package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Ce qu'il faudrait fermer, et ce qui arrive à échéance.
 *
 * <p>Chaque liste est accompagnée de son total et d'un drapeau de troncature : une liste coupée en
 * silence se lit comme une liste complète, et fait conclure que le travail est fait.
 *
 * <p>Trois pièges sont traités explicitement ici, parce que chacun produit un chiffre faux
 * d'apparence crédible :
 * <ul>
 *   <li>{@link #certificatsSansDateValidite} — la date est nullable, et un certificat sans date
 *       n'échoit jamais. Ces dossiers sont une anomalie à remonter, pas un vide à écarter.</li>
 *   <li>{@link #equipementsNonRenseignes} — la déclaration d'équipements est un booléen nullable, et
 *       {@code null} signifie « la question n'a jamais été posée ». Sans ce dénominateur,
 *       « trois dossiers avec équipements » se lit comme un inventaire complet.</li>
 *   <li>{@link #definitionDormance} — aucune donnée ne porte la notion de dormance : la définition
 *       est un choix, et elle voyage avec le chiffre pour que personne n'hérite d'un critère qu'il
 *       n'a pas validé.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AFermerDto {

    private int horizonEcheanceJours;

    private int seuilDormanceJours;

    /** La définition retenue de la dormance, en clair, pour l'écran et pour les exports. */
    private String definitionDormance;

    // ── crédits à solder ───────────────────────────────────────────────────────────────────────

    @Builder.Default
    private List<LigneAFermerDto> certificatsExpiresAvecSolde = new ArrayList<>();
    private long nbCertificatsExpiresAvecSolde;
    private boolean certificatsExpiresTronques;

    /** Ce que ces dossiers immobilisent : le montant qui justifie de s'en occuper. */
    private BigDecimal montantImmobiliseExpires;

    @Builder.Default
    private List<LigneAFermerDto> certificatsExpirantProchainement = new ArrayList<>();
    private long nbCertificatsExpirantProchainement;

    /** Anomalie de données : un certificat sans date de validité n'échoit jamais. */
    @Builder.Default
    private List<LigneAFermerDto> certificatsSansDateValidite = new ArrayList<>();
    private long nbCertificatsSansDateValidite;

    // ── conventions ────────────────────────────────────────────────────────────────────────────

    @Builder.Default
    private List<LigneAFermerDto> conventionsAEcheance = new ArrayList<>();
    private long nbConventionsAEcheance;

    // ── équipements sous régime ────────────────────────────────────────────────────────────────

    @Builder.Default
    private List<LigneAFermerDto> equipementsSurCreditOuvert = new ArrayList<>();
    private long nbEquipementsSurCreditOuvert;

    /** Dossiers où la question des équipements n'a jamais été posée. Le dénominateur. */
    private long equipementsNonRenseignes;

    // ── dormance ───────────────────────────────────────────────────────────────────────────────

    @Builder.Default
    private List<LigneAFermerDto> creditsDormants = new ArrayList<>();
    private long nbCreditsDormants;
    private boolean creditsDormantsTronques;

    private boolean filtersApplied;
}
