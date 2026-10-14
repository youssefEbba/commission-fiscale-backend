package mr.gov.finances.sgci.service.reporting;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.reporting.BlocagesDto;
import mr.gov.finances.sgci.web.dto.reporting.DelaiDirectionDto;
import mr.gov.finances.sgci.web.dto.reporting.DossierImmobileDto;
import mr.gov.finances.sgci.web.dto.reporting.RejetTempOuvertDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Où les dossiers sont arrêtés, depuis combien de temps, et ce que chaque direction met à viser.
 *
 * <p><b>L'étiquette ne doit pas mentir.</b> « Âge dans le statut actuel » n'est calculable pour
 * aucun dossier : rien n'enregistre quand le statut courant a été pris, {@code dateModification} est
 * réécrite par chaque mise à jour qu'elle touche au statut ou non, et {@code CertificatCredit} n'a
 * même pas de date de création. Ce service livre donc <b>l'âge depuis la dernière décision
 * enregistrée</b>, avec repli sur la date de dépôt ou d'émission, et il publie cette définition dans
 * sa réponse pour qu'elle voyage jusqu'à l'en-tête de colonne et jusqu'aux exports. Le véritable âge
 * dans le statut ne deviendra calculable que sur les transitions postérieures à la mise en service
 * de {@code historique_statut}.
 *
 * <p><b>Les délais sont calculés en Java, et c'est voulu.</b> Les différences de dates en HQL
 * reposent sur des fonctions dont le rendu diverge entre H2 — où tournent les tests — et MySQL, où
 * tourne la production : un délai juste en test et faux en exploitation est le pire des deux mondes.
 * La requête ne rapporte donc que les couples de dates, en projection, et la moyenne se fait ici.
 * Le plafond {@link #MAX_DECISIONS_MESUREES} borne ce que cela peut coûter.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlocagesService {

    /** Au-delà, un dossier est compté comme immobile. */
    public static final int SEUIL_IMMOBILITE_JOURS = 30;

    /** Plafond des listes rendues : au-delà, le total reste juste mais la liste est tronquée. */
    private static final int MAX_LIGNES = 500;

    /** Plafond des décisions chargées pour calculer les moyennes. */
    private static final int MAX_DECISIONS_MESUREES = 50_000;

    /**
     * Les statuts d'où l'on ne bouge plus : un dossier adopté, notifié, rejeté ou annulé n'est
     * pas « immobile », il est fini. Les compter gonflerait la liste de dossiers classés et
     * noierait ceux qui attendent vraiment quelque chose.
     */
    private static final Set<StatutDemande> DEMANDES_TERMINEES = Set.of(
            StatutDemande.ADOPTEE, StatutDemande.NOTIFIEE,
            StatutDemande.REJETEE, StatutDemande.ANNULEE);

    private static final String DEFINITION_AGE =
            "Jours écoulés depuis la dernière décision enregistrée sur le dossier "
                    + "(à défaut, depuis son dépôt ou son émission). "
                    + "Ce n'est pas l'ancienneté dans le statut actuel, qu'aucune donnée ne permet "
                    + "encore de calculer.";

    private static final String DEFINITION_DELAI =
            "Du dépôt du dossier au visa de la direction, et non de son arrivée sur le bureau de "
                    + "cette direction : le moment où un dossier lui parvient n'est pas enregistré. "
                    + "Les visas posés par un administrateur sont comptés à part.";

    private final ReportingScopeFactory scopeFactory;
    private final ReportingPredicates predicates;
    private final ReportingQueryRunner runner;

    public BlocagesDto blocages(AuthenticatedUser auth, Instant from, Instant to,
                                Long autoriteContractanteId, Long entrepriseId, Integer seuilJours) {
        ReportingScope scope = scopeFactory.build(auth, from, to, autoriteContractanteId, entrepriseId);
        int seuil = seuilJours != null && seuilJours > 0 ? seuilJours : SEUIL_IMMOBILITE_JOURS;

        List<DossierImmobileDto> immobiles = dossiersImmobiles(scope, seuil);
        List<RejetTempOuvertDto> rejets = rejetsTempOuverts(scope);

        return BlocagesDto.builder()
                .seuilImmobiliteJours(seuil)
                .definitionAge(DEFINITION_AGE)
                .dossiersImmobiles(tronquer(immobiles))
                .nbDossiersImmobiles(immobiles.size())
                .dossiersImmobilesTronques(immobiles.size() > MAX_LIGNES)
                .rejetsTempOuverts(tronquer(rejets))
                .nbRejetsTempOuverts(rejets.size())
                .rejetsTempOuvertsTronques(rejets.size() > MAX_LIGNES)
                .anciennetePlusVieuxRejetJours(rejets.stream()
                        .map(RejetTempOuvertDto::getJoursOuvert)
                        .max(Long::compare)
                        .orElse(null))
                .delaisParDirection(delaisParDirection(scope))
                .filtersApplied(scope.filtersApplied())
                .build();
    }

    // ── les dossiers immobiles ─────────────────────────────────────────────────────────────────

    /**
     * Les demandes de correction qui n'ont pas bougé.
     *
     * <p>Limité aux demandes dans ce lot : c'est le circuit où la dernière décision est la plus
     * fiable, puisque {@code DecisionCorrection} couvre les quatre directions. Étendre aux
     * certificats et aux utilisations demandera la même précaution d'étiquette, et une décision sur
     * ce que « immobile » veut dire pour un crédit déjà ouvert — qui dort légitimement entre deux
     * utilisations.
     */
    private List<DossierImmobileDto> dossiersImmobiles(ReportingScope scope, int seuilJours) {
        String hql = "select d.id, d.reference, d.numero, d.statut, "
                + "(select max(dec.dateDecision) from DecisionCorrection dec "
                + "   where dec.demandeCorrection = d), "
                + "coalesce(d.dateDepot, d.dateCreation), ent.raisonSociale "
                + "from DemandeCorrection d left join d.entreprise ent "
                + "where " + predicates.demande("d", scope, true)
                + "   and d.statut not in (:termines)";

        Instant maintenant = Instant.now();
        List<DossierImmobileDto> out = new ArrayList<>();
        for (Object[] row : runner.lignes(hql, scope, Map.of("termines", DEMANDES_TERMINEES))) {
            Instant derniereDecision = (Instant) row[4];
            Instant repli = (Instant) row[5];
            Instant reference = derniereDecision != null ? derniereDecision : repli;
            if (reference == null) {
                continue;
            }
            long jours = Duration.between(reference, maintenant).toDays();
            if (jours < seuilJours) {
                continue;
            }
            out.add(DossierImmobileDto.builder()
                    .type("DemandeCorrection")
                    .id(nombre(row[0]))
                    .reference(texte(row[1]) != null ? texte(row[1]) : texte(row[2]))
                    .statut(texte(row[3]))
                    .dateReference(reference)
                    .dateDeRepli(derniereDecision == null)
                    .joursDepuisDerniereDecision(jours)
                    .entrepriseRaisonSociale(texte(row[6]))
                    .build());
        }
        out.sort(Comparator.comparingLong(DossierImmobileDto::getJoursDepuisDerniereDecision).reversed());
        return out;
    }

    // ── les rejets temporaires ouverts ─────────────────────────────────────────────────────────

    /**
     * Les rejets temporaires encore ouverts, sur les quatre circuits.
     *
     * <p>Le chiffre le plus solide du bloc : {@code rejetTempStatus} vaut {@code RESOLU} par défaut,
     * donc « ouvert » n'est jamais déduit d'une donnée absente. Et l'agent est atteint par une vraie
     * clé étrangère, contrairement aux visas portés par la demande.
     *
     * <p>Aucune fenêtre temporelle : c'est un état courant. Les borner à la période consultée
     * cacherait les blocages les plus anciens, qui sont ceux qu'il faut voir.
     */
    private List<RejetTempOuvertDto> rejetsTempOuverts(ReportingScope scope) {
        Instant maintenant = Instant.now();
        List<RejetTempOuvertDto> out = new ArrayList<>();

        collecter(out, maintenant, "DemandeCorrection",
                "select o.id, coalesce(o.reference, o.numero), dec.role, dec.utilisateur.username, "
                        + "dec.dateDecision, dec.motifRejet "
                        + "from DecisionCorrection dec join dec.demandeCorrection o "
                        + "where dec.rejetTempStatus = :ouvert and "
                        + predicates.demande("o", scope, false), scope);

        collecter(out, maintenant, "CertificatCredit",
                "select o.id, coalesce(o.reference, o.numero), dec.role, dec.utilisateur.username, "
                        + "dec.dateDecision, dec.motifRejet "
                        + "from DecisionCertificatCredit dec join dec.certificatCredit o "
                        + "where dec.rejetTempStatus = :ouvert and "
                        + predicates.certificat("o", scope, false), scope);

        collecter(out, maintenant, "UtilisationCredit",
                "select o.id, o.numeroCertificatUtilisation, dec.role, dec.utilisateur.username, "
                        + "dec.dateDecision, dec.motifRejet "
                        + "from DecisionUtilisationCredit dec join dec.utilisationCredit o "
                        + "where dec.rejetTempStatus = :ouvert and "
                        + predicates.utilisation("o", scope, false), scope);

        collecter(out, maintenant, "TransfertCredit",
                "select o.id, cast(o.id as string), dec.role, dec.utilisateur.username, "
                        + "dec.dateDecision, dec.motifRejet "
                        + "from DecisionTransfertCredit dec join dec.transfertCredit o "
                        + "where dec.rejetTempStatus = :ouvert and "
                        + predicates.transfert("o", scope, false), scope);

        out.sort(Comparator.comparingLong(RejetTempOuvertDto::getJoursOuvert).reversed());
        return out;
    }

    private void collecter(List<RejetTempOuvertDto> cible, Instant maintenant, String type,
                           String hql, ReportingScope scope) {
        for (Object[] row : runner.lignes(hql, scope,
                Map.of("ouvert", RejetTempStatus.OUVERT))) {
            Instant dateDecision = (Instant) row[4];
            cible.add(RejetTempOuvertDto.builder()
                    .type(type)
                    .dossierId(nombre(row[0]))
                    .reference(texte(row[1]))
                    .role(texte(row[2]))
                    .agentUsername(texte(row[3]))
                    .dateDecision(dateDecision)
                    .joursOuvert(dateDecision != null
                            ? Duration.between(dateDecision, maintenant).toDays() : 0L)
                    .motifRejet(texte(row[5]))
                    .build());
        }
    }

    // ── les délais par direction ───────────────────────────────────────────────────────────────

    private List<DelaiDirectionDto> delaisParDirection(ReportingScope scope) {
        Map<String, Mesure> parRole = new LinkedHashMap<>();

        // Correction : le seul circuit où le drapeau « visa posé par l'administrateur » existe.
        String hqlCorrection = "select dec.role, dec.dateDecision, "
                + "coalesce(o.dateDepot, o.dateCreation), dec.visaParAdmin "
                + "from DecisionCorrection dec join dec.demandeCorrection o "
                + "where dec.decision = :visa and " + predicates.demande("o", scope, true);
        for (Object[] row : runner.lignesPaginees(hqlCorrection, scope,
                Map.of("visa", DecisionCorrectionType.VISA), 0, MAX_DECISIONS_MESUREES)) {
            mesurer(parRole, "CORRECTION", texte(row[0]), (Instant) row[1], (Instant) row[2],
                    Boolean.TRUE.equals(row[3]));
        }

        // Certificat : même drapeau, autre circuit, et le départ est la date d'émission.
        String hqlCertificat = "select dec.role, dec.dateDecision, o.dateEmission, dec.visaParAdmin "
                + "from DecisionCertificatCredit dec join dec.certificatCredit o "
                + "where dec.decision = :visa and " + predicates.certificat("o", scope, true);
        for (Object[] row : runner.lignesPaginees(hqlCertificat, scope,
                Map.of("visa", DecisionCorrectionType.VISA), 0, MAX_DECISIONS_MESUREES)) {
            mesurer(parRole, "CERTIFICAT", texte(row[0]), (Instant) row[1], (Instant) row[2],
                    Boolean.TRUE.equals(row[3]));
        }

        List<DelaiDirectionDto> out = new ArrayList<>();
        parRole.forEach((cle, m) -> out.add(m.versDto()));
        out.sort(Comparator.comparing(DelaiDirectionDto::getCircuit)
                .thenComparing(DelaiDirectionDto::getRole));
        return out;
    }

    private void mesurer(Map<String, Mesure> parRole, String circuit, String role,
                         Instant decision, Instant depart, boolean parAdmin) {
        if (role == null) {
            return;
        }
        Mesure m = parRole.computeIfAbsent(circuit + "|" + role, c -> new Mesure(circuit, role));
        if (parAdmin) {
            // Compté, mais hors moyenne : sinon le chiffre mesure l'administrateur.
            m.nbParAdmin++;
            return;
        }
        if (decision == null || depart == null) {
            return;
        }
        long jours = Duration.between(depart, decision).toDays();
        if (jours < 0) {
            // Un visa antérieur au dépôt : donnée incohérente, reprise d'archive le plus souvent.
            return;
        }
        m.nb++;
        m.totalJours += jours;
        m.maxJours = Math.max(m.maxJours, jours);
    }

    /** Accumulateur, pour que la moyenne se calcule en Java et non en SQL. */
    private static final class Mesure {
        private final String circuit;
        private final String role;
        private long nb;
        private long totalJours;
        private long maxJours;
        private long nbParAdmin;

        private Mesure(String circuit, String role) {
            this.circuit = circuit;
            this.role = role;
        }

        private DelaiDirectionDto versDto() {
            return DelaiDirectionDto.builder()
                    .role(role)
                    .circuit(circuit)
                    .nbVisas(nb)
                    .delaiMoyenJours(nb > 0 ? Math.round(100.0 * totalJours / nb) / 100.0 : null)
                    .delaiMaxJours(nb > 0 ? maxJours : null)
                    .nbVisasParAdmin(nbParAdmin)
                    .definitionDelai(DEFINITION_DELAI)
                    .build();
        }
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
}
