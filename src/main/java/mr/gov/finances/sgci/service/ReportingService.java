package mr.gov.finances.sgci.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.security.EffectiveIdentityService;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportingService {

    @PersistenceContext
    private EntityManager entityManager;
    private final UtilisateurRepository utilisateurRepository;
    private final EffectiveIdentityService effectiveIdentityService;

    private record Scope(Instant from, Instant to, Long autoriteContractanteId, Long entrepriseId,
                         Long delegueUserId, boolean nationalView, boolean filtersApplied,
                         boolean includeNationalAudit) {
    }

    public ReportingSummaryDto getSummary(AuthenticatedUser auth, Instant from, Instant to,
                                          Long autoriteContractanteId, Long entrepriseId) {
        Utilisateur u = utilisateurRepository.findById(auth.getUserId())
                .orElseThrow(() -> new IllegalStateException("Utilisateur non trouvé"));
        Scope scope = buildScope(auth, u, from, to, autoriteContractanteId, entrepriseId);

        ReportingDemandeStatsDto demandeStats = buildDemandeStats(scope);
        List<NamedCountDto> certByStatut = groupedCounts(
                "select c.statut, count(c) from CertificatCredit c where "
                        + certificatWhere("c", scope)
                        + " group by c.statut", scope);
        long certTotal = certByStatut.stream().mapToLong(NamedCountDto::getCount).sum();
        long certPres = countSingle(
                "select count(c) from CertificatCredit c where c.statut = :st and "
                        + certificatWhere("c", scope),
                scope, Map.of("st", StatutCertificat.EN_VALIDATION_PRESIDENT));

        List<NamedCountDto> utilByStatut = groupedCounts(
                "select u.statut, count(u) from UtilisationCredit u where "
                        + utilisationWhere("u", scope)
                        + " group by u.statut", scope);
        List<NamedCountDto> utilByType = groupedCounts(
                "select u.type, count(u) from UtilisationCredit u where "
                        + utilisationWhere("u", scope)
                        + " group by u.type", scope);
        long utilTotal = utilByStatut.stream().mapToLong(NamedCountDto::getCount).sum();

        List<NamedCountDto> convByStatut = groupedCounts(
                "select conv.statut, count(conv) from Convention conv where "
                        + conventionWhere("conv", scope)
                        + " group by conv.statut", scope);

        List<NamedCountDto> refByStatut = groupedCounts(
                "select rp.statut, count(rp) from ReferentielProjet rp where "
                        + referentielWhere("rp", scope)
                        + " group by rp.statut", scope);

        List<NamedCountDto> marcheByStatut = groupedCounts(
                "select m.statut, count(m) from Marche m join m.demandeCorrection d where "
                        + demandeWhere("d", scope, true)
                        + " group by m.statut", scope);

        long transferts = countSingle(
                "select count(c) from CertificatCredit c where c.transfertCredit is not null and "
                        + certificatWhere("c", scope), scope, Map.of());
        long soustrait = countSingle(
                "select count(c) from CertificatCredit c where c.sousTraitance is not null and "
                        + certificatWhere("c", scope), scope, Map.of());

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
        Utilisateur u = utilisateurRepository.findById(auth.getUserId())
                .orElseThrow(() -> new IllegalStateException("Utilisateur non trouvé"));
        Scope scope = buildScope(auth, u, from, to, autoriteContractanteId, entrepriseId);
        String hql = """
                select extract(year from coalesce(d.dateDepot, d.dateCreation)),
                       extract(month from coalesce(d.dateDepot, d.dateCreation)),
                       count(d)
                from DemandeCorrection d
                where coalesce(d.dateDepot, d.dateCreation) between :from and :to
                """ + demandeExtraFilters(scope, "d") + """
                 group by extract(year from coalesce(d.dateDepot, d.dateCreation)),
                          extract(month from coalesce(d.dateDepot, d.dateCreation))
                 order by 1, 2
                """;
        Query q = entityManager.createQuery(hql);
        bindScopeParams(q, scope);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<TimeSeriesPointDto> out = new ArrayList<>();
        for (Object[] row : rows) {
            int y = ((Number) row[0]).intValue();
            int m = ((Number) row[1]).intValue();
            long c = ((Number) row[2]).longValue();
            String period = String.format("%04d-%02d", y, m);
            out.add(TimeSeriesPointDto.builder().period(period).count(c).build());
        }
        out.sort(Comparator.comparing(TimeSeriesPointDto::getPeriod));
        return out;
    }

    private Scope buildScope(AuthenticatedUser auth, Utilisateur u, Instant from, Instant to,
                            Long reqAc, Long reqEnt) {
        Instant t0 = to != null ? to : Instant.now();
        Instant f0 = from != null ? from : t0.minus(365, ChronoUnit.DAYS);
        Role role = auth.getRole();
        boolean national = role == Role.PRESIDENT || role == Role.ADMIN_SI || role == Role.DGD
                || role == Role.DGTCP || role == Role.DGI || role == Role.DGB;
        boolean auditNational = national;
        Long ac = null;
        Long ent = null;
        Long delegueId = null;
        boolean filtersApplied = false;

        if (national) {
            ac = reqAc;
            ent = reqEnt;
            filtersApplied = reqAc != null || reqEnt != null;
        } else if (role == Role.ENTREPRISE || role == Role.SOUS_TRAITANT) {
            ent = effectiveIdentityService.resolveEntrepriseId(auth, u);
        } else if (role == Role.AUTORITE_CONTRACTANTE || role == Role.AUTORITE_UPM
                || role == Role.AUTORITE_UEP) {
            ac = effectiveIdentityService.resolveAutoriteContractanteId(auth, u);
            if (ac == null) {
                throw new IllegalStateException("Autorité contractante requise pour ce rôle");
            }
            if (role == Role.AUTORITE_UPM || role == Role.AUTORITE_UEP) {
                delegueId = u.getId();
            }
        } else {
            ent = effectiveIdentityService.resolveEntrepriseId(auth, u);
            ac = effectiveIdentityService.resolveAutoriteContractanteId(auth, u);
        }

        return new Scope(f0, t0, ac, ent, delegueId, national, filtersApplied, auditNational);
    }

    private ReportingDemandeStatsDto buildDemandeStats(Scope scope) {
        List<NamedCountDto> byStatut = groupedCounts(
                "select d.statut, count(d) from DemandeCorrection d where "
                        + demandeWhere("d", scope, true)
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

    private long countForStatutDemande(Scope scope, StatutDemande statut) {
        return countSingle(
                "select count(d) from DemandeCorrection d where d.statut = :st and "
                        + demandeWhere("d", scope, true),
                scope, Map.of("st", statut));
    }

    /** Conjoints « and … » pour périmètre AC / entreprise / délégué (sans fenêtre temporelle). */
    private String demandeExtraFilters(Scope scope, String alias) {
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
        return sb.toString();
    }

    private String demandeWhere(String alias, Scope scope, boolean dateFilter) {
        StringBuilder sb = new StringBuilder();
        if (dateFilter) {
            sb.append("coalesce(").append(alias).append(".dateDepot, ")
                    .append(alias).append(".dateCreation) between :from and :to");
        }
        String more = demandeExtraFilters(scope, alias);
        if (sb.isEmpty()) {
            return more.isEmpty() ? "1=1" : more.substring(5);
        }
        return sb + more;
    }

    private String certificatWhere(String alias, Scope scope) {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(alias).append(".dateEmission between :from and :to)");
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
        return sb.toString();
    }

    private String utilisationWhere(String alias, Scope scope) {
        StringBuilder sb = new StringBuilder();
        sb.append(alias).append(".dateDemande between :from and :to");
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
        return sb.toString();
    }

    private String conventionWhere(String alias, Scope scope) {
        StringBuilder sb = new StringBuilder();
        sb.append(alias).append(".dateCreation between :from and :to");
        if (scope.autoriteContractanteId() != null) {
            sb.append(" and ").append(alias).append(".autoriteContractante.id = :scopeAc");
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
        return sb.toString();
    }

    private String referentielWhere(String alias, Scope scope) {
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
        return sb.toString();
    }

    private void bindScopeParams(Query q, Scope scope) {
        q.setParameter("from", scope.from());
        q.setParameter("to", scope.to());
        if (scope.autoriteContractanteId() != null) {
            q.setParameter("scopeAc", scope.autoriteContractanteId());
        }
        if (scope.entrepriseId() != null) {
            q.setParameter("scopeEnt", scope.entrepriseId());
        }
        if (scope.delegueUserId() != null) {
            q.setParameter("scopeDelegue", scope.delegueUserId());
        }
    }

    private long countSingle(String hql, Scope scope, Map<String, Object> extra) {
        Query q = entityManager.createQuery(hql);
        bindScopeParams(q, scope);
        extra.forEach(q::setParameter);
        return ((Number) q.getSingleResult()).longValue();
    }

    private List<NamedCountDto> groupedCounts(String hql, Scope scope) {
        Query q = entityManager.createQuery(hql);
        bindScopeParams(q, scope);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<NamedCountDto> out = new ArrayList<>();
        for (Object[] row : rows) {
            String key = row[0] != null ? row[0].toString() : "NULL";
            long c = ((Number) row[1]).longValue();
            out.add(NamedCountDto.builder().key(key).count(c).build());
        }
        out.sort(Comparator.comparing(NamedCountDto::getKey));
        return out;
    }

    private CertificatFinancialTotalsDto buildCertFinancials(Scope scope) {
        String hql = """
                select coalesce(sum(c.montantCordon),0), coalesce(sum(c.montantTVAInterieure),0),
                       coalesce(sum(c.soldeCordon),0), coalesce(sum(c.soldeTVA),0), count(c)
                from CertificatCredit c where
                """ + certificatWhere("c", scope);
        Query q = entityManager.createQuery(hql);
        bindScopeParams(q, scope);
        Object[] row = (Object[]) q.getSingleResult();
        return CertificatFinancialTotalsDto.builder()
                .sumMontantCordon((BigDecimal) row[0])
                .sumMontantTvaInterieure((BigDecimal) row[1])
                .sumSoldeCordon((BigDecimal) row[2])
                .sumSoldeTva((BigDecimal) row[3])
                .certificatCount(((Number) row[4]).longValue())
                .build();
    }

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
