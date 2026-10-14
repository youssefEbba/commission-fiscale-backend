package mr.gov.finances.sgci.service.reporting;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Granularite;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.reporting.ProductionPeriodeDto;
import mr.gov.finances.sgci.web.dto.reporting.ProductionPointDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les volumes et les masses financières par période, avec l'évolution d'une période à l'autre.
 *
 * <p><b>Ce bloc ne mesure pas la même chose que l'engagement, et il ne doit pas prétendre le
 * contraire.</b> Un solde est un niveau : il n'a pas de mois. La consommation d'une période ne peut
 * donc se lire que sur les mouvements, alors que l'engagement la lit sur les soldes. Les deux
 * chiffres divergent sur tous les dossiers repris de l'ancienne application, dont les soldes ont été
 * importés sans mouvements. D'où deux noms distincts — {@code consommeMouvementsPeriode} ici,
 * {@code consommeSoldes} là-bas — et non un nom commun qui ferait croire à une incohérence.
 *
 * <p><b>La datation de la masse accordée est un choix contraint.</b> {@code CertificatCredit} n'a
 * pas de date de création : seule la date de mise en place, avec repli sur la date d'émission, est
 * disponible. C'est exactement la clé que retient déjà le journal des crédits, et la reprendre
 * telle quelle est ce qui garantit que les deux écrans ne se contrediront pas. La clé est publiée
 * dans la réponse plutôt que laissée à deviner.
 *
 * <p>La base n'est interrogée qu'en mois civils ; le repli en trimestres, le comblement des périodes
 * vides et le calcul des variations appartiennent à {@link CalendrierPeriodes}, qui n'a aucun accès
 * à la base et se vérifie donc par un test unitaire pur.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductionPeriodeService {

    private static final String CLE_DATATION =
            "Les montants accordés sont datés de la mise en place du crédit, à défaut de son "
                    + "émission — le certificat ne porte pas de date de création. C'est la même clé "
                    + "que le journal des crédits. Le consommé, lui, est daté de la liquidation du "
                    + "mouvement.";

    private final ReportingScopeFactory scopeFactory;
    private final ReportingPredicates predicates;
    private final ReportingQueryRunner runner;

    public ProductionPeriodeDto production(AuthenticatedUser auth, Instant from, Instant to,
                                           Long autoriteContractanteId, Long entrepriseId,
                                           Granularite granularite) {
        Granularite pas = granularite != null ? granularite : Granularite.MOIS;
        ReportingScope scope = scopeFactory.build(auth, from, to, autoriteContractanteId, entrepriseId);

        Map<String, Long> demandes = CalendrierPeriodes.replier(
                demandesParMois(scope), scope.from(), scope.to(), pas);
        Map<String, Long> certificats = CalendrierPeriodes.replier(
                certificatsParMois(scope), scope.from(), scope.to(), pas);
        Map<String, Long> utilisations = CalendrierPeriodes.replier(
                utilisationsParMois(scope), scope.from(), scope.to(), pas);
        Map<String, BigDecimal> masse = CalendrierPeriodes.replierMontants(
                masseAccordeeParMois(scope), scope.from(), scope.to(), pas);
        Map<String, BigDecimal> consomme = CalendrierPeriodes.replierMontants(
                consommeParMois(scope), scope.from(), scope.to(), pas);

        List<ProductionPointDto> points = new ArrayList<>();
        for (String periode : CalendrierPeriodes.periodes(scope.from(), scope.to(), pas)) {
            points.add(ProductionPointDto.builder()
                    .periode(periode)
                    .nbDemandes(demandes.getOrDefault(periode, 0L))
                    .nbCertificats(certificats.getOrDefault(periode, 0L))
                    .nbUtilisations(utilisations.getOrDefault(periode, 0L))
                    .masseAccordee(masse.getOrDefault(periode, BigDecimal.ZERO))
                    .consommeMouvementsPeriode(consomme.getOrDefault(periode, BigDecimal.ZERO))
                    .build());
        }

        // La période antérieure, de même durée, pour que la variation ait une référence.
        Instant[] precedente = CalendrierPeriodes.fenetrePrecedente(scope.from(), scope.to());
        ReportingScope scopePrecedent = scopeFactory.build(
                auth, precedente[0], precedente[1], autoriteContractanteId, entrepriseId);
        long precDemandes = somme(demandesParMois(scopePrecedent));
        long precCertificats = somme(certificatsParMois(scopePrecedent));
        BigDecimal precMasse = total(masseAccordeeParMois(scopePrecedent));

        long totalDemandes = demandes.values().stream().mapToLong(Long::longValue).sum();
        long totalCertificats = certificats.values().stream().mapToLong(Long::longValue).sum();
        long totalUtilisations = utilisations.values().stream().mapToLong(Long::longValue).sum();

        return ProductionPeriodeDto.builder()
                .granularite(pas)
                .points(points)
                .cleDatation(CLE_DATATION)
                .totalDemandes(totalDemandes)
                .totalCertificats(totalCertificats)
                .totalUtilisations(totalUtilisations)
                .totalMasseAccordee(total(masse))
                .totalConsommeMouvements(total(consomme))
                .precedentDemandes(precDemandes)
                .precedentCertificats(precCertificats)
                .precedentMasseAccordee(precMasse)
                .variationDemandesPct(CalendrierPeriodes.variation(totalDemandes, precDemandes))
                .variationCertificatsPct(CalendrierPeriodes.variation(totalCertificats, precCertificats))
                .variationMassePct(CalendrierPeriodes.variation(total(masse), precMasse))
                .filtersApplied(scope.filtersApplied())
                .build();
    }

    // ── les comptages, en mois civils ──────────────────────────────────────────────────────────

    private Map<String, Long> demandesParMois(ReportingScope scope) {
        return comptages("select extract(year from coalesce(d.dateDepot, d.dateCreation)), "
                + "extract(month from coalesce(d.dateDepot, d.dateCreation)), count(d) "
                + "from DemandeCorrection d where " + predicates.demande("d", scope, true)
                + " group by extract(year from coalesce(d.dateDepot, d.dateCreation)), "
                + "extract(month from coalesce(d.dateDepot, d.dateCreation))", scope);
    }

    private Map<String, Long> certificatsParMois(ReportingScope scope) {
        return comptages("select extract(year from c.dateEmission), "
                + "extract(month from c.dateEmission), count(c) "
                + "from CertificatCredit c where " + predicates.certificat("c", scope)
                + " group by extract(year from c.dateEmission), extract(month from c.dateEmission)",
                scope);
    }

    private Map<String, Long> utilisationsParMois(ReportingScope scope) {
        return comptages("select extract(year from u.dateDemande), "
                + "extract(month from u.dateDemande), count(u) "
                + "from UtilisationCredit u where " + predicates.utilisation("u", scope)
                + " group by extract(year from u.dateDemande), extract(month from u.dateDemande)",
                scope);
    }

    // ── les masses ─────────────────────────────────────────────────────────────────────────────

    /**
     * Le crédit accordé, daté de la mise en place avec repli sur l'émission.
     *
     * <p>La fenêtre porte sur la date d'émission, comme pour tout le reste du reporting, mais le
     * <em>rangement en mois</em> suit la mise en place : c'est elle qui dit quand le crédit a
     * réellement été ouvert.
     */
    private Map<String, BigDecimal> masseAccordeeParMois(ReportingScope scope) {
        return montants("select extract(year from coalesce(c.dateMiseEnPlace, c.dateEmission)), "
                + "extract(month from coalesce(c.dateMiseEnPlace, c.dateEmission)), "
                + "coalesce(sum(coalesce(c.montantCordon,0) + coalesce(c.montantTVAInterieure,0)),0) "
                + "from CertificatCredit c where " + predicates.certificat("c", scope)
                + " group by extract(year from coalesce(c.dateMiseEnPlace, c.dateEmission)), "
                + "extract(month from coalesce(c.dateMiseEnPlace, c.dateEmission))", scope);
    }

    /** Le consommé de la période, sur les mouvements liquidés — un solde n'a pas de mois. */
    private Map<String, BigDecimal> consommeParMois(ReportingScope scope) {
        Map<String, BigDecimal> cumul = new LinkedHashMap<>(montants(
                "select extract(year from ud.dateLiquidation), extract(month from ud.dateLiquidation), "
                        + "coalesce(sum(coalesce(ud.totalPrisEnCharge,0) - coalesce(ud.montantTVA,0)),0) "
                        + "from UtilisationDouaniere ud join ud.certificatCredit c "
                        + "where " + predicates.certificat("c", scope)
                        + " and ud.dateLiquidation is not null"
                        + " group by extract(year from ud.dateLiquidation), "
                        + "extract(month from ud.dateLiquidation)", scope));

        montants("select extract(year from uti.dateLiquidation), extract(month from uti.dateLiquidation), "
                + "coalesce(sum(uti.creditInterieurUtilise),0) "
                + "from UtilisationTVAInterieure uti join uti.certificatCredit c "
                + "where " + predicates.certificat("c", scope)
                + " and uti.dateLiquidation is not null"
                + " group by extract(year from uti.dateLiquidation), "
                + "extract(month from uti.dateLiquidation)", scope)
                .forEach((cle, valeur) -> cumul.merge(cle, valeur, BigDecimal::add));

        return cumul;
    }

    // ── exécution ──────────────────────────────────────────────────────────────────────────────

    private Map<String, Long> comptages(String hql, ReportingScope scope) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Object[] row : runner.lignes(hql, scope, Map.of())) {
            out.merge(cleDe(row), ((Number) row[2]).longValue(), Long::sum);
        }
        return out;
    }

    private Map<String, BigDecimal> montants(String hql, ReportingScope scope) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (Object[] row : runner.lignes(hql, scope, Map.of())) {
            out.merge(cleDe(row), montant(row[2]), BigDecimal::add);
        }
        return out;
    }

    private static String cleDe(Object[] row) {
        return CalendrierPeriodes.cleMois(
                ((Number) row[0]).intValue(), ((Number) row[1]).intValue());
    }

    private static long somme(Map<String, Long> m) {
        return m.values().stream().mapToLong(Long::longValue).sum();
    }

    private static BigDecimal total(Map<String, BigDecimal> m) {
        BigDecimal t = BigDecimal.ZERO;
        for (BigDecimal v : m.values()) {
            t = t.add(v != null ? v : BigDecimal.ZERO);
        }
        return t;
    }

    private static BigDecimal montant(Object v) {
        return v instanceof BigDecimal b ? b
                : v instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
    }
}
