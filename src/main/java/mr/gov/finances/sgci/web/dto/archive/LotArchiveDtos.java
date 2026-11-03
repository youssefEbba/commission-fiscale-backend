package mr.gov.finances.sgci.web.dto.archive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Échanges de la reprise d'archive par fichier de période ({@code /api/archive/lots}). */
public final class LotArchiveDtos {

    private LotArchiveDtos() {
    }

    /** Un lot : en liste sans ses crédits, en détail avec. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LotDto {
        private Long id;
        private String nomFichier;
        private Instant dateAnalyse;
        private String analyseParNom;
        private int nbCredits;
        /** Nombre de crédits par statut (A_COMPLETER, PRET, IMPORTE, ERREUR, IGNORE). */
        private Map<String, Long> parStatut;
        /** NIF distincts sans entreprise retrouvée ni choisie, parmi les crédits non importés. */
        private int nifsSansEntreprise;
        /** NIF sans entreprise que la DGI n'a pas encore été interrogée sur : l'écran relance tant qu'il en reste. */
        private int nifsAInterrogerDgi;
        /** Vrai quand le fichier versé avait déjà été analysé : c'est le lot existant qui est rendu. */
        private boolean dejaAnalyse;
        private List<CreditDto> credits;
    }

    /** Un crédit du lot, sans ses utilisations. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditDto {
        private Long id;
        private int ordre;
        private Integer ligneDebut;
        private Integer ligneFin;
        private String numeroCredit;
        private String nif;
        private String referenceMarche;
        private String numeroMarche;
        private Instant dateMarche;
        private String dureeMarche;
        private Instant dateCredit;
        private BigDecimal montantMarche;
        private BigDecimal montantCreditImpot;
        private BigDecimal creditDouanier;
        private BigDecimal creditInterieur;
        private BigDecimal transfertCredit;
        /**
         * Éléments du calcul des soldes, pour l'expliquer à l'écran :
         * douanier = creditDouanier − totalUtilisationsDouane − sortieTransfert ;
         * intérieur = creditInterieur + transfertCredit − totalUtilisationsInterieur.
         */
        private BigDecimal totalUtilisationsDouane;
        private BigDecimal totalUtilisationsInterieur;
        /**
         * Ce que le transfert retire du crédit douanier : tout le reliquat ventilé dans « Détailles
         * transfert » (part TVA transférée + droits soldés), à défaut le « Transfert de crédit ».
         */
        private BigDecimal sortieTransfert;
        private BigDecimal soldeDouanierCalcule;
        private BigDecimal soldeInterieurCalcule;
        private BigDecimal soldeDouanierDeclare;
        private BigDecimal soldeInterieurDeclare;
        private int nbUtilisations;
        private List<String> anomalies;

        private Long entrepriseId;
        private String entrepriseRaisonSociale;
        private String sourceEntreprise;
        private Long autoriteContractanteId;
        private String autoriteContractanteNom;
        private String sourceAutorite;
        private Long conventionId;
        private String conventionIntitule;
        private String sourceConvention;
        private Long marcheId;
        private String marcheNumero;
        private String sourceMarche;
        private boolean creerMarche;
        private boolean confirmerMalgreAnomalies;

        private String raisonSocialeDgi;
        private String nomCommercialDgi;
        private String etatDgi;
        /** Vrai : la DGI a été interrogée et ne connaît pas ce NIF. */
        private boolean inconnuDgi;

        /** Un certificat « ARCHIVE-n° » existe déjà : il sera complété. */
        private boolean dejaRepris;
        private String statut;
        /** Ce qui manque pour être PRET : NUMERO, DATE, ENTREPRISE, AUTORITE, CONVENTION, MARCHE, ANOMALIES. */
        private List<String> manquants;

        private Long certificatId;
        private String certificatReference;
        private String messageErreur;
        private Instant dateImport;
    }

    /** Un crédit et son relevé complet (utilisations, lignes de taxe). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditDetailDto {
        private CreditDto credit;
        private ReleveArchiveDto releve;
    }

    /**
     * Affectation groupée : aux crédits listés, ou à tous les crédits non importés d'un NIF. Un champ
     * absent est laissé tel quel ; {@code creerMarche=true} retire le marché choisi.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AffectationRequest {
        private List<Long> creditIds;
        private String nif;
        private Long entrepriseId;
        private Long autoriteContractanteId;
        private Long conventionId;
        private Long marcheId;
        private Boolean creerMarche;
        private Boolean confirmerMalgreAnomalies;
        /** true : écarter les crédits ; false : les réintégrer. */
        private Boolean ignorer;
        /** N° du crédit, quand le fichier ne le donne pas : un seul crédit visé. */
        private String numeroCredit;
        /** Date du crédit (AAAA-MM-JJ), quand le fichier ne la donne pas : un seul crédit visé. */
        private LocalDate dateCredit;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportRequest {
        /** Crédits à importer ; absent, tous les crédits PRET du lot. */
        private List<Long> creditIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportResultatDto {
        private int importes;
        private int erreurs;
        /** Crédits demandés mais pas prêts, donc non tentés. */
        private int nonPrets;
        private List<CreditDto> credits;
    }
}
