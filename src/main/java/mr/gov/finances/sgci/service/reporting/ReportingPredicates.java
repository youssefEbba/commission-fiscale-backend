package mr.gov.finances.sgci.service.reporting;

import org.springframework.stereotype.Component;

/**
 * Les fragments {@code where} qui appliquent le périmètre d'un {@link ReportingScope}.
 *
 * <p>Déplacés tels quels depuis {@code ReportingService}, où ils étaient privés. Chaque bloc de
 * contenu les réutilise au lieu d'écrire les siens : le périmètre d'une entreprise ou d'une autorité
 * contractante ne doit avoir qu'une seule formulation, sinon le sixième bloc oubliera la jointure
 * des délégués que les cinq premiers portent.
 *
 * <p>Composant sans état : le fragment ne dépend que du scope reçu.
 */
@Component
public class ReportingPredicates {

    /**
     * Ferme un périmètre vide.
     *
     * <p>Ceinture et bretelles : {@link ReportingQueryRunner} court-circuite déjà un périmètre
     * fermé sans exécuter la requête. Ce fragment protège le cas d'un HQL assemblé hors du runner.
     */
    private static final String FERME = " and 1=0";

    /** Conjoints « and … » pour périmètre AC / entreprise / délégué (sans fenêtre temporelle). */
    public String demandeExtraFilters(ReportingScope scope, String alias) {
        StringBuilder sb = new StringBuilder();
        if (scope.autoriteContractanteId() != null) {
            sb.append(" and ").append(alias).append(".autoriteContractante.id = :scopeAc");
        }
        if (scope.entrepriseId() != null) {
            sb.append(" and ").append(alias).append(".entreprise.id = :scopeEnt");
        }
        if (scope.delegueUserId() != null) {
            sb.append(" and exists (select 1 from Marche m join m.delegues md where m.demandeCorrection = ")
                    .append(alias).append(" and md.delegue.id = :scopeDelegue)");
        }
        if (scope.perimetreVide()) {
            sb.append(FERME);
        }
        return sb.toString();
    }

    public String demande(String alias, ReportingScope scope, boolean filtreDate) {
        StringBuilder sb = new StringBuilder();
        if (filtreDate) {
            sb.append("coalesce(").append(alias).append(".dateDepot, ")
                    .append(alias).append(".dateCreation) between :from and :to");
        }
        String more = demandeExtraFilters(scope, alias);
        if (sb.isEmpty()) {
            // Retirer le « and » de tête : le fragment devient la clause entière.
            return more.isEmpty() ? "1=1" : more.substring(5);
        }
        return sb + more;
    }

    public String certificat(String alias, ReportingScope scope) {
        return certificat(alias, scope, true);
    }

    /**
     * Variante sans fenêtre temporelle, pour ce qui est un état courant et non un événement.
     *
     * <p>Un rejet temporaire encore ouvert depuis soixante-seize jours compte, même si la décision
     * qui l'a posé est antérieure à la période consultée. Le borner à la fenêtre reviendrait à
     * cacher les blocages les plus anciens, qui sont précisément ceux qu'il faut voir.
     */
    public String certificat(String alias, ReportingScope scope, boolean filtreDate) {
        StringBuilder sb = new StringBuilder();
        sb.append(filtreDate
                ? "(" + alias + ".dateEmission between :from and :to)"
                : "1=1");
        if (scope.entrepriseId() != null) {
            sb.append(" and ").append(alias).append(".entreprise.id = :scopeEnt");
        }
        if (scope.autoriteContractanteId() != null) {
            sb.append(" and exists (select 1 from DemandeCorrection d where d = ")
                    .append(alias).append(".demandeCorrection and d.autoriteContractante.id = :scopeAc)");
        }
        if (scope.delegueUserId() != null) {
            sb.append(" and exists (select 1 from DemandeCorrection d join d.marche m join m.delegues md ")
                    .append("where d = ").append(alias).append(".demandeCorrection ")
                    .append("and md.delegue.id = :scopeDelegue)");
        }
        if (scope.perimetreVide()) {
            sb.append(FERME);
        }
        return sb.toString();
    }

    public String utilisation(String alias, ReportingScope scope) {
        return utilisation(alias, scope, true);
    }

    /** Variante sans fenêtre temporelle. Voir {@link #certificat(String, ReportingScope, boolean)}. */
    public String utilisation(String alias, ReportingScope scope, boolean filtreDate) {
        StringBuilder sb = new StringBuilder();
        sb.append(filtreDate ? alias + ".dateDemande between :from and :to" : "1=1");
        if (scope.entrepriseId() != null) {
            sb.append(" and ").append(alias).append(".entreprise.id = :scopeEnt");
        }
        if (scope.autoriteContractanteId() != null) {
            sb.append(" and exists (select 1 from DemandeCorrection d where d = ")
                    .append(alias).append(".certificatCredit.demandeCorrection ")
                    .append("and d.autoriteContractante.id = :scopeAc)");
        }
        if (scope.delegueUserId() != null) {
            sb.append(" and exists (select 1 from DemandeCorrection d join d.marche m join m.delegues md ")
                    .append("where d = ").append(alias).append(".certificatCredit.demandeCorrection ")
                    .append("and md.delegue.id = :scopeDelegue)");
        }
        if (scope.perimetreVide()) {
            sb.append(FERME);
        }
        return sb.toString();
    }

    public String convention(String alias, ReportingScope scope) {
        StringBuilder sb = new StringBuilder();
        sb.append(alias).append(".dateCreation between :from and :to");
        if (scope.autoriteContractanteId() != null) {
            // Toute autorité reliée à la convention, et non plus la seule principale.
            sb.append(" and (").append(alias).append(".autoriteContractante.id = :scopeAc or exists (select 1 from ")
                    .append("Convention cx join cx.autorites ax where cx = ").append(alias)
                    .append(" and ax.id = :scopeAc))");
        }
        if (scope.entrepriseId() != null) {
            sb.append(" and exists (select 1 from DemandeCorrection d where d.convention = ")
                    .append(alias).append(" and d.entreprise.id = :scopeEnt)");
        }
        if (scope.delegueUserId() != null) {
            sb.append(" and exists (select 1 from DemandeCorrection d join d.marche m join m.delegues md ")
                    .append("where d.convention = ").append(alias)
                    .append(" and md.delegue.id = :scopeDelegue)");
        }
        if (scope.perimetreVide()) {
            sb.append(FERME);
        }
        return sb.toString();
    }

    public String referentiel(String alias, ReportingScope scope) {
        StringBuilder sb = new StringBuilder();
        sb.append(alias).append(".dateDepot between :from and :to");
        if (scope.autoriteContractanteId() != null) {
            sb.append(" and ").append(alias).append(".autoriteContractante.id = :scopeAc");
        }
        if (scope.entrepriseId() != null) {
            sb.append(" and exists (select 1 from DemandeCorrection d where d.convention = ")
                    .append(alias).append(".convention and d.entreprise.id = :scopeEnt)");
        }
        if (scope.delegueUserId() != null) {
            sb.append(" and exists (select 1 from DemandeCorrection d join d.marche m join m.delegues md ")
                    .append("where d.convention = ").append(alias).append(".convention ")
                    .append("and md.delegue.id = :scopeDelegue)");
        }
        if (scope.perimetreVide()) {
            sb.append(FERME);
        }
        return sb.toString();
    }

    /**
     * Périmètre d'un transfert, atteint par son certificat.
     *
     * <p>{@code TransfertCredit} ne porte qu'une date de demande et aucune date d'aboutissement :
     * sa durée d'instruction n'est pas calculable, et seule son ancienneté l'est.
     */
    public String transfert(String alias, ReportingScope scope, boolean filtreDate) {
        StringBuilder sb = new StringBuilder();
        sb.append(filtreDate ? alias + ".dateDemande between :from and :to" : "1=1");
        if (scope.entrepriseId() != null) {
            sb.append(" and ").append(alias).append(".certificatCredit.entreprise.id = :scopeEnt");
        }
        if (scope.autoriteContractanteId() != null) {
            sb.append(" and exists (select 1 from DemandeCorrection d where d = ")
                    .append(alias).append(".certificatCredit.demandeCorrection ")
                    .append("and d.autoriteContractante.id = :scopeAc)");
        }
        if (scope.delegueUserId() != null) {
            sb.append(" and exists (select 1 from DemandeCorrection d join d.marche m join m.delegues md ")
                    .append("where d = ").append(alias).append(".certificatCredit.demandeCorrection ")
                    .append("and md.delegue.id = :scopeDelegue)");
        }
        if (scope.perimetreVide()) {
            sb.append(FERME);
        }
        return sb.toString();
    }
}
