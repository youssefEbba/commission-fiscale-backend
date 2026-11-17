package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.TypeAchat;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@DiscriminatorValue("TVA_INTERIEURE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UtilisationTVAInterieure extends UtilisationCredit {

    /** Remplace les numéros de facture ; le premier est recopié dans {@code numeroFacture}. */
    public void remplacerNumerosFacture(java.util.List<String> numeros) {
        numerosFacture.clear();
        numerosFacture.addAll(numeros);
        numeroFacture = numeros.isEmpty() ? null : numeros.get(0);
    }

    public java.util.List<String> numerosFactureEffectifs() {
        return NumerosJustificatifs.effectifs(numerosFacture, numeroFacture);
    }

    @Enumerated(EnumType.STRING)
    private TypeAchat typeAchat;
    /** Le premier des {@link #numerosFacture}, gardé pour les lecteurs d'un seul numéro. */
    private String numeroFacture;

    /** Tous les numéros de facture de l'utilisation, dans l'ordre de saisie. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "utilisation_numero_facture", joinColumns = @JoinColumn(name = "utilisation_id"))
    @OrderColumn(name = "ordre")
    @Column(name = "numero", length = 128)
    @org.hibernate.annotations.BatchSize(size = 64)
    private java.util.List<String> numerosFacture = new java.util.ArrayList<>();
    private Instant dateFacture;

    @Column(precision = 19, scale = 4)
    private BigDecimal montantTVA;

    private String numeroDecompte;

    @Column(precision = 19, scale = 4)
    private BigDecimal tvaDeductibleUtilisee;

    @Column(precision = 19, scale = 4)
    private BigDecimal tvaNette;

    @Column(precision = 19, scale = 4)
    private BigDecimal creditInterieurUtilise;

    @Column(precision = 19, scale = 4)
    private BigDecimal paiementEntreprise;

    @Column(precision = 19, scale = 4)
    private BigDecimal reportANouveau;

    @Column(precision = 19, scale = 4)
    private BigDecimal soldeTVAAvant;

    @Column(precision = 19, scale = 4)
    private BigDecimal soldeTVAApres;
}
