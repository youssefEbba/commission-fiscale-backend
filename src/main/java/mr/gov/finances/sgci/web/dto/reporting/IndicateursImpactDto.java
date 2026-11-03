package mr.gov.finances.sgci.web.dto.reporting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Les indicateurs d'impact de la digitalisation : volumes, délais par phase, taux de décision,
 * montants et utilisation effective des crédits, comparés à la période d'avant la bascule.
 *
 * <p>Un indicateur sans donnée vaut {@code null}, jamais zéro : un délai moyen « 0 » sur aucun
 * dossier se lirait comme une performance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicateursImpactDto {

    private Instant from;
    private Instant to;

    /** Par processus : déposés, traités, taux de validation, de rejet et de compléments. */
    private List<Processus> processus;

    /** Délai moyen et médian de chaque phase, par processus. */
    private List<Phase> phases;

    private Montants montants;

    private TauxUtilisation tauxUtilisation;

    /** Date de bascule vers la plateforme ; {@code null} si elle n'est pas renseignée. */
    private LocalDate dateBascule;

    private List<Comparaison> avantApres;

    /** Ce que chaque chiffre mesure, en clair : à reprendre tel quel dans les rapports. */
    private List<String> definitions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Processus {
        /** CORRECTION, MISE_EN_PLACE, UTILISATION_TVA, UTILISATION_DOUANE, TRANSFERT, AVENANT. */
        private String code;
        private String libelle;
        private long deposes;
        /** Dossiers arrivés à une issue (validé ou rejeté). */
        private long traites;
        private long valides;
        private long rejetes;
        /** Dossiers ayant reçu au moins une demande de compléments (rejet temporaire). */
        private long avecComplements;
        private Double tauxValidationPct;
        private Double tauxRejetPct;
        private Double tauxComplementsPct;
        /** Délai moyen de réponse de l'usager à une demande de compléments, en jours. */
        private Double delaiReponseComplementsJours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Phase {
        private String processus;
        private String phase;
        private long nbDossiers;
        private Double moyenneJours;
        private Double medianeJours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Montants {
        /** Crédits demandés par les autorités contractantes (déclarés au dépôt). */
        private BigDecimal demandes;
        /** Crédits retenus par la Commission sur les demandes adoptées. */
        private BigDecimal retenus;
        /** Crédits mis en place (ouverts) : enveloppes droits et taxes + TVA intérieure. */
        private BigDecimal valides;
        /** Crédits consommés, mesurés sur les soldes des certificats. */
        private BigDecimal consommes;
        /** Variation nette des enveloppes par les avenants appliqués. */
        private BigDecimal ajustes;
        /** Soldes abandonnés par annulation du crédit. */
        private BigDecimal annules;
        /** Soldes restants à la clôture des crédits. */
        private BigDecimal clotures;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TauxUtilisation {
        private BigDecimal accordeTvaInterieure;
        private BigDecimal consommeTvaInterieure;
        private Double tvaInterieurePct;
        private BigDecimal accordeDouane;
        private BigDecimal consommeDouane;
        /** Droits et taxes hors TVA et TVA à l'import, ensemble : l'enveloppe des Douanes. */
        private Double douanePct;
        private Double globalPct;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Comparaison {
        private String code;
        private String libelle;
        private String unite;
        private BigDecimal avant;
        private BigDecimal apres;
        private Double variationPct;
        /** D'où vient la valeur d'avant : « archive reprise » ou « valeur de référence saisie ». */
        private String sourceAvant;
        private String periodeAvant;
    }
}
