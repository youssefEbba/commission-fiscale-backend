package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("DOUANIER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UtilisationDouaniere extends UtilisationCredit {

    private String numeroDeclaration;
    private String numeroBulletin;
    private Instant dateDeclaration;

    /** Lignes du bulletin saisies par l'entreprise, annotées par le DGD lors de la liquidation. */
    @OneToMany(mappedBy = "utilisationDouaniere", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("typeLigne ASC, id ASC")
    private List<LigneBulletinLiquidation> lignes = new ArrayList<>();

    /** Quittances Trésor enregistrées par le DGTCP. */
    @OneToMany(mappedBy = "utilisationDouaniere", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("dateQuittance ASC, id ASC")
    private List<QuittanceTresor> quittances = new ArrayList<>();

    /** Somme des lignes AU_CI – montant imputé au crédit d'impôt (débité du solde cordon). */
    @Column(precision = 19, scale = 4)
    private BigDecimal totalPrisEnCharge;

    /** Somme des lignes A_PAYER – montant restant à payer comptant par l'entreprise. */
    @Column(precision = 19, scale = 4)
    private BigDecimal totalAPayer;

    /**
     * Part TVA des lignes AU_CI (code "TVA") – utilisée pour créer le stock de TVA déductible.
     * Calculée automatiquement à la liquidation.
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal montantTVA;

    /**
     * Part hors-TVA des lignes AU_CI – conservée pour référence comptable.
     * Calculée automatiquement à la liquidation.
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal montantDroits;

    private Boolean enregistreeSYDONIA;

    /** Chèque certifié fourni par l'entreprise après visa DGD. */
    private String banqueNom;
    private String numeroCheque;
    @Column(precision = 19, scale = 4)
    private BigDecimal montantCheque;
    private Instant dateCheque;

    @Column(precision = 19, scale = 4)
    private BigDecimal soldeCordonAvant;

    @Column(precision = 19, scale = 4)
    private BigDecimal soldeCordonApres;
}
