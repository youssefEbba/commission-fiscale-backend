package mr.gov.finances.sgci.service.reporting;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.reporting.AFermerDto;
import mr.gov.finances.sgci.web.dto.reporting.LigneAFermerDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ce qu'il faudrait fermer, et ce qui arrive à échéance.
 *
 * <p>Trois pièges sont traités ici parce que chacun produit un chiffre faux d'apparence crédible.
 *
 * <p><b>Le type de {@code Convention.dateFin}.</b> C'est un {@code LocalDate}, alors que toute la
 * fenêtre de reporting est en {@code Instant}. Lier l'un contre l'autre ne compare pas ce que l'on
 * croit et renvoie silencieusement les mauvaises lignes — le genre de défaut qui ne se voit qu'en
 * recoupant à la main. Ce bloc dérive donc ses propres bornes en dates civiles.
 *
 * <p><b>Les certificats sans date de validité.</b> La colonne est nullable, et un certificat sans
 * date n'échoit jamais : il ne sortirait d'aucun des deux autres seaux. Il est donc remonté dans
 * son propre seau, comme l'anomalie de données qu'il constitue.
 *
 * <p><b>La déclaration d'équipements.</b> Elle est portée par un {@code Boolean} nullable, où
 * {@code null} signifie « la question n'a jamais été posée ». Le nombre de dossiers non renseignés
 * est publié à côté du chiffre : sans ce dénominateur, « trois dossiers avec équipements » se lit
 * comme un inventaire complet alors que c'est trois sur neuf réponses.
 *
 * <p>Et une définition, assumée comme telle : aucune donnée ne porte la notion de <b>dormance</b>.
 * Le critère retenu — aucune utilisation ni décision depuis {@value #SEUIL_DORMANCE_JOURS} jours —
 * voyage avec le chiffre, pour que personne n'hérite d'un critère qu'il n'a pas validé.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AFermerService {

    /** Horizon par défaut d'une échéance « prochaine ». */
    public static final int HORIZON_ECHEANCE_JOURS = 60;

    /** Sans utilisation ni décision depuis six mois. */
    public static final int SEUIL_DORMANCE_JOURS = 180;

    private static final int MAX_LIGNES = 500;

    /** Les certificats encore vivants : un crédit clos ou annulé n'est pas « à fermer ». */
    private static final Set<StatutCertificat> VIVANTS = Set.of(
            StatutCertificat.OUVERT, StatutCertificat.MODIFIE);

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final ReportingScopeFactory scopeFactory;
    private final ReportingPredicates predicates;
    private final ReportingQueryRunner runner;

    public AFermerDto aFermer(AuthenticatedUser auth, Instant from, Instant to,
                              Long autoriteContractanteId, Long entrepriseId,
                              Integer horizonJours, Integer dormanceJours) {
        ReportingScope scope = scopeFactory.build(auth, from, to, autoriteContractanteId, entrepriseId);
        int horizon = horizonJours != null && horizonJours > 0 ? horizonJours : HORIZON_ECHEANCE_JOURS;
        int dormance = dormanceJours != null && dormanceJours > 0 ? dormanceJours : SEUIL_DORMANCE_JOURS;
        Instant maintenant = Instant.now();

        List<LigneAFermerDto> expires = certificatsExpiresAvecSolde(scope, maintenant);
        List<LigneAFermerDto> expirant = certificatsExpirantProchainement(scope, maintenant, horizon);
        List<LigneAFermerDto> sansDate = certificatsSansDateValidite(scope);
        List<LigneAFermerDto> conventions = conventionsAEcheance(scope, horizon);
        List<LigneAFermerDto> equipements = equipementsSurCreditOuvert(scope);
        List<LigneAFermerDto> dormants = creditsDormants(scope, maintenant, dormance);

        BigDecimal immobilise = BigDecimal.ZERO;
        for (LigneAFermerDto l : expires) {
            immobilise = immobilise.add(l.getRestantTotal() != null ? l.getRestantTotal() : BigDecimal.ZERO);
        }

        return AFermerDto.builder()
                .horizonEcheanceJours(horizon)
                .seuilDormanceJours(dormance)
                .definitionDormance("Crédit ouvert sans aucune utilisation ni décision depuis "
                        + dormance + " jours. Aucune donnée ne porte la notion de dormance : "
                        + "ce critère est un choix, à valider par la Commission.")
                .certificatsExpiresAvecSolde(tronquer(expires))
                .nbCertificatsExpiresAvecSolde(expires.size())
                .certificatsExpiresTronques(expires.size() > MAX_LIGNES)
                .montantImmobiliseExpires(immobilise)
                .certificatsExpirantProchainement(tronquer(expirant))
                .nbCertificatsExpirantProchainement(expirant.size())
                .certificatsSansDateValidite(tronquer(sansDate))
                .nbCertificatsSansDateValidite(sansDate.size())
                .conventionsAEcheance(tronquer(conventions))
                .nbConventionsAEcheance(conventions.size())
                .equipementsSurCreditOuvert(tronquer(equipements))
                .nbEquipementsSurCreditOuvert(equipements.size())
                .equipementsNonRenseignes(equipementsNonRenseignes(scope))
                .creditsDormants(tronquer(dormants))
                .nbCreditsDormants(dormants.size())
                .creditsDormantsTronques(dormants.size() > MAX_LIGNES)
                .filtersApplied(scope.filtersApplied())
                .build();
    }

    // ── les crédits ────────────────────────────────────────────────────────────────────────────

    private List<LigneAFermerDto> certificatsExpiresAvecSolde(ReportingScope scope, Instant maintenant) {
        String hql = selectCertificat()
                + " where " + predicates.certificat("c", scope, false)
                + "   and c.statut in (:vivants)"
                + "   and c.dateValidite is not null and c.dateValidite < :maintenant"
                + "   and (coalesce(c.soldeCordon,0) > 0 or coalesce(c.soldeTVA,0) > 0"
                + "        or coalesce(c.tvaImportationDouane,0) > 0)";
        return certificats(hql, scope, Map.of("vivants", VIVANTS, "maintenant", maintenant), maintenant);
    }

    private List<LigneAFermerDto> certificatsExpirantProchainement(ReportingScope scope,
                                                                   Instant maintenant, int horizon) {
        Instant limite = maintenant.plus(horizon, ChronoUnit.DAYS);
        String hql = selectCertificat()
                + " where " + predicates.certificat("c", scope, false)
                + "   and c.statut in (:vivants)"
                + "   and c.dateValidite is not null"
                + "   and c.dateValidite >= :maintenant and c.dateValidite <= :limite";
        return certificats(hql, scope,
                Map.of("vivants", VIVANTS, "maintenant", maintenant, "limite", limite), maintenant);
    }

    /** L'anomalie : sans date de validité, un certificat n'échoit jamais et n'alerte jamais. */
    private List<LigneAFermerDto> certificatsSansDateValidite(ReportingScope scope) {
        String hql = selectCertificat()
                + " where " + predicates.certificat("c", scope, false)
                + "   and c.statut in (:vivants) and c.dateValidite is null";
        return certificats(hql, scope, Map.of("vivants", VIVANTS), null);
    }

    private String selectCertificat() {
        return "select c.id, coalesce(c.reference, c.numero), c.statut, ent.raisonSociale, "
                + "c.dateValidite, coalesce(c.soldeCordon,0) + coalesce(c.soldeTVA,0) "
                + "  + coalesce(c.tvaImportationDouane,0) "
                + "from CertificatCredit c left join c.entreprise ent";
    }

    private List<LigneAFermerDto> certificats(String hql, ReportingScope scope,
                                              Map<String, Object> params, Instant maintenant) {
        List<LigneAFermerDto> out = new ArrayList<>();
        for (Object[] row : runner.lignesPaginees(hql, scope, params, 0, MAX_LIGNES + 1)) {
            Instant validite = (Instant) row[4];
            out.add(LigneAFermerDto.builder()
                    .type("CertificatCredit")
                    .id(nombre(row[0]))
                    .reference(texte(row[1]))
                    .statut(texte(row[2]))
                    .entrepriseRaisonSociale(texte(row[3]))
                    .dateValidite(validite)
                    .joursAvantEcheance(validite != null && maintenant != null
                            ? Duration.between(maintenant, validite).toDays() : null)
                    .restantTotal(montant(row[5]))
                    .build());
        }
        return out;
    }

    // ── les conventions ────────────────────────────────────────────────────────────────────────

    /**
     * Les conventions dont la date de fin approche.
     *
     * <p>Les bornes sont des {@code LocalDate} dérivées du scope, et non les {@code Instant} de la
     * fenêtre : {@code Convention.dateFin} est une date civile. Lier un instant contre elle
     * produirait une comparaison qui a l'air de marcher et qui renvoie les mauvaises lignes.
     */
    private List<LigneAFermerDto> conventionsAEcheance(ReportingScope scope, int horizon) {
        LocalDate aujourdhui = LocalDate.now(ZONE);
        LocalDate limite = aujourdhui.plusDays(horizon);
        String hql = "select conv.id, conv.reference, conv.statut, conv.dateFin "
                + "from Convention conv "
                + "where " + predicates.convention("conv", scope)
                + "   and conv.dateFin is not null and conv.dateFin <= :limiteDate"
                + "   and (conv.actif is null or conv.actif = true)";

        List<LigneAFermerDto> out = new ArrayList<>();
        for (Object[] row : runner.lignesPaginees(hql, scope, Map.of("limiteDate", limite),
                0, MAX_LIGNES + 1)) {
            LocalDate fin = (LocalDate) row[3];
            out.add(LigneAFermerDto.builder()
                    .type("Convention")
                    .id(nombre(row[0]))
                    .reference(texte(row[1]))
                    .statut(texte(row[2]))
                    .dateFin(fin)
                    .joursAvantEcheance(fin != null ? ChronoUnit.DAYS.between(aujourdhui, fin) : null)
                    .build());
        }
        return out;
    }

    // ── les équipements sous régime ────────────────────────────────────────────────────────────

    /**
     * Du matériel sous admission temporaire, et un crédit encore ouvert.
     *
     * <p>Le critère a été déplacé de la simple présence d'équipements vers le <b>régime</b> : c'est
     * l'admission temporaire qui crée une obligation de réexportation, donc un risque à la
     * clôture. Du matériel importé définitivement n'a rien à ressortir et encombrait la liste.
     */
    private List<LigneAFermerDto> equipementsSurCreditOuvert(ReportingScope scope) {
        String hql = "select c.id, coalesce(c.reference, c.numero), c.statut, ent.raisonSociale, "
                + "c.dateValidite "
                + "from CertificatCredit c left join c.entreprise ent join c.demandeCorrection d "
                + "where " + predicates.certificat("c", scope, false)
                + "   and c.statut in (:vivants) and d.admissionTemporaire = true";

        List<LigneAFermerDto> out = new ArrayList<>();
        for (Object[] row : runner.lignesPaginees(hql, scope, Map.of("vivants", VIVANTS),
                0, MAX_LIGNES + 1)) {
            out.add(LigneAFermerDto.builder()
                    .type("CertificatCredit")
                    .id(nombre(row[0]))
                    .reference(texte(row[1]))
                    .statut(texte(row[2]))
                    .entrepriseRaisonSociale(texte(row[3]))
                    .dateValidite((Instant) row[4])
                    .build());
        }
        return out;
    }

    /**
     * Le dénominateur, sans lequel le chiffre ci-dessus se lit comme un inventaire complet.
     *
     * <p>Compte les dossiers où le régime n'a jamais été renseigné. Il sera élevé au début, et
     * c'est l'information juste : la question est neuve, la plupart des dossiers ne l'ont pas reçue.
     */
    private long equipementsNonRenseignes(ReportingScope scope) {
        return runner.countSingle(
                "select count(d) from DemandeCorrection d where "
                        + predicates.demande("d", scope, true)
                        + " and d.admissionTemporaire is null",
                scope, Map.of());
    }

    // ── la dormance ────────────────────────────────────────────────────────────────────────────

    private List<LigneAFermerDto> creditsDormants(ReportingScope scope, Instant maintenant, int seuil) {
        Instant limite = maintenant.minus(seuil, ChronoUnit.DAYS);
        String hql = "select c.id, coalesce(c.reference, c.numero), c.statut, ent.raisonSociale, "
                + "c.dateValidite, coalesce(c.soldeCordon,0) + coalesce(c.soldeTVA,0) "
                + "  + coalesce(c.tvaImportationDouane,0), "
                + "greatest(coalesce((select max(u.dateDemande) from UtilisationCredit u "
                + "                     where u.certificatCredit = c), c.dateEmission), "
                + "         coalesce((select max(dec.dateDecision) from DecisionCertificatCredit dec "
                + "                     where dec.certificatCredit = c), c.dateEmission)) "
                + "from CertificatCredit c left join c.entreprise ent "
                + "where " + predicates.certificat("c", scope, false)
                + "   and c.statut in (:vivants)"
                + "   and (coalesce(c.soldeCordon,0) > 0 or coalesce(c.soldeTVA,0) > 0"
                + "        or coalesce(c.tvaImportationDouane,0) > 0)";

        List<LigneAFermerDto> out = new ArrayList<>();
        for (Object[] row : runner.lignes(hql, scope, Map.of("vivants", VIVANTS))) {
            Instant derniereActivite = (Instant) row[6];
            if (derniereActivite == null || derniereActivite.isAfter(limite)) {
                continue;
            }
            out.add(LigneAFermerDto.builder()
                    .type("CertificatCredit")
                    .id(nombre(row[0]))
                    .reference(texte(row[1]))
                    .statut(texte(row[2]))
                    .entrepriseRaisonSociale(texte(row[3]))
                    .dateValidite((Instant) row[4])
                    .restantTotal(montant(row[5]))
                    .joursSansActivite(Duration.between(derniereActivite, maintenant).toDays())
                    .build());
        }
        out.sort((x, y) -> Long.compare(
                y.getJoursSansActivite() != null ? y.getJoursSansActivite() : 0L,
                x.getJoursSansActivite() != null ? x.getJoursSansActivite() : 0L));
        return out;
    }

    // ── utilitaires ────────────────────────────────────────────────────────────────────────────

    private <T> List<T> tronquer(List<T> liste) {
        return liste.size() <= MAX_LIGNES ? liste : new ArrayList<>(liste.subList(0, MAX_LIGNES));
    }

    private static Long nombre(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    private static String texte(Object v) {
        return v != null ? v.toString() : null;
    }

    private static BigDecimal montant(Object v) {
        return v instanceof BigDecimal b ? b
                : v instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
    }
}
