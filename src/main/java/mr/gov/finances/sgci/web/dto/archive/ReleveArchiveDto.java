package mr.gov.finances.sgci.web.dto.archive;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
     * <p>La reprise d'archive n'applique aucune correction : les soldes déclarés dans le relevé
     * sont importés tels quels.
     */
    private BigDecimal totalTransfertSortant;

    /** Solde tel qu'affiché dans le relevé — c'est celui retenu à l'import. */
    private BigDecimal soldeDouanierDeclare;
    private BigDecimal soldeInterieurDeclare;

    /** Solde recalculé depuis les utilisations, pour contrôle. */
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

    /** Renseigné si un import du même relevé a déjà été effectué. */
    private Long certificatDejaImporteId;
    private String certificatDejaImporteReference;
}
