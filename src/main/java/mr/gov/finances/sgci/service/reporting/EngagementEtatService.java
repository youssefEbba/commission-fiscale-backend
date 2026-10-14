package mr.gov.finances.sgci.service.reporting;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.AxeEngagement;
import mr.gov.finances.sgci.domain.enums.FiabiliteAgregat;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.reporting.EngagementEtatDto;
import mr.gov.finances.sgci.web.dto.reporting.EngagementLigneDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combien l'État a engagé en crédit d'impôt, ce qui en a été consommé, ce qu'il en reste.
 *
 * <p><b>Trois enveloppes, et c'est le cœur du calcul.</b> L'intuition dit
 * {@code montantCordon − soldeCordon}, et elle a tort sur chaque dossier :
 * {@code applyOuvertureInitialisation} ouvre {@code soldeCordon} aux seuls droits et taxes hors TVA
 * (b + c), tandis que l'invariant du récapitulatif impose {@code montantCordon = b + c + d}. La
 * formule naïve affiche donc une consommation égale à la TVA à l'import accordée sur un certificat
 * ouvert et jamais utilisé. Les trois enveloppes suivies séparément sont :
 *
 * <pre>
 *   droits et taxes hors TVA : accordé = b + c                 restant = soldeCordon
 *   TVA à l'import           : accordé = d accordée            restant = quota d + Σ stock restant
 *   TVA intérieure           : accordé = montantTVAInterieure   restant = soldeTVA
 * </pre>
 *
 * <p>Le terme {@code Σ stock restant} n'est pas un raffinement : un transfert met le quota de TVA à
 * l'import à zéro et déverse le reliquat dans le stock de TVA déductible, et la liquidation
 * douanière fait de même. Sans ce terme, tout transfert afficherait 100 % de TVA à l'import
 * consommée. C'était le chiffre le plus sûrement faux de tout le bloc.
 *
 * <p><b>Les soldes sont la source de vérité, les mouvements servent de contrôle.</b> Le code le dit
 * lui-même — « ces soldes sont la source de vérité des UtilisationCredit » —, c'est sur eux que le
 * workflow refuse une utilisation, et c'est eux que l'import d'archive renseigne. Mesurer sur les
 * mouvements afficherait zéro consommé sur tout le stock repris de l'ancienne application. Les
 * mouvements sont donc recalculés en parallèle et <b>l'écart est publié</b>, jamais compensé ni
 * arrondi : c'est ce qui rend les deux autres chiffres croyables.
 *
 * <p>Noter que {@code UtilisationCredit.montant} ne sert pas à ce contrôle : la liquidation y écrit
 * {@code totalPrisEnCharge}, TVA comprise, alors qu'elle n'impute au cordon que
 * {@code totalPrisEnCharge − montantTVA}. Les bonnes colonnes sont celles des sous-classes.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngagementEtatService {

    /** Les dossiers sans demande de correction d'origine : ni convention, ni autorité, ni bailleur. */
    private static final String CLE_HORS_RATTACHEMENT = "HORS_RATTACHEMENT";

    /** La convention existe mais ne porte pas de bailleur. */
    private static final String CLE_SANS_VALEUR = "NON_RENSEIGNE";

    private static final String LIBELLE_HORS_RATTACHEMENT = "Hors rattachement (sans demande d'origine)";
    private static final String LIBELLE_SANS_VALEUR = "Non renseigné";

    /**
     * Les jointures, toutes à gauche.
     *
     * <p>Un parcours implicite — {@code c.demandeCorrection.autoriteContractante.id} — produit une
     * jointure interne en HQL, et ferait disparaître sans bruit les certificats sans demande
     * d'origine. Or ce sont précisément eux que la ligne « hors rattachement » doit montrer.
     */
    private static final String JOINTURES = """
             left join c.demandeCorrection d
             left join d.autoriteContractante ac
             left join d.convention conv
             left join conv.bailleur b
             left join c.entreprise ent
            """;

    private final ReportingScopeFactory scopeFactory;
    private final ReportingPredicates predicates;
    private final ReportingQueryRunner runner;

    public EngagementEtatDto parAxe(AuthenticatedUser auth, Instant from, Instant to,
                                    Long autoriteContractanteId, Long entrepriseId,
                                    AxeEngagement axe) {
        AxeEngagement axeRetenu = axe != null ? axe : AxeEngagement.AUTORITE_CONTRACTANTE;
        ReportingScope scope = scopeFactory.build(auth, from, to, autoriteContractanteId, entrepriseId);

        Map<String, EngagementLigneDto> parCle = soldesParAxe(scope, axeRetenu);
        appliquerStock(scope, axeRetenu, parCle);
        Map<String, BigDecimal> mouvements = mouvementsParAxe(scope, axeRetenu);
        Map<String, Long> causesConnues = causesConnuesParAxe(scope, axeRetenu);

        for (EngagementLigneDto ligne : parCle.values()) {
            finaliser(ligne, mouvements.get(ligne.getCle()), causesConnues.getOrDefault(ligne.getCle(), 0L));
        }

        List<EngagementLigneDto> lignes = new ArrayList<>(parCle.values());
        lignes.sort((x, y) -> nz(y.getAccordeTotal()).compareTo(nz(x.getAccordeTotal())));

        return EngagementEtatDto.builder()
                .axe(axeRetenu)
                .lignes(lignes)
                .total(totaliser(lignes))
                .ecartVentilation(ecartVentilation(scope))
                .nbEcartVentilation(nbEcartVentilation(scope))
                .ecartMouvements(sommeEcarts(lignes))
                .nbRestantSuperieurAccorde(nbRestantSuperieurAccorde(scope))
                .filtersApplied(scope.filtersApplied())
                .build();
    }

    // ── les soldes, source de vérité ───────────────────────────────────────────────────────────

    private Map<String, EngagementLigneDto> soldesParAxe(ReportingScope scope, AxeEngagement axe) {
        String hql = "select " + cleExpression(axe) + ", " + libelleExpression(axe) + ", "
                + discriminant() + ", count(c), "
                + "coalesce(sum(coalesce(c.droitsEtTaxesDouaneHorsTva,0) + coalesce(c.taxesConsommation,0)),0), "
                + "coalesce(sum(c.soldeCordon),0), "
                + "coalesce(sum(c.tvaImportationDouaneAccordee),0), "
                + "coalesce(sum(c.tvaImportationDouane),0), "
                + "coalesce(sum(c.montantTVAInterieure),0), "
                + "coalesce(sum(c.soldeTVA),0) "
                + "from CertificatCredit c " + JOINTURES
                + " where " + predicates.certificat("c", scope)
                + " group by " + cleExpression(axe) + ", " + libelleExpression(axe) + ", " + discriminant();

        Map<String, EngagementLigneDto> parCle = new LinkedHashMap<>();
        for (Object[] row : runner.lignes(hql, scope, Map.of())) {
            String cle = cleDe(row[0], (String) row[2]);
            String libelle = libelleDe(row[1], (String) row[2]);
            EngagementLigneDto ligne = parCle.computeIfAbsent(cle, k -> EngagementLigneDto.builder()
                    .cle(k)
                    .libelle(libelle)
                    .accordeCordonHorsTva(BigDecimal.ZERO)
                    .restantCordonHorsTva(BigDecimal.ZERO)
                    .accordeTvaImport(BigDecimal.ZERO)
                    .restantTvaImport(BigDecimal.ZERO)
                    .accordeTvaInterieure(BigDecimal.ZERO)
                    .restantTvaInterieure(BigDecimal.ZERO)
                    .build());
            ligne.setNbCertificats(ligne.getNbCertificats() + nombre(row[3]));
            ligne.setAccordeCordonHorsTva(nz(ligne.getAccordeCordonHorsTva()).add(montant(row[4])));
            ligne.setRestantCordonHorsTva(nz(ligne.getRestantCordonHorsTva()).add(montant(row[5])));
            ligne.setAccordeTvaImport(nz(ligne.getAccordeTvaImport()).add(montant(row[6])));
            ligne.setRestantTvaImport(nz(ligne.getRestantTvaImport()).add(montant(row[7])));
            ligne.setAccordeTvaInterieure(nz(ligne.getAccordeTvaInterieure()).add(montant(row[8])));
            ligne.setRestantTvaInterieure(nz(ligne.getRestantTvaInterieure()).add(montant(row[9])));
        }
        return parCle;
    }

    /** Le stock de TVA déductible, à réintégrer au restant de la TVA à l'import. */
    private void appliquerStock(ReportingScope scope, AxeEngagement axe,
                                Map<String, EngagementLigneDto> parCle) {
        String hql = "select " + cleExpression(axe) + ", " + discriminant() + ", "
                + "coalesce(sum(s.montantRestant),0) "
                + "from TvaDeductibleStock s join s.certificatCredit c " + JOINTURES
                + " where " + predicates.certificat("c", scope)
                + " group by " + cleExpression(axe) + ", " + discriminant();

        for (Object[] row : runner.lignes(hql, scope, Map.of())) {
            EngagementLigneDto ligne = parCle.get(cleDe(row[0], (String) row[1]));
            if (ligne != null) {
                ligne.setRestantTvaImport(nz(ligne.getRestantTvaImport()).add(montant(row[2])));
            }
        }
    }

    // ── les mouvements, contrôle ───────────────────────────────────────────────────────────────

    /**
     * Le consommé recalculé depuis les mouvements, sur les mêmes certificats.
     *
     * <p>Aucune fenêtre temporelle n'est appliquée aux mouvements : un solde est un niveau, pas un
     * flux. Borner les mouvements à la période tout en comparant à un solde cumulé produirait un
     * écart artificiel énorme. Le cadrage se fait par les certificats, pas par les mouvements.
     */
    private Map<String, BigDecimal> mouvementsParAxe(ReportingScope scope, AxeEngagement axe) {
        Map<String, BigDecimal> total = new LinkedHashMap<>();

        // Côté douane : l'imputation au cordon est le total pris en charge moins la part TVA.
        accumuler(total, "select " + cleExpression(axe) + ", " + discriminant() + ", "
                + "coalesce(sum(coalesce(ud.totalPrisEnCharge,0) - coalesce(ud.montantTVA,0)),0) "
                + "from UtilisationDouaniere ud join ud.certificatCredit c " + JOINTURES
                + " where " + predicates.certificat("c", scope)
                + " and ud.dateLiquidation is not null"
                + " group by " + cleExpression(axe) + ", " + discriminant(), scope);

        // Côté intérieur : la part réellement imputée au crédit intérieur.
        accumuler(total, "select " + cleExpression(axe) + ", " + discriminant() + ", "
                + "coalesce(sum(uti.creditInterieurUtilise),0) "
                + "from UtilisationTVAInterieure uti join uti.certificatCredit c " + JOINTURES
                + " where " + predicates.certificat("c", scope)
                + " and uti.dateLiquidation is not null"
                + " group by " + cleExpression(axe) + ", " + discriminant(), scope);

        return total;
    }

    private void accumuler(Map<String, BigDecimal> cumul, String hql, ReportingScope scope) {
        for (Object[] row : runner.lignes(hql, scope, Map.of())) {
            String cle = cleDe(row[0], (String) row[1]);
            cumul.merge(cle, montant(row[2]), BigDecimal::add);
        }
    }

    /**
     * Les dossiers portant une cause connue d'écart : reprise d'archive ou correction manuelle.
     *
     * <p>Sert à qualifier la ligne, pas à imputer l'écart. Un écart accompagné d'une cause connue se
     * lit {@code EXPLIQUE} ; sans aucune cause, {@code A_VERIFIER} — et c'est ce résidu, seul, qui
     * mérite l'attention de la Commission.
     */
    private Map<String, Long> causesConnuesParAxe(ReportingScope scope, AxeEngagement axe) {
        String hql = "select " + cleExpression(axe) + ", " + discriminant() + ", count(distinct c.id) "
                + "from CertificatCredit c " + JOINTURES
                + " where " + predicates.certificat("c", scope)
                + " and (exists (select 1 from UtilisationCredit u where u.certificatCredit = c"
                + "                and u.origineArchiveLibelle is not null)"
                + "   or exists (select 1 from AuditLog a where a.entityType = 'CertificatCredit'"
                + "                and a.action = mr.gov.finances.sgci.domain.enums.AuditAction.ADMIN_CORRECTION"
                + "                and a.entityId = str(c.id)))"
                + " group by " + cleExpression(axe) + ", " + discriminant();

        Map<String, Long> parCle = new LinkedHashMap<>();
        for (Object[] row : runner.lignes(hql, scope, Map.of())) {
            parCle.merge(cleDe(row[0], (String) row[1]), nombre(row[2]), Long::sum);
        }
        return parCle;
    }

    // ── les écarts globaux ─────────────────────────────────────────────────────────────────────

    private BigDecimal ecartVentilation(ReportingScope scope) {
        Object[] row = runner.uneLigne(
                "select coalesce(sum(c.montantCordon - (coalesce(c.droitsEtTaxesDouaneHorsTva,0)"
                        + " + coalesce(c.taxesConsommation,0) + coalesce(c.tvaImportationDouaneAccordee,0))),0)"
                        + ", count(c) from CertificatCredit c where " + predicates.certificat("c", scope)
                        + " and c.montantCordon is not null and c.montantCordon > 0",
                scope, Map.of());
        return row != null ? montant(row[0]) : BigDecimal.ZERO;
    }

    private long nbEcartVentilation(ReportingScope scope) {
        return runner.countSingle(
                "select count(c) from CertificatCredit c where " + predicates.certificat("c", scope)
                        + " and c.montantCordon is not null and c.montantCordon > 0"
                        + " and c.montantCordon <> coalesce(c.droitsEtTaxesDouaneHorsTva,0)"
                        + " + coalesce(c.taxesConsommation,0) + coalesce(c.tvaImportationDouaneAccordee,0)",
                scope, Map.of());
    }

    /**
     * Certificats dont un restant dépasse son accordé.
     *
     * <p>Le report à nouveau de TVA intérieure le permet légitimement. Compté, jamais écrêté : un
     * chiffre rogné en silence coûte plus cher qu'un chiffre surprenant.
     */
    private long nbRestantSuperieurAccorde(ReportingScope scope) {
        return runner.countSingle(
                "select count(c) from CertificatCredit c where " + predicates.certificat("c", scope)
                        + " and (coalesce(c.soldeTVA,0) > coalesce(c.montantTVAInterieure,0)"
                        + "   or coalesce(c.soldeCordon,0) > coalesce(c.droitsEtTaxesDouaneHorsTva,0)"
                        + "      + coalesce(c.taxesConsommation,0))",
                scope, Map.of());
    }

    // ── assemblage ─────────────────────────────────────────────────────────────────────────────

    private void finaliser(EngagementLigneDto ligne, BigDecimal mouvements, long causesConnues) {
        ligne.setConsommeCordonHorsTva(
                nz(ligne.getAccordeCordonHorsTva()).subtract(nz(ligne.getRestantCordonHorsTva())));
        ligne.setConsommeTvaImport(
                nz(ligne.getAccordeTvaImport()).subtract(nz(ligne.getRestantTvaImport())));
        ligne.setConsommeTvaInterieure(
                nz(ligne.getAccordeTvaInterieure()).subtract(nz(ligne.getRestantTvaInterieure())));

        ligne.setAccordeTotal(nz(ligne.getAccordeCordonHorsTva())
                .add(nz(ligne.getAccordeTvaImport()))
                .add(nz(ligne.getAccordeTvaInterieure())));
        ligne.setRestantTotal(nz(ligne.getRestantCordonHorsTva())
                .add(nz(ligne.getRestantTvaImport()))
                .add(nz(ligne.getRestantTvaInterieure())));
        ligne.setConsommeSoldes(nz(ligne.getAccordeTotal()).subtract(nz(ligne.getRestantTotal())));

        ligne.setConsommeMouvements(nz(mouvements));
        ligne.setEcartMouvements(nz(ligne.getConsommeSoldes()).subtract(nz(mouvements)));
        ligne.setNbDossiersRepriseOuCorriges(causesConnues);
        ligne.setFiabilite(qualifier(ligne.getEcartMouvements(), causesConnues));
    }

    private FiabiliteAgregat qualifier(BigDecimal ecart, long causesConnues) {
        if (nz(ecart).signum() == 0) {
            return FiabiliteAgregat.CONFORME;
        }
        return causesConnues > 0 ? FiabiliteAgregat.EXPLIQUE : FiabiliteAgregat.A_VERIFIER;
    }

    private EngagementLigneDto totaliser(List<EngagementLigneDto> lignes) {
        EngagementLigneDto total = EngagementLigneDto.builder()
                .cle("TOTAL")
                .libelle("Total")
                .accordeCordonHorsTva(BigDecimal.ZERO)
                .restantCordonHorsTva(BigDecimal.ZERO)
                .accordeTvaImport(BigDecimal.ZERO)
                .restantTvaImport(BigDecimal.ZERO)
                .accordeTvaInterieure(BigDecimal.ZERO)
                .restantTvaInterieure(BigDecimal.ZERO)
                .build();
        BigDecimal mouvements = BigDecimal.ZERO;
        long causes = 0;
        for (EngagementLigneDto l : lignes) {
            total.setNbCertificats(total.getNbCertificats() + l.getNbCertificats());
            total.setAccordeCordonHorsTva(nz(total.getAccordeCordonHorsTva()).add(nz(l.getAccordeCordonHorsTva())));
            total.setRestantCordonHorsTva(nz(total.getRestantCordonHorsTva()).add(nz(l.getRestantCordonHorsTva())));
            total.setAccordeTvaImport(nz(total.getAccordeTvaImport()).add(nz(l.getAccordeTvaImport())));
            total.setRestantTvaImport(nz(total.getRestantTvaImport()).add(nz(l.getRestantTvaImport())));
            total.setAccordeTvaInterieure(nz(total.getAccordeTvaInterieure()).add(nz(l.getAccordeTvaInterieure())));
            total.setRestantTvaInterieure(nz(total.getRestantTvaInterieure()).add(nz(l.getRestantTvaInterieure())));
            mouvements = mouvements.add(nz(l.getConsommeMouvements()));
            causes += l.getNbDossiersRepriseOuCorriges();
        }
        finaliser(total, mouvements, causes);
        return total;
    }

    private BigDecimal sommeEcarts(List<EngagementLigneDto> lignes) {
        BigDecimal somme = BigDecimal.ZERO;
        for (EngagementLigneDto l : lignes) {
            somme = somme.add(nz(l.getEcartMouvements()));
        }
        return somme;
    }

    // ── les axes ───────────────────────────────────────────────────────────────────────────────

    private String cleExpression(AxeEngagement axe) {
        return switch (axe) {
            case AUTORITE_CONTRACTANTE -> "ac.id";
            case BAILLEUR -> "b.id";
            case CONVENTION -> "conv.id";
            case ENTREPRISE -> "ent.id";
        };
    }

    private String libelleExpression(AxeEngagement axe) {
        return switch (axe) {
            case AUTORITE_CONTRACTANTE -> "ac.nom";
            case BAILLEUR -> "b.nom";
            case CONVENTION -> "conv.reference";
            case ENTREPRISE -> "ent.raisonSociale";
        };
    }

    /**
     * Sépare « hors rattachement » de « non renseigné » quand la clé de l'axe est nulle.
     *
     * <p>Sur l'axe bailleur, les deux cas existent et ne veulent pas dire la même chose : un
     * certificat sans demande d'origine n'a aucun rattachement, tandis qu'une convention peut
     * simplement ne pas porter de bailleur. Les fondre en une seule ligne mélangerait un défaut de
     * données et une absence légitime.
     */
    private String discriminant() {
        return "case when d.id is null then '" + CLE_HORS_RATTACHEMENT + "' else '" + CLE_SANS_VALEUR + "' end";
    }

    private String cleDe(Object cle, String discriminant) {
        if (cle != null) {
            return String.valueOf(cle);
        }
        return CLE_HORS_RATTACHEMENT.equals(discriminant) ? CLE_HORS_RATTACHEMENT : CLE_SANS_VALEUR;
    }

    private String libelleDe(Object libelle, String discriminant) {
        if (libelle != null) {
            return String.valueOf(libelle);
        }
        return CLE_HORS_RATTACHEMENT.equals(discriminant)
                ? LIBELLE_HORS_RATTACHEMENT : LIBELLE_SANS_VALEUR;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal montant(Object v) {
        return v instanceof BigDecimal b ? b
                : v instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
    }

    private static long nombre(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
