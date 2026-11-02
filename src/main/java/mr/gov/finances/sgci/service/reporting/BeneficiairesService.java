package mr.gov.finances.sgci.service.reporting;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.CategorieBailleur;
import mr.gov.finances.sgci.domain.enums.CategorieEntreprise;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.reporting.BeneficiairesDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Le recensement des bénéficiaires (cahier des charges, point 4).
 *
 * <p>Un bénéficiaire est l'entreprise titulaire — le chef de file pour un groupement — d'au moins un
 * crédit <b>mis en place</b> dans la période. Les crédits repris de l'archive y figurent : ce sont
 * des bénéficiaires réels, antérieurs à la plateforme. L'année fiscale est l'année civile de la mise
 * en place du crédit.
 *
 * <p>Sans borne de date, la période couvre tout l'historique : l'évolution par année n'a de sens
 * que sur plusieurs années, contrairement aux autres blocs du reporting (365 jours par défaut).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BeneficiairesService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final String NON_RENSEIGNE = "NON_RENSEIGNE";

    private static final List<String> DEFINITIONS = List.of(
            "Bénéficiaire : entreprise titulaire (chef de file d'un groupement) d'au moins un crédit mis en place dans la période, crédits repris de l'archive compris.",
            "Année fiscale : année civile de la mise en place du crédit.",
            "Montant accordé : enveloppe droits et taxes de douane (TVA à l'import comprise) + enveloppe TVA intérieure. Consommé : accordé moins les soldes restants du crédit.",
            "Financement : fonds nationaux si le marché n'a pas de convention ou si tous ses bailleurs sont nationaux ; partenaires extérieurs dès qu'un bailleur est multilatéral, bilatéral ou privé ; non renseigné sinon.",
            "Catégorie (PME / grande entreprise) et secteur : saisis sur la fiche de l'entreprise ; « non renseigné » tant qu'ils ne le sont pas.");

    private record Credit(long id, long entrepriseId, boolean etrangere, CategorieEntreprise categorie,
                          String secteur, BigDecimal accorde, BigDecimal consomme, int annee, String financement) {
    }

    private final ReportingScopeFactory scopeFactory;
    private final ReportingPredicates predicates;
    private final ReportingQueryRunner runner;

    public BeneficiairesDto beneficiaires(AuthenticatedUser auth, Instant from, Instant to,
                                          Long autoriteContractanteId, Long entrepriseId) {
        ReportingScope scope = scopeFactory.build(auth,
                from != null ? from : Instant.EPOCH, to != null ? to : Instant.now(),
                autoriteContractanteId, entrepriseId);
        List<Credit> credits = credits(scope);

        Set<Long> entreprises = new HashSet<>();
        BigDecimal accorde = BigDecimal.ZERO;
        BigDecimal consomme = BigDecimal.ZERO;
        for (Credit c : credits) {
            entreprises.add(c.entrepriseId());
            accorde = accorde.add(c.accorde());
            consomme = consomme.add(c.consomme());
        }

        return BeneficiairesDto.builder()
                .from(scope.from())
                .to(scope.to())
                .totalBeneficiaires(entreprises.size())
                .totalCredits(credits.size())
                .montantAccorde(accorde)
                .montantConsomme(consomme)
                .parNature(ventiler(credits, c -> c.etrangere() ? "ETRANGERE" : "NATIONALE",
                        Map.of("NATIONALE", "Entreprises nationales", "ETRANGERE", "Entreprises étrangères"),
                        List.of("NATIONALE", "ETRANGERE")))
                .parCategorie(ventiler(credits, c -> c.categorie().name(),
                        Map.of("PME", "PME", "GRANDE_ENTREPRISE", "Grandes entreprises",
                                "NON_RENSEIGNEE", "Non renseignée"),
                        List.of("PME", "GRANDE_ENTREPRISE", "NON_RENSEIGNEE")))
                .parSecteur(ventilerParSecteur(credits))
                .parFinancement(ventiler(credits, Credit::financement,
                        Map.of("FONDS_NATIONAUX", "Fonds nationaux", "PARTENAIRES_EXTERIEURS", "Partenaires extérieurs",
                                NON_RENSEIGNE, "Non renseigné"),
                        List.of("FONDS_NATIONAUX", "PARTENAIRES_EXTERIEURS", NON_RENSEIGNE)))
                .parAnnee(parAnnee(credits))
                .definitions(DEFINITIONS)
                .build();
    }

    private List<Credit> credits(ReportingScope scope) {
        Map<Long, Set<CategorieBailleur>> bailleursParConvention = bailleursParConvention(scope);
        String hql = "select c.id, e.id, e.entrepriseEtrangere, e.categorie, s.libelle,"
                + " c.montantCordon, c.montantTVAInterieure, c.soldeCordon, c.tvaImportationDouane, c.soldeTVA,"
                + " coalesce(c.dateMiseEnPlace, c.dateEmission), conv.id"
                + " from CertificatCredit c join c.entreprise e left join e.secteur s"
                + " left join c.demandeCorrection d left join d.convention conv"
                + " where c.dateMiseEnPlace is not null"
                + " and coalesce(c.dateMiseEnPlace, c.dateEmission) between :from and :to"
                + " and " + predicates.certificat("c", scope, false);
        List<Credit> out = new ArrayList<>();
        for (Object[] r : runner.lignes(hql, scope, Map.of())) {
            BigDecimal accorde = nz(r[5]).add(nz(r[6]));
            BigDecimal restant = nz(r[7]).add(nz(r[8])).add(nz(r[9]));
            BigDecimal consomme = accorde.subtract(restant).max(BigDecimal.ZERO);
            Long conventionId = r[11] != null ? ((Number) r[11]).longValue() : null;
            out.add(new Credit(((Number) r[0]).longValue(), ((Number) r[1]).longValue(),
                    Boolean.TRUE.equals(r[2]),
                    r[3] != null ? (CategorieEntreprise) r[3] : CategorieEntreprise.NON_RENSEIGNEE,
                    r[4] != null ? r[4].toString() : null,
                    accorde, consomme,
                    LocalDate.ofInstant((Instant) r[10], ZONE).getYear(),
                    financement(conventionId, bailleursParConvention)));
        }
        return out;
    }

    /** Les catégories des bailleurs de chaque convention : le principal et les bailleurs liés. */
    private Map<Long, Set<CategorieBailleur>> bailleursParConvention(ReportingScope scope) {
        Map<Long, Set<CategorieBailleur>> out = new HashMap<>();
        if (scope.perimetreVide()) {
            return out;
        }
        // Un référentiel, pas une donnée du périmètre : lu sans les filtres d'entité du cadrage.
        ReportingScope referentiel = new ReportingScope(scope.from(), scope.to(), null, null, null,
                scope.nationalView(), false, false, false);
        for (Object[] r : runner.lignes("select conv.id, b.categorie from Convention conv join conv.bailleur b",
                referentiel, Map.of())) {
            out.computeIfAbsent(((Number) r[0]).longValue(), k -> EnumSet.noneOf(CategorieBailleur.class))
                    .add(r[1] != null ? (CategorieBailleur) r[1] : CategorieBailleur.NON_RENSEIGNE);
        }
        for (Object[] r : runner.lignes("select conv.id, b.categorie from Convention conv join conv.bailleurs b",
                referentiel, Map.of())) {
            out.computeIfAbsent(((Number) r[0]).longValue(), k -> EnumSet.noneOf(CategorieBailleur.class))
                    .add(r[1] != null ? (CategorieBailleur) r[1] : CategorieBailleur.NON_RENSEIGNE);
        }
        return out;
    }

    /**
     * L'origine du financement d'un crédit. Un marché sans convention est réputé financé sur fonds
     * nationaux : la convention est l'instrument d'un financement extérieur.
     */
    static String financement(Long conventionId, Map<Long, Set<CategorieBailleur>> bailleursParConvention) {
        if (conventionId == null) {
            return "FONDS_NATIONAUX";
        }
        Set<CategorieBailleur> categories = bailleursParConvention.getOrDefault(conventionId, Set.of());
        if (categories.stream().anyMatch(c -> c == CategorieBailleur.MULTILATERAL
                || c == CategorieBailleur.BILATERAL || c == CategorieBailleur.PRIVE)) {
            return "PARTENAIRES_EXTERIEURS";
        }
        if (!categories.isEmpty() && categories.stream().allMatch(c -> c == CategorieBailleur.NATIONAL)) {
            return "FONDS_NATIONAUX";
        }
        return NON_RENSEIGNE;
    }

    private static List<BeneficiairesDto.Ventilation> ventiler(List<Credit> credits, Function<Credit, String> cle,
                                                              Map<String, String> libelles, List<String> ordre) {
        Map<String, List<Credit>> groupes = new LinkedHashMap<>();
        ordre.forEach(k -> groupes.put(k, new ArrayList<>()));
        credits.forEach(c -> groupes.computeIfAbsent(cle.apply(c), k -> new ArrayList<>()).add(c));
        List<BeneficiairesDto.Ventilation> out = new ArrayList<>();
        groupes.forEach((k, liste) -> out.add(ventilation(k, libelles.getOrDefault(k, k), liste)));
        return out;
    }

    private static List<BeneficiairesDto.Ventilation> ventilerParSecteur(List<Credit> credits) {
        Map<String, List<Credit>> groupes = new TreeMap<>();
        credits.forEach(c -> groupes.computeIfAbsent(c.secteur() != null ? c.secteur() : "", k -> new ArrayList<>()).add(c));
        List<BeneficiairesDto.Ventilation> out = new ArrayList<>();
        groupes.forEach((k, liste) -> out.add(ventilation(k.isEmpty() ? NON_RENSEIGNE : k,
                k.isEmpty() ? "Non renseigné" : k, liste)));
        out.sort(Comparator.comparingLong(BeneficiairesDto.Ventilation::getNbBeneficiaires).reversed());
        return out;
    }

    private static BeneficiairesDto.Ventilation ventilation(String cle, String libelle, List<Credit> liste) {
        return BeneficiairesDto.Ventilation.builder()
                .cle(cle)
                .libelle(libelle)
                .nbBeneficiaires(liste.stream().map(Credit::entrepriseId).distinct().count())
                .nbCredits(liste.size())
                .montantAccorde(liste.stream().map(Credit::accorde).reduce(BigDecimal.ZERO, BigDecimal::add))
                .montantConsomme(liste.stream().map(Credit::consomme).reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }

    private static List<BeneficiairesDto.Annee> parAnnee(List<Credit> credits) {
        Map<Long, Integer> premiereAnnee = new HashMap<>();
        for (Credit c : credits) {
            premiereAnnee.merge(c.entrepriseId(), c.annee(), Math::min);
        }
        Map<Integer, List<Credit>> parAnnee = new TreeMap<>();
        credits.forEach(c -> parAnnee.computeIfAbsent(c.annee(), k -> new ArrayList<>()).add(c));
        List<BeneficiairesDto.Annee> out = new ArrayList<>();
        parAnnee.forEach((annee, liste) -> out.add(BeneficiairesDto.Annee.builder()
                .annee(annee)
                .nouveauxBeneficiaires(premiereAnnee.values().stream().filter(a -> a.equals(annee)).count())
                .beneficiaires(liste.stream().map(Credit::entrepriseId).distinct().count())
                .nbCredits(liste.size())
                .montantAccorde(liste.stream().map(Credit::accorde).reduce(BigDecimal.ZERO, BigDecimal::add))
                .montantConsomme(liste.stream().map(Credit::consomme).reduce(BigDecimal.ZERO, BigDecimal::add))
                .build()));
        return out;
    }

    private static BigDecimal nz(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    }
}
