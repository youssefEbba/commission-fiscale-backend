package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mr.gov.finances.sgci.domain.enums.StatutCreditLotArchive;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un crédit d'un lot de reprise : le relevé lu dans le fichier, les choix de l'opérateur pour ce
 * que le fichier ne dit pas (entreprise, autorité, convention, marché), et le résultat de l'import.
 */
@Entity
@Table(name = "lot_import_archive_credit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LotImportArchiveCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private LotImportArchive lot;

    @Column(nullable = false)
    private Integer ordre;

    /** Place du bloc dans la feuille, en numéros de ligne Excel. */
    private Integer ligneDebut;
    private Integer ligneFin;

    private String numeroCredit;
    private String nif;
    @Column(length = 500)
    private String referenceMarche;
    private Instant dateCredit;

    @Column(precision = 18, scale = 2)
    private BigDecimal montantCreditImpot;
    @Column(precision = 18, scale = 2)
    private BigDecimal creditDouanier;
    @Column(precision = 18, scale = 2)
    private BigDecimal creditInterieur;
    @Column(precision = 18, scale = 2)
    private BigDecimal soldeDouanierCalcule;
    @Column(precision = 18, scale = 2)
    private BigDecimal soldeInterieurCalcule;
    @Column(precision = 18, scale = 2)
    private BigDecimal soldeDouanierDeclare;
    @Column(precision = 18, scale = 2)
    private BigDecimal soldeInterieurDeclare;
    private Integer nbUtilisations;
    private Integer nbAnomalies;

    /** Le relevé lu, sérialisé : c'est lui qui est importé, tel que l'opérateur l'a vu. */
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String releveJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id")
    private Entreprise entreprise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorite_contractante_id")
    private AutoriteContractante autoriteContractante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "convention_id")
    private Convention convention;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marche_id")
    private Marche marche;

    /** Sans marché choisi, l'import en crée un à partir du relevé. */
    @Builder.Default
    private Boolean creerMarche = Boolean.FALSE;

    @Builder.Default
    private Boolean confirmerMalgreAnomalies = Boolean.FALSE;

    /** D'où vient chaque choix : NIF, MARCHE, CODE_AC, CONVENTION_UNIQUE ou MANUEL. */
    @Column(length = 24)
    private String sourceEntreprise;
    @Column(length = 24)
    private String sourceAutorite;
    @Column(length = 24)
    private String sourceConvention;
    @Column(length = 24)
    private String sourceMarche;

    /** Ce que le service « Vérifier NIF » de la DGI a répondu pour ce NIF. */
    private String raisonSocialeDgi;
    private String nomCommercialDgi;
    @Column(length = 32)
    private String etatDgi;
    /** Vrai quand la DGI a été interrogée et ne connaît pas ce NIF. */
    private Boolean inconnuDgi;

    /** Un certificat « ARCHIVE-n° » existe déjà : l'import le complétera au lieu d'en créer un. */
    @Builder.Default
    private Boolean dejaRepris = Boolean.FALSE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private StatutCreditLotArchive statut;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id")
    private CertificatCredit certificat;

    @Column(length = 2000)
    private String messageErreur;
    private Instant dateImport;
}
