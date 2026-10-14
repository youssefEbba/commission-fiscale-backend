package mr.gov.finances.sgci.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.Granularite;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.reporting.CalendrierPeriodes;
import mr.gov.finances.sgci.service.reporting.ReportingPredicates;
import mr.gov.finances.sgci.service.reporting.ReportingQueryRunner;
import mr.gov.finances.sgci.service.reporting.ReportingScope;
import mr.gov.finances.sgci.service.reporting.ReportingScopeFactory;
import mr.gov.finances.sgci.web.dto.reporting.CertificatFinancialTotalsDto;
import mr.gov.finances.sgci.web.dto.reporting.NamedCountDto;
import mr.gov.finances.sgci.web.dto.reporting.ReportingAuditStatsDto;
import mr.gov.finances.sgci.web.dto.reporting.ReportingDemandeStatsDto;
import mr.gov.finances.sgci.web.dto.reporting.ReportingSummaryDto;
import mr.gov.finances.sgci.web.dto.reporting.TimeSeriesPointDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La synthèse multi-domaines et la série temporelle des demandes.
 *
 * <p>Le cadrage par rôle, les fragments de périmètre et l'exécution des requêtes vivent désormais
 * dans {@code service.reporting} : {@link ReportingScopeFactory}, {@link ReportingPredicates} et
 * {@link ReportingQueryRunner}. Ils y ont été extraits pour que les blocs de contenu ajoutés
 * ensuite partagent exactement le même périmètre — un périmètre recalculé par bloc est la façon la
 * plus sûre de faire fuir les chiffres d'un dossier qu'on n'a pas le droit de voir.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportingService {

    /** Réservé aux statistiques d'audit, seule partie à ne porter aucun périmètre métier. */
    @PersistenceContext
    private EntityManager entityManager;

    private final ReportingScopeFactory scopeFactory;
    private final ReportingPredicates predicates;
    private final ReportingQueryRunner runner;

    public ReportingSummaryDto getSummary(AuthenticatedUser auth, Instant from, Instant to,
                                          Long autoriteContractanteId, Long entrepriseId) {
        ReportingScope scope = scopeFactory.build(auth, from, to, autoriteContractanteId, entrepriseId);

        ReportingDemandeStatsDto demandeStats = buildDemandeStats(scope);
        List<NamedCountDto> certByStatut = runner.groupedCounts(
                "select c.statut, count(c) from CertificatCredit c where "
                        + predicates.certificat("c", scope)
                        + " group by c.statut", scope);
        long certTotal = certByStatut.stream().mapToLong(NamedCountDto::getCount).sum();
        long certPres = runner.countSingle(
                "select count(c) from CertificatCredit c where c.statut = :st and "
                        + predicates.certificat("c", scope),
                scope, Map.of("st", StatutCertificat.EN_VALIDATION_PRESIDENT));

        List<NamedCountDto> utilByStatut = runner.groupedCounts(
                "select u.statut, count(u) from UtilisationCredit u where "
                        + predicates.utilisation("u", scope)
                        + " group by u.statut", scope);
        List<NamedCountDto> utilByType = runner.groupedCounts(
                "select u.type, count(u) from UtilisationCredit u where "
                        + predicates.utilisation("u", scope)
                        + " group by u.type", scope);
        long utilTotal = utilByStatut.stream().mapToLong(NamedCountDto::getCount).sum();

        List<NamedCountDto> convByStatut = runner.groupedCounts(
                "select conv.statut, count(conv) from Convention conv where "
                        + predicates.convention("conv", scope)
                        + " group by conv.statut", scope);

        List<NamedCountDto> refByStatut = runner.groupedCounts(
                "select rp.statut, count(rp) from ReferentielProjet rp where "
                        + predicates.referentiel("rp", scope)
                        + " group by rp.statut", scope);

        List<NamedCountDto> marcheByStatut = runner.groupedCounts(
                "select m.statut, count(m) from Marche m join m.demandeCorrection d where "
                        + predicates.demande("d", scope, true)
                        + " group by m.statut", scope);

        long transferts = runner.countSingle(
                "select count(c) from CertificatCredit c where c.transfertCredit is not null and "
                        + predicates.certificat("c", scope), scope, Map.of());
        long soustrait = runner.countSingle(
                "select count(c) from CertificatCredit c where c.sousTraitance is not null and "
                        + predicates.certificat("c", scope), scope, Map.of());

        ReportingAuditStatsDto audit = scope.includeNationalAudit()
                ? buildAuditStats(scope.from(), scope.to())
                : ReportingAuditStatsDto.builder()
                .byAction(List.of())
                .topEntityTypes(List.of())
                .totalActions(0)
                .build();

        CertificatFinancialTotalsDto financials = buildCertFinancials(scope);

        return ReportingSummaryDto.builder()
                .demandes(demandeStats)
                .certificatsByStatut(certByStatut)
                .certificatsTotal(certTotal)
                .certificatsEnValidationPresident(certPres)
                .utilisationsByStatut(utilByStatut)
                .utilisationsByType(utilByType)
                .utilisationsTotal(utilTotal)
                .conventionsByStatut(convByStatut)
                .referentielsByStatut(refByStatut)
                .marchesByStatut(marcheByStatut)
                .transfertsTotal(transferts)
                .sousTraitancesTotal(soustrait)
                .audit(audit)
                .certificatFinancials(financials)
                .filtersApplied(scope.filtersApplied())
                .build();
    }

    public List<TimeSeriesPointDto> getDemandeTimeseries(AuthenticatedUser auth, Instant from, Instant to,
                                                         Long autoriteContractanteId, Long entrepriseId) {
        ReportingScope scope = scopeFactory.build(auth, from, to, autoriteContractanteId, entrepriseId);
        String hql = """
                select extract(year from coalesce(d.dateDepot, d.dateCreation)),
                       extract(month from coalesce(d.dateDepot, d.dateCreation)),
                       count(d)
                from DemandeCorrection d
                where coalesce(d.dateDepot, d.dateCreation) between :from and :to
                """ + predicates.demandeExtraFilters(scope, "d") + """
                 group by extract(year from coalesce(d.dateDepot, d.dateCreation)),
                          extract(month from coalesce(d.dateDepot, d.dateCreation))
                 order by 1, 2
                """;
        Map<String, Long> parMois = new LinkedHashMap<>();
        for (Object[] row : runner.lignes(hql, scope, Map.of())) {
            int y = ((Number) row[0]).intValue();
            int m = ((Number) row[1]).intValue();
            parMois.merge(CalendrierPeriodes.cleMois(y, m), ((Number) row[2]).longValue(), Long::sum);
        }
        // Les mois sans demande étaient simplement absents : la courbe reliait alors deux mois
        // distants en ligne droite, et l'arrêt qui séparait disparaissait de l'écran.
        Map<String, Long> comble = CalendrierPeriodes.replier(
                parMois, scope.from(), scope.to(), Granularite.MOIS);

        List<TimeSeriesPointDto> out = new ArrayList<>();
        comble.forEach((periode, compte) -> out.add(
                TimeSeriesPointDto.builder().period(periode).count(compte).build()));
        out.sort(Comparator.comparing(TimeSeriesPointDto::getPeriod));
        return out;
    }

    private ReportingDemandeStatsDto buildDemandeStats(ReportingScope scope) {
        List<NamedCountDto> byStatut = runner.groupedCounts(
                "select d.statut, count(d) from DemandeCorrection d where "
                        + predicates.demande("d", scope, true)
                        + " group by d.statut", scope);
        long total = byStatut.stream().mapToLong(NamedCountDto::getCount).sum();
        long adop = countForStatutDemande(scope, StatutDemande.ADOPTEE)
                + countForStatutDemande(scope, StatutDemande.NOTIFIEE);
        long rej = countForStatutDemande(scope, StatutDemande.REJETEE);
        long ann = countForStatutDemande(scope, StatutDemande.ANNULEE);
        long base = total - ann;
        Double tauxAdopt = base > 0
                ? BigDecimal.valueOf(100.0 * adop / base).setScale(2, RoundingMode.HALF_UP).doubleValue()
                : null;
        Double tauxRej = base > 0
                ? BigDecimal.valueOf(100.0 * rej / base).setScale(2, RoundingMode.HALF_UP).doubleValue()
                : null;
        return ReportingDemandeStatsDto.builder()
                .byStatut(byStatut)
                .total(total)
                .tauxAdoptionPct(tauxAdopt)
                .tauxRejetPct(tauxRej)
                .build();
    }

    private long countForStatutDemande(ReportingScope scope, StatutDemande statut) {
        return runner.countSingle(
                "select count(d) from DemandeCorrection d where d.statut = :st and "
                        + predicates.demande("d", scope, true),
                scope, Map.of("st", statut));
    }

    private CertificatFinancialTotalsDto buildCertFinancials(ReportingScope scope) {
        String hql = """
                select coalesce(sum(c.montantCordon),0), coalesce(sum(c.montantTVAInterieure),0),
                       coalesce(sum(c.soldeCordon),0), coalesce(sum(c.soldeTVA),0), count(c)
                from CertificatCredit c where
                """ + predicates.certificat("c", scope);
        Object[] row = runner.uneLigne(hql, scope, Map.of());
        if (row == null) {
            // Périmètre fermé : des zéros, et non les totaux nationaux.
            return CertificatFinancialTotalsDto.builder()
                    .sumMontantCordon(BigDecimal.ZERO)
                    .sumMontantTvaInterieure(BigDecimal.ZERO)
                    .sumSoldeCordon(BigDecimal.ZERO)
                    .sumSoldeTva(BigDecimal.ZERO)
                    .certificatCount(0)
                    .build();
        }
        return CertificatFinancialTotalsDto.builder()
                .sumMontantCordon((BigDecimal) row[0])
                .sumMontantTvaInterieure((BigDecimal) row[1])
                .sumSoldeCordon((BigDecimal) row[2])
                .sumSoldeTva((BigDecimal) row[3])
                .certificatCount(((Number) row[4]).longValue())
                .build();
    }

    /**
     * Statistiques du journal d'audit, réservées aux rôles nationaux.
     *
     * <p>Seule partie du reporting à ne porter aucun périmètre métier : l'audit n'est filtré que par
     * date. C'est pourquoi elle n'emprunte ni les prédicats ni le runner, et garde son accès direct
     * à l'{@code EntityManager}.
     */
    private ReportingAuditStatsDto buildAuditStats(Instant from, Instant to) {
        Query q1 = entityManager.createQuery(
                "select a.action, count(a) from AuditLog a where a.timestamp between :from and :to group by a.action order by 1");
        q1.setParameter("from", from);
        q1.setParameter("to", to);
        @SuppressWarnings("unchecked")
        List<Object[]> rows1 = q1.getResultList();
        List<NamedCountDto> byAction = new ArrayList<>();
        long total = 0;
        for (Object[] row : rows1) {
            long c = ((Number) row[1]).longValue();
            total += c;
            byAction.add(NamedCountDto.builder().key(row[0].toString()).count(c).build());
        }

        Query q2 = entityManager.createQuery(
                "select a.entityType, count(a) from AuditLog a where a.timestamp between :from and :to "
                        + "group by a.entityType order by count(a) desc");
        q2.setParameter("from", from);
        q2.setParameter("to", to);
        q2.setMaxResults(10);
        @SuppressWarnings("unchecked")
        List<Object[]> rows2 = q2.getResultList();
        List<NamedCountDto> top = new ArrayList<>();
        for (Object[] row : rows2) {
            top.add(NamedCountDto.builder()
                    .key(row[0] != null ? row[0].toString() : "NULL")
                    .count(((Number) row[1]).longValue())
                    .build());
        }

        return ReportingAuditStatsDto.builder()
                .byAction(byAction)
                .topEntityTypes(top)
                .totalActions(total)
                .build();
    }
}
