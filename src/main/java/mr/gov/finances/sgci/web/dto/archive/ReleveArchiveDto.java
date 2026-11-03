package mr.gov.finances.sgci.web.dto.archive;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contenu d'un relevé de crédit d'impôt de l'ancienne application, tel que lu dans le fichier.
 * Sert d'aperçu avant import : aucune écriture n'est faite tant que l'utilisateur n'a pas confirmé.
 */
@Data
public class ReleveArchiveDto {

    private String nomFichier;

    /** NIF tel qu'inscrit dans le relevé (cadrage des zéros de tête non garanti). */
    private String nif;
    private String referenceMarche;
    private String numeroCredit;
    private Instant dateCredit;
    private BigDecimal montantMarche;
    /** Numéro du marché : la référence sans sa date (« … DU 06/10/2025 »). */
    private String numeroMarche;
    private Instant dateMarche;
    /** Durée d'exécution telle qu'écrite dans le relevé (« 07 MOIS », « 30 JOURS », « IMMEDIAT »). */
    private String dureeMarche;

    private BigDecimal creditDouanier;
    private BigDecimal creditInterieur;
    private BigDecimal montantCreditImpot;
    /**
     * Seule part réellement transférée : la composante TVA du crédit douanier, qui vient abonder
     * le crédit intérieur.
     */
    private BigDecimal transfertCredit;
    /**
     * Totalité du reliquat douanier sorti du crédit, ventilée par taxe dans le bloc
     * « Détailles transfert » de l'ancienne application.
     *
     * <p>Attention au nom : ce total recouvre <b>deux opérations de nature différente</b> que
     * l'ancienne application regroupe à tort dans un même bloc —
     * la part TVA effectivement transférée ({@link #transfertCredit}), et les droits non
     * transférables (DD, RS, PC, PSC, TCO) qui sont simplement <b>mis à zéro</b>. La règle métier
     * est bien que seule la TVA se transfère ; le reste est soldé.
     *
     * <p>Il entre dans le recalcul du solde douanier ({@link #soldeDouanierCalcule}).
     */
    private BigDecimal totalTransfertSortant;

    /**
     * Solde intérieur selon la règle de l'ancienne application (crédit + transfert − utilisations) :
     * c'est lui qui se compare au solde affiché par le fichier. Le solde importé est
     * {@link #soldeInterieurCalcule}, rejoué selon les règles de l'application.
     */
    private BigDecimal soldeInterieurRegleFichier;

    /** Stock de TVA déductible reconstitué : TVA des importations et TVA transférée, avec leurs restes. */
    private List<StockTvaArchiveDto> stockTva;
    private BigDecimal stockTvaEntrees;
    private BigDecimal stockTvaDeduit;
    private BigDecimal stockTvaRestant;
    private BigDecimal paiementEntrepriseTotal;
    private BigDecimal reportsTotal;

    /** Part TVA du bloc « Détailles transfert » : ce qui est passé de la TVA d'importation au crédit intérieur. */
    private BigDecimal transfertSortantTva;

    /** Ligne « Totaux » : crédit douanier initial par taxe (DD, TVADD, RS, PC, PSC, TCO, TT). */
    private Map<String, BigDecimal> taxesInitiales;
    /** Ligne « Solde = » : soldes par taxe affichés par l'ancienne application, pour contrôle. */
    private Map<String, BigDecimal> taxesSoldeDeclare;

    /** Composantes de l'application : b = DD + RS + PC + PSC + TT, c = TCO, d = TVADD. */
    private BigDecimal droitsEtTaxesHorsTva;
    private BigDecimal taxesConsommation;
    private BigDecimal tvaImportation;
    /** Recalculés : TVA d'importation restante (d′) et solde des droits (b + c restants). d′ + droits = solde douanier. */
    private BigDecimal soldeTvaImportationCalcule;
    private BigDecimal soldeDroitsCalcule;

    /** Solde tel qu'affiché dans le relevé, gardé pour contrôle : tout écart avec le calcul est signalé. */
    private BigDecimal soldeDouanierDeclare;
    private BigDecimal soldeInterieurDeclare;

    /**
     * Solde recalculé depuis le relevé — c'est celui retenu à l'import :
     * douanier = crédit douanier − utilisations douanières − sortie par transfert ;
     * intérieur = crédit intérieur + transfert − utilisations intérieures.
     */
    private BigDecimal soldeDouanierCalcule;
    private BigDecimal soldeInterieurCalcule;

    private BigDecimal totalUtilisationsDouane;
    private BigDecimal totalUtilisationsInterieur;

    private List<UtilisationArchiveDto> utilisations = new ArrayList<>();

    /** Écarts et incohérences relevés à la lecture. N'empêchent pas l'import, mais doivent être vus. */
    private List<String> anomalies = new ArrayList<>();

    /**
     * Entités déjà présentes en base et rapprochées à partir du contenu du relevé.
     * Elles servent à <b>pré-sélectionner</b> les listes à l'écran : le NIF désigne l'entreprise,
     * la référence désigne le marché, et le marché retrouvé désigne à son tour l'autorité
     * contractante. L'opérateur reste libre de changer chaque choix.
     */
    private Long entrepriseRapprocheeId;
    private String entrepriseRapprocheeRaisonSociale;
    /** {@code NIF} si le rapprochement vient du NIF du relevé, {@code AUCUN} sinon. */
    private String entrepriseRapprocheeSource;

    private Long marcheRapprocheId;
    private String marcheRapprocheNumero;
    private String marcheRapprocheIntitule;

    private Long autoriteRapprocheeId;
    private String autoriteRapprocheeNom;
    /** {@code MARCHE} si l'autorité vient du marché retrouvé, {@code CODE_AC} si du code lu dans sa référence. */
    private String autoriteRapprocheeSource;

    /** Renseigné si un import du même relevé a déjà été effectué. */
    private Long certificatDejaImporteId;
    private String certificatDejaImporteReference;
}
