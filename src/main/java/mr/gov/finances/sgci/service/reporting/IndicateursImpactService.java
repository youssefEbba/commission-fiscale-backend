package mr.gov.finances.sgci.service.reporting;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.ValeurReferenceDigitalisation;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.IndicateurReference;
import mr.gov.finances.sgci.domain.enums.StatutAvenant;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeOperationCloture;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.ValeurReferenceDigitalisationRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ParametreService;
import mr.gov.finances.sgci.web.dto.reporting.EngagementEtatDto;
import mr.gov.finances.sgci.web.dto.reporting.EngagementLigneDto;
import mr.gov.finances.sgci.web.dto.reporting.IndicateursImpactDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les indicateurs d'impact de la digitalisation (cahier des charges, point 2).
 *
 * <p>Tout est compté sur les dossiers <b>déposés dans la fenêtre</b> et dans le périmètre du rôle,
 * avec les prédicats partagés du reporting. Les dossiers repris de l'archive papier en sont exclus :
 * ils n'ont ni dates de décision ni circuit, et forment la base de la colonne « avant ».
 *
 * <p>Les délais par phase viennent de l'historique des statuts ({@code historique_statut}) : la
 * première entrée dans chaque statut. Cet historique n'existe que pour les transitions faites depuis
 * sa mise en service ; un dossier plus ancien n'entre simplement dans aucune phase. Les durées sont
 * calculées en Java, comme dans {@link BlocagesService}, pour ne pas dépendre des fonctions de date
 * de H2 et de MySQL.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndicateursImpactService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final String ARCHIVE_CERTIFICAT = "ARCHIVE-%";
    private static final String ARCHIVE_DEMANDE = "ARCHIVE-DC-%";

    /** Une borne de phase : le dépôt du dossier, ou la première entrée dans l'un des statuts. */
    private static final String DEPOT = "__DEPOT__";

    private record DefinitionPhase(String libelle, Set<String> depuis, Set<String> jusqua) {
    }

    private static final List<DefinitionPhase> PHASES_CORRECTION = List.of(
            new DefinitionPhase("Dépôt → recevabilité", Set.of(DEPOT),
                    noms(StatutDemande.RECEVABLE, StatutDemande.EN_EVALUATION)),
            new DefinitionPhase("Instruction par les directions (visas)", noms(StatutDemande.EN_EVALUATION),
                    noms(StatutDemande.EN_VALIDATION)),
            new DefinitionPhase("Décision du Président", noms(StatutDemande.EN_VALIDATION),
                    noms(StatutDemande.ADOPTEE, StatutDemande.REJETEE)),
            new DefinitionPhase("Total : dépôt → décision", Set.of(DEPOT),
                    noms(StatutDemande.ADOPTEE, StatutDemande.REJETEE, StatutDemande.NOTIFIEE)));

    private static final List<DefinitionPhase> PHASES_MISE_EN_PLACE = List.of(
            new DefinitionPhase("Dépôt → prise en charge", Set.of(DEPOT), noms(StatutCertificat.EN_CONTROLE)),
            new DefinitionPhase("Contrôle par les directions (visas)", noms(StatutCertificat.EN_CONTROLE),
                    noms(StatutCertificat.EN_VALIDATION_PRESIDENT)),
            new DefinitionPhase("Validation du Président", noms(StatutCertificat.EN_VALIDATION_PRESIDENT),
                    noms(StatutCertificat.VALIDE_PRESIDENT, StatutCertificat.EN_OUVERTURE_DGTCP,
                            StatutCertificat.OUVERT)),
            new DefinitionPhase("Ouverture du crédit par la DGTCP",
                    noms(StatutCertificat.VALIDE_PRESIDENT, StatutCertificat.EN_OUVERTURE_DGTCP),
                    noms(StatutCertificat.OUVERT)),
            new DefinitionPhase("Total : dépôt → crédit ouvert", Set.of(DEPOT), noms(StatutCertificat.OUVERT)));

    private static final List<DefinitionPhase> PHASES_UTILISATION = List.of(
            new DefinitionPhase("Dépôt → prise en charge", Set.of(DEPOT),
                    noms(StatutUtilisation.EN_VERIFICATION, StatutUtilisation.VISE, StatutUtilisation.EN_CONTROLE_DGD)),
            new DefinitionPhase("Instruction → certificat d'utilisation", Set.of(DEPOT),
                    noms(StatutUtilisation.CERTIFICAT_EMIS)),
            new DefinitionPhase("Certificat → débit du crédit", noms(StatutUtilisation.CERTIFICAT_EMIS),
                    noms(StatutUtilisation.LIQUIDEE, StatutUtilisation.APUREE)),
            new DefinitionPhase("Total : dépôt → débit du crédit", Set.of(DEPOT),
                    noms(StatutUtilisation.LIQUIDEE, StatutUtilisation.APUREE)));

    private static final List<DefinitionPhase> PHASES_TRANSFERT = List.of(
            new DefinitionPhase("Total : dépôt → décision", Set.of(DEPOT),
                    noms(StatutTransfert.TRANSFERE, StatutTransfert.REJETE)));

    private static final List<String> DEFINITIONS = List.of(
            "Dossiers déposés : dossiers dont la date de dépôt tombe dans la période, hors brouillons et hors reprise d'archive.",
            "Traités : dossiers arrivés à une issue — validés (adoptés, crédit ouvert, crédit débité, transfert effectué, avenant adopté) ou rejetés.",
            "Taux de validation et de rejet : rapportés aux dossiers traités. Taux de compléments : part des dossiers déposés ayant reçu au moins une demande de pièces.",
            "Délais par phase : de la première entrée dans le statut de départ à la première entrée dans le statut d'arrivée, d'après l'historique des statuts ; un dossier antérieur à cet historique n'est pas compté.",
            "Montants demandés : crédits déclarés par l'autorité contractante au dépôt ; retenus : crédits arrêtés par la Commission sur les demandes adoptées ; validés : enveloppes des crédits ouverts dans la période.",
            "Consommés et taux d'utilisation : mesurés sur les soldes des certificats (droits et taxes hors TVA et TVA à l'import pour les Douanes, TVA intérieure pour la DGI).",
            "Ajustés : variation nette des enveloppes par les avenants appliqués dans la période. Annulés et clôturés : soldes abandonnés à l'annulation ou à la clôture des crédits.",
            "Avant / après : volumes et montants d'avant tirés des crédits repris de l'archive, délais et taux d'avant saisis par l'administrateur ; moyennes annuelles de part et d'autre de la date de bascule.");

    private final ReportingScopeFactory scopeFactory;
    private final ReportingPredicates predicates;
    private final ReportingQueryRunner runner;
    private final EngagementEtatService engagementEtatService;
    private final ValeurReferenceDigitalisationRepository referenceRepository;
    private final ParametreService parametreService;

    public IndicateursImpactDto indicateurs(AuthenticatedUser auth, Instant from, Instant to,
                                            Long autoriteContractanteId, Long entrepriseId) {
        ReportingScope scope = scopeFactory.build(auth, from, to, autoriteContractanteId, entrepriseId);

        List<IndicateursImpactDto.Processus> processus = new ArrayList<>();
        processus.add(processusCorrection(scope));
        processus.add(processusMiseEnPlace(scope));
        processus.add(processusUtilisation(scope, TypeUtilisation.TVA_INTERIEURE));
        processus.add(processusUtilisation(scope, TypeUtilisation.DOUANIER));
        processus.add(processusTransfert(scope));
        processus.add(processusAvenant(scope));

        List<IndicateursImpactDto.Phase> phases = new ArrayList<>();
        phases.addAll(phases(scope, "CORRECTION", "DemandeCorrection",
                "from DemandeCorrection d where " + predicates.demande("d", scope, true)
                        + " and d.statut <> :brouillon and (d.numero is null or d.numero not like :archiveDc)",
                "coalesce(d.dateDepot, d.dateCreation)",
                Map.of("brouillon", StatutDemande.BROUILLON, "archiveDc", ARCHIVE_DEMANDE), PHASES_CORRECTION));
        phases.addAll(phases(scope, "MISE_EN_PLACE", "CertificatCredit",
                "from CertificatCredit d where " + predicates.certificat("d", scope, true)
                        + " and d.statut <> :brouillon and d.numero not like :archive",
                "d.dateEmission",
                Map.of("brouillon", StatutCertificat.BROUILLON, "archive", ARCHIVE_CERTIFICAT), PHASES_MISE_EN_PLACE));
        for (TypeUtilisation type : TypeUtilisation.values()) {
            phases.addAll(phases(scope, codeUtilisation(type), "UtilisationCredit",
                    "from UtilisationCredit d where " + predicates.utilisation("d", scope, true)
                            + " and d.type = :type and d.statut <> :brouillon and d.origineArchiveLibelle is null",
                    "d.dateDemande",
                    Map.of("type", type, "brouillon", StatutUtilisation.BROUILLON), PHASES_UTILISATION));
        }
        phases.addAll(phases(scope, "TRANSFERT", "TransfertCredit",
                "from TransfertCredit d where " + predicates.transfert("d", scope, true),
                "d.dateDemande", Map.of(), PHASES_TRANSFERT));
        phases.addAll(phasesAvenant(scope));

        EngagementEtatDto engagement = engagementEtatService.parAxe(auth, from, to,
                autoriteContractanteId, entrepriseId, null);

        IndicateursImpactDto.Montants montants = montants(scope, engagement.getTotal());
        IndicateursImpactDto.TauxUtilisation taux = tauxUtilisation(engagement.getTotal());
        LocalDate bascule = dateBascule();

        return IndicateursImpactDto.builder()
                .from(scope.from())
                .to(scope.to())
                .processus(processus)
                .phases(phases)
                .montants(montants)
                .tauxUtilisation(taux)
                .dateBascule(bascule)
                .avantApres(avantApres(scope, bascule, processus, phases))
                .definitions(DEFINITIONS)
                .build();
    }

    // ── volumes et taux, par processus ────────────────────────────────────────────────────────

    private IndicateursImpactDto.Processus processusCorrection(ReportingScope scope) {
        String base = "from DemandeCorrection d where " + predicates.demande("d", scope, true)
                + " and d.statut <> :brouillon and (d.numero is null or d.numero not like :archiveDc)";
        Map<String, Object> p = Map.of("brouillon", StatutDemande.BROUILLON, "archiveDc", ARCHIVE_DEMANDE);
        Map<String, Long> parStatut = compterParStatut("select d.statut, count(d) " + base + " group by d.statut", scope, p);
        long avecComplements = runner.countSingle("select count(distinct d.id) " + base
                + " and exists (select 1 from DecisionCorrection dec where dec.demandeCorrection = d"
                + " and dec.decision = :rejetTemp)", scope, avec(p, "rejetTemp", DecisionCorrectionType.REJET_TEMP));
        Double reponse = delaiReponse("select dec.dateDecision, dec.rejetTempResolvedAt from DecisionCorrection dec"
                + " join dec.demandeCorrection d where dec.decision = :rejetTemp and dec.rejetTempResolvedAt is not null and "
                + base.substring(base.indexOf(" where ") + 7), scope, avec(p, "rejetTemp", DecisionCorrectionType.REJET_TEMP));
        return processus("CORRECTION", "Correction de l'offre fiscale", parStatut,
                noms(StatutDemande.ADOPTEE, StatutDemande.NOTIFIEE), noms(StatutDemande.REJETEE),
                avecComplements, reponse);
    }

    private IndicateursImpactDto.Processus processusMiseEnPlace(ReportingScope scope) {
        String base = "from CertificatCredit d where " + predicates.certificat("d", scope, true)
                + " and d.statut <> :brouillon and d.numero not like :archive";
        Map<String, Object> p = Map.of("brouillon", StatutCertificat.BROUILLON, "archive", ARCHIVE_CERTIFICAT);
        Map<String, Long> parStatut = compterParStatut("select d.statut, count(d) " + base + " group by d.statut", scope, p);
        long avecComplements = runner.countSingle("select count(distinct d.id) " + base
                + " and exists (select 1 from DecisionCertificatCredit dec where dec.certificatCredit = d"
                + " and dec.decision = :rejetTemp)", scope, avec(p, "rejetTemp", DecisionCorrectionType.REJET_TEMP));
        Double reponse = delaiReponse("select dec.dateDecision, dec.rejetTempResolvedAt from DecisionCertificatCredit dec"
                + " join dec.certificatCredit d where dec.decision = :rejetTemp and dec.rejetTempResolvedAt is not null and "
                + base.substring(base.indexOf(" where ") + 7), scope, avec(p, "rejetTemp", DecisionCorrectionType.REJET_TEMP));
        return processus("MISE_EN_PLACE", "Mise en place du crédit", parStatut,
                noms(StatutCertificat.VALIDE_PRESIDENT, StatutCertificat.EN_OUVERTURE_DGTCP, StatutCertificat.OUVERT,
                        StatutCertificat.MODIFIE, StatutCertificat.CLOTURE),
                noms(StatutCertificat.ANNULE), avecComplements, reponse);
    }

    private IndicateursImpactDto.Processus processusUtilisation(ReportingScope scope, TypeUtilisation type) {
        String base = "from UtilisationCredit d where " + predicates.utilisation("d", scope, true)
                + " and d.type = :type and d.statut <> :brouillon and d.origineArchiveLibelle is null";
        Map<String, Object> p = Map.of("type", type, "brouillon", StatutUtilisation.BROUILLON);
        Map<String, Long> parStatut = compterParStatut("select d.statut, count(d) " + base + " group by d.statut", scope, p);
        long avecComplements = runner.countSingle("select count(distinct d.id) " + base
                + " and exists (select 1 from DecisionUtilisationCredit dec where dec.utilisationCredit = d"
                + " and dec.decision = :rejetTemp)", scope, avec(p, "rejetTemp", DecisionCorrectionType.REJET_TEMP));
        Double reponse = delaiReponse("select dec.dateDecision, dec.rejetTempResolvedAt from DecisionUtilisationCredit dec"
                + " join dec.utilisationCredit d where dec.decision = :rejetTemp and dec.rejetTempResolvedAt is not null and "
                + base.substring(base.indexOf(" where ") + 7), scope, avec(p, "rejetTemp", DecisionCorrectionType.REJET_TEMP));
        return processus(codeUtilisation(type), type == TypeUtilisation.DOUANIER
                        ? "Utilisation en douane" : "Utilisation TVA intérieure", parStatut,
                noms(StatutUtilisation.LIQUIDEE, StatutUtilisation.APUREE, StatutUtilisation.CLOTUREE),
                noms(StatutUtilisation.REJETEE), avecComplements, reponse);
    }

    private IndicateursImpactDto.Processus processusTransfert(ReportingScope scope) {
        String base = "from TransfertCredit d where " + predicates.transfert("d", scope, true);
        Map<String, Long> parStatut = compterParStatut("select d.statut, count(d) " + base + " group by d.statut",
                scope, Map.of());
        long avecComplements = runner.countSingle("select count(distinct d.id) " + base
                + " and exists (select 1 from DecisionTransfertCredit dec where dec.transfertCredit = d"
                + " and dec.decision = :rejetTemp)", scope, Map.of("rejetTemp", DecisionCorrectionType.REJET_TEMP));
        Double reponse = delaiReponse("select dec.dateDecision, dec.rejetTempResolvedAt from DecisionTransfertCredit dec"
                + " join dec.transfertCredit d where dec.decision = :rejetTemp and dec.rejetTempResolvedAt is not null and "
                + base.substring(base.indexOf(" where ") + 7), scope, Map.of("rejetTemp", DecisionCorrectionType.REJET_TEMP));
        return processus("TRANSFERT", "Transfert de crédit", parStatut,
                noms(StatutTransfert.TRANSFERE), noms(StatutTransfert.REJETE), avecComplements, reponse);
    }

    private IndicateursImpactDto.Processus processusAvenant(ReportingScope scope) {
        Map<String, Long> parStatut = compterParStatut("select a.statut, count(a) from Avenant a join a.certificatCredit c"
                + " where a.dateDepot between :from and :to and " + predicates.certificat("c", scope, false)
                + " group by a.statut", scope, Map.of());
        return processus("AVENANT", "Avenant", parStatut,
                noms(StatutAvenant.ADOPTE, StatutAvenant.APPLIQUE), noms(StatutAvenant.REJETE), 0L, null);
    }

    private static IndicateursImpactDto.Processus processus(String code, String libelle, Map<String, Long> parStatut,
                                                            Set<String> valides, Set<String> rejetes,
                                                            long avecComplements, Double reponse) {
        long deposes = parStatut.values().stream().mapToLong(Long::longValue).sum();
        long nbValides = somme(parStatut, valides);
        long nbRejetes = somme(parStatut, rejetes);
        long traites = nbValides + nbRejetes;
        return IndicateursImpactDto.Processus.builder()
                .code(code)
                .libelle(libelle)
                .deposes(deposes)
                .traites(traites)
                .valides(nbValides)
                .rejetes(nbRejetes)
                .avecComplements(avecComplements)
                .tauxValidationPct(pct(nbValides, traites))
                .tauxRejetPct(pct(nbRejetes, traites))
                .tauxComplementsPct(pct(avecComplements, deposes))
                .delaiReponseComplementsJours(reponse)
                .build();
    }

    // ── délais par phase ──────────────────────────────────────────────────────────────────────

    /**
     * Les phases d'un processus, mesurées sur l'historique des statuts.
     *
     * <p>{@code fromWhere} est la clause {@code from … where …} qui délimite les dossiers ;
     * {@code dateDepot} l'expression de leur date de dépôt.
     */
    private List<IndicateursImpactDto.Phase> phases(ReportingScope scope, String processus, String entityType,
                                                    String fromWhere, String dateDepot, Map<String, Object> params,
                                                    List<DefinitionPhase> definitions) {
        Map<Long, Instant> depots = new HashMap<>();
        for (Object[] row : runner.lignes("select d.id, " + dateDepot + " " + fromWhere, scope, params)) {
            if (row[1] != null) {
                depots.put(((Number) row[0]).longValue(), (Instant) row[1]);
            }
        }
        Map<Long, Map<String, Instant>> entrees = new HashMap<>();
        if (!depots.isEmpty()) {
            String hql = "select h.entityId, h.statutApres, min(h.dateTransition) from HistoriqueStatut h"
                    + " where h.entityType = :entityType and h.entityId in (select d.id " + fromWhere + ")"
                    + " group by h.entityId, h.statutApres";
            for (Object[] row : runner.lignes(hql, scope, avec(params, "entityType", entityType))) {
                entrees.computeIfAbsent(((Number) row[0]).longValue(), k -> new HashMap<>())
                        .put(String.valueOf(row[1]), (Instant) row[2]);
            }
        }
        List<IndicateursImpactDto.Phase> out = new ArrayList<>();
        for (DefinitionPhase def : definitions) {
            List<Double> durees = new ArrayList<>();
            depots.forEach((id, depot) -> {
                Map<String, Instant> parStatut = entrees.getOrDefault(id, Map.of());
                Instant debut = premiere(def.depuis(), parStatut, depot);
                Instant fin = premiere(def.jusqua(), parStatut, null);
                if (debut != null && fin != null && !fin.isBefore(debut)) {
                    durees.add(jours(debut, fin));
                }
            });
            out.add(phase(processus, def.libelle(), durees));
        }
        return out;
    }

    /** L'avenant porte ses propres dates d'étape : pas besoin de l'historique. */
    private List<IndicateursImpactDto.Phase> phasesAvenant(ReportingScope scope) {
        List<Double> examen = new ArrayList<>();
        List<Double> validation = new ArrayList<>();
        List<Double> application = new ArrayList<>();
        List<Double> total = new ArrayList<>();
        for (Object[] row : runner.lignes("select a.dateDepot, a.dateExamen, a.dateValidation, a.dateApplication"
                + " from Avenant a join a.certificatCredit c where a.dateDepot between :from and :to and "
                + predicates.certificat("c", scope, false), scope, Map.of())) {
            Instant depot = (Instant) row[0];
            Instant ex = (Instant) row[1];
            Instant val = (Instant) row[2];
            Instant app = (Instant) row[3];
            ajouter(examen, depot, ex);
            ajouter(validation, ex, val);
            ajouter(application, val, app);
            ajouter(total, depot, app);
        }
        return List.of(phase("AVENANT", "Dépôt → prise en charge", examen),
                phase("AVENANT", "Prise en charge → validation", validation),
                phase("AVENANT", "Validation → application au crédit", application),
                phase("AVENANT", "Total : dépôt → application", total));
    }

    private static Instant premiere(Set<String> statuts, Map<String, Instant> parStatut, Instant depot) {
        Instant out = null;
        for (String s : statuts) {
            Instant t = DEPOT.equals(s) ? depot : parStatut.get(s);
            if (t != null && (out == null || t.isBefore(out))) {
                out = t;
            }
        }
        return out;
    }

    private static void ajouter(List<Double> durees, Instant debut, Instant fin) {
        if (debut != null && fin != null && !fin.isBefore(debut)) {
            durees.add(jours(debut, fin));
        }
    }

    private static IndicateursImpactDto.Phase phase(String processus, String libelle, List<Double> durees) {
        return IndicateursImpactDto.Phase.builder()
                .processus(processus)
                .phase(libelle)
                .nbDossiers(durees.size())
                .moyenneJours(moyenne(durees))
                .medianeJours(mediane(durees))
                .build();
    }

    private Double delaiReponse(String hql, ReportingScope scope, Map<String, Object> params) {
        List<Double> durees = new ArrayList<>();
        for (Object[] row : runner.lignes(hql, scope, params)) {
            ajouter(durees, (Instant) row[0], (Instant) row[1]);
        }
        return moyenne(durees);
    }

    // ── montants et utilisation effective ─────────────────────────────────────────────────────

    private IndicateursImpactDto.Montants montants(ReportingScope scope, EngagementLigneDto total) {
        Map<String, Object> pDemande = Map.of("brouillon", StatutDemande.BROUILLON,
                "annulee", StatutDemande.ANNULEE, "archiveDc", ARCHIVE_DEMANDE);
        String demandes = " from DemandeCorrection d where " + predicates.demande("d", scope, true)
                + " and d.statut not in (:brouillon, :annulee) and (d.numero is null or d.numero not like :archiveDc)";
        BigDecimal demande = montant("select sum(coalesce(d.creditInterieurDeclare, d.creditInterieur, 0)"
                + " + coalesce(d.creditExterieurDeclare, d.creditExterieur, 0))" + demandes, scope, pDemande);
        BigDecimal retenus = montant("select sum(coalesce(d.creditInterieur, 0) + coalesce(d.creditExterieur, 0))"
                + demandes + " and d.statut in (:adoptees)", scope,
                avec(pDemande, "adoptees", EnumSet.of(StatutDemande.ADOPTEE, StatutDemande.NOTIFIEE)));
        BigDecimal valides = montant("select sum(coalesce(c.montantCordon, 0) + coalesce(c.montantTVAInterieure, 0))"
                + " from CertificatCredit c where c.dateMiseEnPlace between :from and :to"
                + " and c.numero not like :archive and " + predicates.certificat("c", scope, false),
                scope, Map.of("archive", ARCHIVE_CERTIFICAT));
        BigDecimal ajustes = montant("select sum(coalesce(a.montantCordonApres, 0) - coalesce(a.montantCordonAvant, 0)"
                + " + coalesce(a.montantTVAInterieureApres, 0) - coalesce(a.montantTVAInterieureAvant, 0))"
                + " from Avenant a join a.certificatCredit c where a.statut = :applique"
                + " and a.dateApplication between :from and :to and " + predicates.certificat("c", scope, false),
                scope, Map.of("applique", StatutAvenant.APPLIQUE));
        String clotures = "select sum(coalesce(cc.soldeRestant, 0)) from ClotureCredit cc join cc.certificatCredit c"
                + " where cc.typeOperation = :operation and cc.dateCloture between :from and :to and "
                + predicates.certificat("c", scope, false);
        BigDecimal annules = montant(clotures, scope, Map.of("operation", TypeOperationCloture.ANNULATION));
        BigDecimal clotures2 = montant(clotures, scope, Map.of("operation", TypeOperationCloture.CLOTURE));
        return IndicateursImpactDto.Montants.builder()
                .demandes(demande)
                .retenus(retenus)
                .valides(valides)
                .consommes(total != null ? total.getConsommeSoldes() : null)
                .ajustes(ajustes)
                .annules(annules)
                .clotures(clotures2)
                .build();
    }

    private static IndicateursImpactDto.TauxUtilisation tauxUtilisation(EngagementLigneDto t) {
        if (t == null) {
            return IndicateursImpactDto.TauxUtilisation.builder().build();
        }
        BigDecimal accordeDouane = nz(t.getAccordeCordonHorsTva()).add(nz(t.getAccordeTvaImport()));
        BigDecimal consommeDouane = nz(t.getConsommeCordonHorsTva()).add(nz(t.getConsommeTvaImport()));
        BigDecimal accordeTva = nz(t.getAccordeTvaInterieure());
        BigDecimal consommeTva = nz(t.getConsommeTvaInterieure());
        return IndicateursImpactDto.TauxUtilisation.builder()
                .accordeTvaInterieure(accordeTva)
                .consommeTvaInterieure(consommeTva)
                .tvaInterieurePct(pct(consommeTva, accordeTva))
                .accordeDouane(accordeDouane)
                .consommeDouane(consommeDouane)
                .douanePct(pct(consommeDouane, accordeDouane))
                .globalPct(pct(consommeDouane.add(consommeTva), accordeDouane.add(accordeTva)))
                .build();
    }

    // ── avant / après ─────────────────────────────────────────────────────────────────────────

    /**
     * La comparaison avant / après la bascule, en moyennes annuelles. Le « avant » des volumes et
     * des montants vient de l'archive reprise ; celui des délais et des taux, des valeurs de
     * référence saisies. Une valeur d'avant absente laisse la ligne avec {@code avant = null}.
     */
    private List<IndicateursImpactDto.Comparaison> avantApres(ReportingScope scope, LocalDate bascule,
                                                              List<IndicateursImpactDto.Processus> processus,
                                                              List<IndicateursImpactDto.Phase> phases) {
        List<IndicateursImpactDto.Comparaison> out = new ArrayList<>();
        if (scope.perimetreVide()) {
            return out;
        }
        Map<IndicateurReference, ValeurReferenceDigitalisation> refs = new HashMap<>();
        referenceRepository.findAll().forEach(r -> refs.put(r.getCode(), r));

        // Les crédits repris de l'archive, en moyenne par année couverte.
        Object[] archive = runner.uneLigne("select count(c), sum(coalesce(c.montantCordon, 0) + coalesce(c.montantTVAInterieure, 0)),"
                + " min(c.dateEmission), max(c.dateEmission) from CertificatCredit c where c.numero like :archive and "
                + predicates.certificat("c", scope, false), scope, Map.of("archive", ARCHIVE_CERTIFICAT));
        Object[] digital = runner.uneLigne("select count(c), sum(coalesce(c.montantCordon, 0) + coalesce(c.montantTVAInterieure, 0)),"
                + " min(c.dateEmission), max(c.dateEmission) from CertificatCredit c where c.numero not like :archive"
                + " and c.dateMiseEnPlace is not null and " + predicates.certificat("c", scope, false),
                scope, Map.of("archive", ARCHIVE_CERTIFICAT));
        double anneesAvant = annees(archive);
        double anneesApres = annees(digital);
        String periodeAvant = periode(archive);
        if (archive != null && anneesAvant > 0) {
            out.add(comparaison("CREDITS_MIS_EN_PLACE_PAR_AN", "Crédits mis en place par an", "dossiers",
                    parAn(nombre(archive[0]), anneesAvant), parAn(nombre(digital != null ? digital[0] : null), anneesApres),
                    "archive reprise", periodeAvant));
            out.add(comparaison("MONTANT_MIS_EN_PLACE_PAR_AN", "Montant mis en place par an", "MRU",
                    parAn(montantDe(archive[1]), anneesAvant),
                    parAn(montantDe(digital != null ? digital[1] : null), anneesApres),
                    "archive reprise", periodeAvant));
        }

        IndicateursImpactDto.Processus correction = trouver(processus, "CORRECTION");
        out.add(depuisReference(refs, IndicateurReference.DELAI_MOYEN_CORRECTION_JOURS, "Délai moyen d'une correction",
                "jours", decimal(moyennePhase(phases, "CORRECTION", "Total : dépôt → décision"))));
        out.add(depuisReference(refs, IndicateurReference.DELAI_MOYEN_MISE_EN_PLACE_JOURS, "Délai moyen d'une mise en place",
                "jours", decimal(moyennePhase(phases, "MISE_EN_PLACE", "Total : dépôt → crédit ouvert"))));
        out.add(depuisReference(refs, IndicateurReference.DELAI_MOYEN_UTILISATION_JOURS, "Délai moyen d'une utilisation",
                "jours", decimal(moyenneUtilisations(phases))));
        out.add(depuisReference(refs, IndicateurReference.TAUX_VALIDATION_CORRECTION_PCT, "Taux d'adoption des corrections",
                "%", decimal(correction != null ? correction.getTauxValidationPct() : null)));
        out.add(depuisReference(refs, IndicateurReference.TAUX_REJET_CORRECTION_PCT, "Taux de rejet des corrections",
                "%", decimal(correction != null ? correction.getTauxRejetPct() : null)));
        out.add(depuisReference(refs, IndicateurReference.TAUX_COMPLEMENTS_PCT, "Taux de demandes de compléments",
                "%", decimal(tauxComplementsGlobal(processus))));
        double anneesFenetre = Math.max(Duration.between(scope.from(), scope.to()).toDays() / 365.25, 1.0 / 12);
        long traites = processus.stream().mapToLong(IndicateursImpactDto.Processus::getTraites).sum();
        out.add(depuisReference(refs, IndicateurReference.DEMANDES_TRAITEES_PAR_AN, "Demandes traitées par an",
                "dossiers", parAn(BigDecimal.valueOf(traites), anneesFenetre)));
        return out;
    }

    private static IndicateursImpactDto.Comparaison depuisReference(Map<IndicateurReference, ValeurReferenceDigitalisation> refs,
                                                                    IndicateurReference code, String libelle, String unite,
                                                                    BigDecimal apres) {
        ValeurReferenceDigitalisation ref = refs.get(code);
        return comparaison(code.name(), libelle, unite, ref != null ? ref.getValeur() : null, apres,
                ref != null ? "valeur de référence saisie" : "non renseignée",
                ref != null ? ref.getPeriodeSource() : null);
    }

    private static IndicateursImpactDto.Comparaison comparaison(String code, String libelle, String unite,
                                                                BigDecimal avant, BigDecimal apres,
                                                                String source, String periodeAvant) {
        return IndicateursImpactDto.Comparaison.builder()
                .code(code)
                .libelle(libelle)
                .unite(unite)
                .avant(avant)
                .apres(apres)
                .variationPct(avant != null && apres != null ? CalendrierPeriodes.variation(apres, avant) : null)
                .sourceAvant(source)
                .periodeAvant(periodeAvant)
                .build();
    }

    /** La date de bascule saisie dans les réglages ; {@code null} si absente ou illisible. */
    LocalDate dateBascule() {
        String valeur = parametreService.lire(ParametreService.DIGITALISATION_DATE_BASCULE);
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valeur.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ── utilitaires ───────────────────────────────────────────────────────────────────────────

    private Map<String, Long> compterParStatut(String hql, ReportingScope scope, Map<String, Object> params) {
        Map<String, Long> out = new HashMap<>();
        for (Object[] row : runner.lignes(hql, scope, params)) {
            out.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return out;
    }

    private BigDecimal montant(String hql, ReportingScope scope, Map<String, Object> params) {
        if (scope.perimetreVide()) {
            return BigDecimal.ZERO;
        }
        List<Object[]> rows = runner.lignes(hql.replaceFirst("select ", "select 1, "), scope, params);
        return rows.isEmpty() ? BigDecimal.ZERO : montantDe(rows.get(0)[1]);
    }

    private static Map<String, Object> avec(Map<String, Object> base, String cle, Object valeur) {
        Map<String, Object> out = new HashMap<>(base);
        out.put(cle, valeur);
        return out;
    }

    private static long somme(Map<String, Long> parStatut, Set<String> statuts) {
        return statuts.stream().mapToLong(s -> parStatut.getOrDefault(s, 0L)).sum();
    }

    private static Set<String> noms(Enum<?>... valeurs) {
        Set<String> out = new java.util.HashSet<>();
        for (Enum<?> v : valeurs) {
            out.add(v.name());
        }
        return Set.copyOf(out);
    }

    private static String codeUtilisation(TypeUtilisation type) {
        return type == TypeUtilisation.DOUANIER ? "UTILISATION_DOUANE" : "UTILISATION_TVA";
    }

    private static IndicateursImpactDto.Processus trouver(List<IndicateursImpactDto.Processus> processus, String code) {
        return processus.stream().filter(p -> code.equals(p.getCode())).findFirst().orElse(null);
    }

    private static Double moyennePhase(List<IndicateursImpactDto.Phase> phases, String processus, String libelle) {
        return phases.stream()
                .filter(p -> processus.equals(p.getProcessus()) && libelle.equals(p.getPhase()))
                .findFirst()
                .map(IndicateursImpactDto.Phase::getMoyenneJours)
                .orElse(null);
    }

    /** Moyenne des deux types d'utilisation, pondérée par leur nombre de dossiers. */
    private static Double moyenneUtilisations(List<IndicateursImpactDto.Phase> phases) {
        double somme = 0;
        long n = 0;
        for (IndicateursImpactDto.Phase p : phases) {
            if ((p.getProcessus().startsWith("UTILISATION_")) && p.getPhase().startsWith("Total")
                    && p.getMoyenneJours() != null) {
                somme += p.getMoyenneJours() * p.getNbDossiers();
                n += p.getNbDossiers();
            }
        }
        return n == 0 ? null : arrondi(somme / n);
    }

    private static Double tauxComplementsGlobal(List<IndicateursImpactDto.Processus> processus) {
        long deposes = processus.stream().mapToLong(IndicateursImpactDto.Processus::getDeposes).sum();
        long complements = processus.stream().mapToLong(IndicateursImpactDto.Processus::getAvecComplements).sum();
        return pct(complements, deposes);
    }

    private static double annees(Object[] ligne) {
        if (ligne == null || ligne[2] == null || ligne[3] == null) {
            return 0;
        }
        int debut = LocalDate.ofInstant((Instant) ligne[2], ZONE).getYear();
        int fin = LocalDate.ofInstant((Instant) ligne[3], ZONE).getYear();
        return fin - debut + 1;
    }

    private static String periode(Object[] ligne) {
        if (ligne == null || ligne[2] == null || ligne[3] == null) {
            return null;
        }
        int debut = LocalDate.ofInstant((Instant) ligne[2], ZONE).getYear();
        int fin = LocalDate.ofInstant((Instant) ligne[3], ZONE).getYear();
        return debut == fin ? String.valueOf(debut) : debut + "-" + fin;
    }

    private static BigDecimal parAn(BigDecimal valeur, double annees) {
        if (valeur == null || annees <= 0) {
            return null;
        }
        return valeur.divide(BigDecimal.valueOf(annees), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nombre(Object v) {
        return v instanceof Number n ? BigDecimal.valueOf(n.longValue()) : BigDecimal.ZERO;
    }

    private static BigDecimal montantDe(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    }

    private static BigDecimal decimal(Double v) {
        return v != null ? BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP) : null;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static double jours(Instant debut, Instant fin) {
        return Duration.between(debut, fin).toMinutes() / 1440.0;
    }

    private static Double moyenne(Collection<Double> valeurs) {
        if (valeurs.isEmpty()) {
            return null;
        }
        return arrondi(valeurs.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static Double mediane(List<Double> valeurs) {
        if (valeurs.isEmpty()) {
            return null;
        }
        List<Double> tri = new ArrayList<>(valeurs);
        tri.sort(Double::compare);
        int n = tri.size();
        double m = n % 2 == 1 ? tri.get(n / 2) : (tri.get(n / 2 - 1) + tri.get(n / 2)) / 2;
        return arrondi(m);
    }

    private static Double pct(long part, long total) {
        return total == 0 ? null : arrondi(100.0 * part / total);
    }

    private static Double pct(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return null;
        }
        return arrondi(100.0 * part.doubleValue() / total.doubleValue());
    }

    private static Double arrondi(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
