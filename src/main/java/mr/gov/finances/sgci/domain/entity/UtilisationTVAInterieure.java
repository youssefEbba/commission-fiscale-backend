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

    @Enumerated(EnumType.STRING)
    private TypeAchat typeAchat;
    private String numeroFacture;
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

    /** Numéro du certificat d'utilisation, attribué au moment de l'apurement ({@code CU-001/2026}). */
    @Column(name = "numero_certificat_utilisation", length = 40)
    private String numeroCertificatUtilisation;

    @Column(name = "date_certificat_utilisation")
    private Instant dateCertificatUtilisation;
}
