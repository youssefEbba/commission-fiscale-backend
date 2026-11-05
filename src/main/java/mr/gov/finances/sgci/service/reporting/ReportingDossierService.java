package mr.gov.finances.sgci.service.reporting;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.BlocReporting;
import mr.gov.finances.sgci.domain.enums.Granularite;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.reporting.model.ReportingDossier;
import mr.gov.finances.sgci.web.dto.reporting.AFermerDto;
import mr.gov.finances.sgci.web.dto.reporting.BlocagesDto;
import mr.gov.finances.sgci.web.dto.reporting.DelaiDirectionDto;
import mr.gov.finances.sgci.web.dto.reporting.DossierImmobileDto;
import mr.gov.finances.sgci.web.dto.reporting.EngagementEtatDto;
import mr.gov.finances.sgci.web.dto.reporting.EngagementLigneDto;
import mr.gov.finances.sgci.web.dto.reporting.IndicateursImpactDto;
import mr.gov.finances.sgci.web.dto.reporting.BeneficiairesDto;
import mr.gov.finances.sgci.web.dto.feedback.FeedbackDtos;
import mr.gov.finances.sgci.web.dto.reporting.LigneAFermerDto;
import mr.gov.finances.sgci.web.dto.reporting.ProductionPeriodeDto;
import mr.gov.finances.sgci.web.dto.reporting.ProductionPointDto;
import mr.gov.finances.sgci.web.dto.reporting.RejetTempOuvertDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Assemble un état de reporting dans une forme neutre, que les trois formats rendent ensuite.
 *
 * <p><b>Un seul assemblage, trois rendus.</b> C'est ce qui empêche l'export de dériver : le défaut
 * classique est qu'un fichier ait sa propre requête, juste au départ puis progressivement fausse à
 * mesure que l'écran évolue. Ici le tableur, le CSV et le PDF reçoivent le même objet, produit par
 * les mêmes services que les routes JSON.
 *
 * <p><b>Les règles d'affichage sont appliquées ici, une fois.</b> En particulier celle qui compte le
 * plus : <em>un nombre absent s'écrit « n/a », jamais « 0 »</em>. Pour un délai, zéro jour signifie
 * « visé le jour même » — un excellent résultat, l'inverse exact de « on ne sait pas ». Laisser
 * chaque format décider aurait produit trois conventions différentes pour un même état.
 *
 * <p>Les montants sont écrits sans séparateur de milliers et avec un point décimal, afin que le
 * rédacteur de tableur les reconnaisse comme des nombres et les rende sommables. Un montant écrit
 * en texte dans un classeur est un montant qu'on ne peut pas additionner.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportingDossierService {

    /** Ce qu'on écrit quand il n'y a pas de valeur. Jamais zéro. */
    public static final String ABSENT = "n/a";

    private static final DateTimeFormatter JOUR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter JOUR_HEURE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static final String MENTION_FIABILITE =
            "Les montants consommés sont mesurés sur les soldes des certificats, source de vérité du "
                    + "système. Le recalcul par les mouvements sert de contrôle et l'écart est publié, "
                    + "jamais compensé : il s'explique par les dossiers repris de l'ancienne "
                    + "application, dont les soldes ont été importés sans mouvements, et par les "
                    + "corrections manuelles d'administrateur. Les durées sont comptées depuis la "
                    + "dernière décision enregistrée, et non depuis l'entrée dans le statut courant, "
                    + "qu'aucune donnée ne permet encore de calculer.";

    private final EngagementEtatService engagementEtatService;
    private final BlocagesService blocagesService;
    private final AFermerService aFermerService;
    private final ProductionPeriodeService productionPeriodeService;
    private final IndicateursImpactService indicateursImpactService;
    private final BeneficiairesService beneficiairesService;
    private final mr.gov.finances.sgci.service.FeedbackService feedbackService;

    public ReportingDossier assembler(AuthenticatedUser auth, Instant from, Instant to,
                                      Long autoriteContractanteId, Long entrepriseId,
                                      Set<BlocReporting> blocs) {
        Set<BlocReporting> retenus = blocs == null || blocs.isEmpty()
                ? Set.of(BlocReporting.values())
                : blocs;

        List<ReportingDossier.Section> sections = new ArrayList<>();
        if (retenus.contains(BlocReporting.ENGAGEMENT)) {
            sections.add(sectionEngagement(engagementEtatService.parAxe(
                    auth, from, to, autoriteContractanteId, entrepriseId, null)));
        }
        if (retenus.contains(BlocReporting.BLOCAGES)) {
            sections.addAll(sectionsBlocages(blocagesService.blocages(
                    auth, from, to, autoriteContractanteId, entrepriseId, null)));
        }
        if (retenus.contains(BlocReporting.A_FERMER)) {
            sections.addAll(sectionsAFermer(aFermerService.aFermer(
                    auth, from, to, autoriteContractanteId, entrepriseId, null, null)));
        }
        if (retenus.contains(BlocReporting.PRODUCTION)) {
            sections.add(sectionProduction(productionPeriodeService.production(
                    auth, from, to, autoriteContractanteId, entrepriseId, Granularite.MOIS)));
        }

        if (retenus.contains(BlocReporting.IMPACT)) {
            sections.addAll(sectionsImpact(indicateursImpactService.indicateurs(
                    auth, from, to, autoriteContractanteId, entrepriseId)));
        }

        if (retenus.contains(BlocReporting.BENEFICIAIRES)) {
            sections.addAll(sectionsBeneficiaires(beneficiairesService.beneficiaires(
                    auth, from, to, autoriteContractanteId, entrepriseId)));
        }

        // La satisfaction n'est pas une donnée du périmètre métier : elle ne s'exporte que pour qui
        // lit son tableau de bord.
        if (retenus.contains(BlocReporting.SATISFACTION) && aLAutorite("feedback.dashboard.view")) {
            sections.addAll(sectionsSatisfaction(feedbackService.tableauDeBord(from, to)));
        }

        String perimetre = perimetre(autoriteContractanteId, entrepriseId);
        String fenetre = from != null && to != null
                ? "du " + JOUR.format(from) + " au " + JOUR.format(to)
                : "période par défaut (les 365 derniers jours)";

        return new ReportingDossier(
                new ReportingDossier.EnTete(
                        "Reporting du crédit d'impôt",
                        perimetre,
                        fenetre,
                        auth != null ? auth.getUsername() : ABSENT,
                        Instant.now(),
                        MENTION_FIABILITE),
                sections);
    }

    // ── l'engagement ───────────────────────────────────────────────────────────────────────────

    private ReportingDossier.Section sectionEngagement(EngagementEtatDto dto) {
        List<List<String>> lignes = new ArrayList<>();
        for (EngagementLigneDto l : dto.getLignes()) {
            lignes.add(ligneEngagement(l));
        }
        if (dto.getTotal() != null) {
            lignes.add(ligneEngagement(dto.getTotal()));
        }

        String note = "Trois enveloppes suivies séparément : les droits et taxes de douane hors TVA, "
                + "la TVA à l'import et la TVA intérieure. Le restant de TVA à l'import inclut le "
                + "stock de TVA déductible, sans quoi un transfert apparaîtrait consommé à 100 % "
                + "alors qu'il ne fait que reclasser. Écart de ventilation : "
                + montant(dto.getEcartVentilation()) + " sur " + dto.getNbEcartVentilation()
                + " dossier(s). Écart aux mouvements : " + montant(dto.getEcartMouvements())
                + ". Dossiers dont le restant dépasse l'accordé : " + dto.getNbRestantSuperieurAccorde()
                + " — légitime, le report à nouveau de TVA intérieure le permet.";

        return new ReportingDossier.Section(
                "Engagement de l'État par " + libelleAxe(dto),
                List.of("Clé", "Libellé", "Certificats",
                        "Accordé droits et taxes", "Restant droits et taxes", "Consommé droits et taxes",
                        "Accordé TVA import", "Restant TVA import", "Consommé TVA import",
                        "Accordé TVA intérieure", "Restant TVA intérieure", "Consommé TVA intérieure",
                        "Accordé total", "Restant total", "Consommé (soldes)",
                        "Consommé (mouvements)", "Écart", "Dossiers repris ou corrigés", "Fiabilité"),
                lignes, null, note);
    }

    private List<String> ligneEngagement(EngagementLigneDto l) {
        return Arrays.asList(
                texte(l.getCle()), texte(l.getLibelle()), String.valueOf(l.getNbCertificats()),
                montant(l.getAccordeCordonHorsTva()), montant(l.getRestantCordonHorsTva()),
                montant(l.getConsommeCordonHorsTva()),
                montant(l.getAccordeTvaImport()), montant(l.getRestantTvaImport()),
                montant(l.getConsommeTvaImport()),
                montant(l.getAccordeTvaInterieure()), montant(l.getRestantTvaInterieure()),
                montant(l.getConsommeTvaInterieure()),
                montant(l.getAccordeTotal()), montant(l.getRestantTotal()),
                montant(l.getConsommeSoldes()), montant(l.getConsommeMouvements()),
                montant(l.getEcartMouvements()),
                String.valueOf(l.getNbDossiersRepriseOuCorriges()),
                l.getFiabilite() != null ? l.getFiabilite().name() : ABSENT);
    }

    private String libelleAxe(EngagementEtatDto dto) {
        if (dto.getAxe() == null) {
            return "axe";
        }
        return switch (dto.getAxe()) {
            case AUTORITE_CONTRACTANTE -> "autorité contractante";
            case BAILLEUR -> "bailleur";
            case CONVENTION -> "convention";
            case ENTREPRISE -> "entreprise";
        };
    }

    // ── les blocages ───────────────────────────────────────────────────────────────────────────

    private List<ReportingDossier.Section> sectionsBlocages(BlocagesDto dto) {
        List<ReportingDossier.Section> sections = new ArrayList<>();

        List<List<String>> immobiles = new ArrayList<>();
        for (DossierImmobileDto d : dto.getDossiersImmobiles()) {
            immobiles.add(Arrays.asList(libelleType(d.getType()), String.valueOf(d.getId()),
                    texte(d.getReference()), texte(d.getStatut()),
                    date(d.getDateReference()), d.isDateDeRepli() ? "oui" : "non",
                    String.valueOf(d.getJoursDepuisDerniereDecision()),
                    texte(d.getEntrepriseRaisonSociale())));
        }
        sections.add(new ReportingDossier.Section(
                "Dossiers immobiles depuis plus de " + dto.getSeuilImmobiliteJours() + " jours",
                List.of("Type", "Identifiant", "Référence", "Statut", "Date de référence",
                        "Date de repli", "Jours", "Entreprise"),
                immobiles,
                troncature(dto.isDossiersImmobilesTronques(), immobiles.size(),
                        dto.getNbDossiersImmobiles()),
                dto.getDefinitionAge()));

        List<List<String>> rejets = new ArrayList<>();
        for (RejetTempOuvertDto r : dto.getRejetsTempOuverts()) {
            rejets.add(Arrays.asList(libelleType(r.getType()), String.valueOf(r.getDossierId()),
                    texte(r.getReference()), texte(r.getRole()), texte(r.getAgentUsername()),
                    date(r.getDateDecision()), String.valueOf(r.getJoursOuvert()),
                    texte(r.getMotifRejet())));
        }
        sections.add(new ReportingDossier.Section(
                "Rejets temporaires encore ouverts",
                List.of("Type", "Identifiant", "Référence", "Direction", "Agent",
                        "Date du rejet", "Jours ouverts", "Motif"),
                rejets,
                troncature(dto.isRejetsTempOuvertsTronques(), rejets.size(),
                        dto.getNbRejetsTempOuverts()),
                "Ancienneté du plus vieux rejet encore ouvert : "
                        + nombre(dto.getAnciennetePlusVieuxRejetJours()) + " jour(s). "
                        + "Aucune fenêtre temporelle n'est appliquée : un rejet ouvert est un état "
                        + "courant, non un événement de la période."));

        List<List<String>> delais = new ArrayList<>();
        String definitionDelai = null;
        for (DelaiDirectionDto d : dto.getDelaisParDirection()) {
            delais.add(Arrays.asList(texte(d.getCircuit()), texte(d.getRole()),
                    String.valueOf(d.getNbVisas()), decimal(d.getDelaiMoyenJours()),
                    nombre(d.getDelaiMaxJours()), String.valueOf(d.getNbVisasParAdmin())));
            if (definitionDelai == null) {
                definitionDelai = d.getDefinitionDelai();
            }
        }
        sections.add(new ReportingDossier.Section(
                "Délais par direction",
                List.of("Circuit", "Direction", "Visas", "Délai moyen (jours)",
                        "Délai maximal (jours)", "Visas par administrateur"),
                delais, null, definitionDelai));

        return sections;
    }

    // ── ce qu'il faut fermer ───────────────────────────────────────────────────────────────────

    private List<ReportingDossier.Section> sectionsAFermer(AFermerDto dto) {
        List<ReportingDossier.Section> sections = new ArrayList<>();

        sections.add(sectionLignes("Crédits échus avec un solde non nul",
                dto.getCertificatsExpiresAvecSolde(),
                troncature(dto.isCertificatsExpiresTronques(),
                        dto.getCertificatsExpiresAvecSolde().size(),
                        dto.getNbCertificatsExpiresAvecSolde()),
                "Montant total immobilisé : " + montant(dto.getMontantImmobiliseExpires())
                        + ". C'est ce chiffre qui justifie de s'en occuper."));

        sections.add(sectionLignes(
                "Crédits arrivant à échéance sous " + dto.getHorizonEcheanceJours() + " jours",
                dto.getCertificatsExpirantProchainement(), null, null));

        sections.add(sectionLignes("Crédits sans date de validité",
                dto.getCertificatsSansDateValidite(), null,
                "Anomalie de données, et non une liste vide à ignorer : un certificat sans date de "
                        + "validité n'échoit jamais, donc il n'alerte jamais."));

        sections.add(sectionLignes(
                "Conventions arrivant à échéance sous " + dto.getHorizonEcheanceJours() + " jours",
                dto.getConventionsAEcheance(), null, null));

        sections.add(sectionLignes("Matériel sous admission temporaire sur un crédit encore ouvert",
                dto.getEquipementsSurCreditOuvert(), null,
                "Dossiers où le régime n'a jamais été renseigné : "
                        + dto.getEquipementsNonRenseignes()
                        + ". Sans ce dénominateur, le nombre ci-dessus se lirait comme un inventaire "
                        + "complet."));

        sections.add(sectionLignes("Crédits dormants",
                dto.getCreditsDormants(),
                troncature(dto.isCreditsDormantsTronques(), dto.getCreditsDormants().size(),
                        dto.getNbCreditsDormants()),
                dto.getDefinitionDormance()));

        return sections;
    }

    private ReportingDossier.Section sectionLignes(String titre, List<LigneAFermerDto> source,
                                                   String troncature, String note) {
        List<List<String>> lignes = new ArrayList<>();
        for (LigneAFermerDto l : source) {
            lignes.add(Arrays.asList(libelleType(l.getType()), String.valueOf(l.getId()),
                    texte(l.getReference()), texte(l.getStatut()),
                    texte(l.getEntrepriseRaisonSociale()),
                    l.getDateValidite() != null ? date(l.getDateValidite())
                            : (l.getDateFin() != null ? l.getDateFin().toString() : ABSENT),
                    nombre(l.getJoursAvantEcheance()), montant(l.getRestantTotal()),
                    nombre(l.getJoursSansActivite())));
        }
        return new ReportingDossier.Section(titre,
                List.of("Type", "Identifiant", "Référence", "Statut", "Entreprise",
                        "Échéance", "Jours avant échéance", "Restant", "Jours sans activité"),
                lignes, troncature, note);
    }

    // ── la production ──────────────────────────────────────────────────────────────────────────

    private ReportingDossier.Section sectionProduction(ProductionPeriodeDto dto) {
        List<List<String>> lignes = new ArrayList<>();
        for (ProductionPointDto p : dto.getPoints()) {
            lignes.add(Arrays.asList(texte(p.getPeriode()),
                    String.valueOf(p.getNbDemandes()), String.valueOf(p.getNbCertificats()),
                    String.valueOf(p.getNbUtilisations()),
                    montant(p.getMasseAccordee()), montant(p.getConsommeMouvementsPeriode())));
        }
        String note = dto.getCleDatation()
                + " Variation par rapport à la période précédente, de même durée — demandes : "
                + pourcentage(dto.getVariationDemandesPct()) + ", certificats : "
                + pourcentage(dto.getVariationCertificatsPct()) + ", masse accordée : "
                + pourcentage(dto.getVariationMassePct())
                + ". Une variation « n/a » signifie que la période précédente est vide : il n'y a pas "
                + "de référence, ce qui n'est pas un effondrement.";

        return new ReportingDossier.Section("Production par mois",
                List.of("Période", "Demandes", "Certificats", "Utilisations",
                        "Masse accordée", "Consommé sur mouvements"),
                lignes, null, note);
    }

    // ── l'impact de la digitalisation ──────────────────────────────────────────────────────────

    private List<ReportingDossier.Section> sectionsImpact(IndicateursImpactDto dto) {
        List<ReportingDossier.Section> out = new ArrayList<>();

        List<List<String>> processus = new ArrayList<>();
        for (IndicateursImpactDto.Processus p : dto.getProcessus()) {
            processus.add(Arrays.asList(texte(p.getLibelle()), String.valueOf(p.getDeposes()),
                    String.valueOf(p.getTraites()), String.valueOf(p.getValides()), String.valueOf(p.getRejetes()),
                    pourcentage(p.getTauxValidationPct()), pourcentage(p.getTauxRejetPct()),
                    pourcentage(p.getTauxComplementsPct()), decimal(p.getDelaiReponseComplementsJours())));
        }
        out.add(new ReportingDossier.Section("Impact : demandes traitées et taux de décision",
                List.of("Processus", "Déposés", "Traités", "Validés", "Rejetés", "Taux de validation",
                        "Taux de rejet", "Taux de compléments", "Réponse aux compléments (jours)"),
                processus, null, String.join(" ", dto.getDefinitions().subList(0, 3))));

        List<List<String>> phases = new ArrayList<>();
        for (IndicateursImpactDto.Phase p : dto.getPhases()) {
            phases.add(Arrays.asList(texte(p.getProcessus()), texte(p.getPhase()),
                    String.valueOf(p.getNbDossiers()), decimal(p.getMoyenneJours()), decimal(p.getMedianeJours())));
        }
        out.add(new ReportingDossier.Section("Impact : délais moyens de traitement par phase",
                List.of("Processus", "Phase", "Dossiers", "Moyenne (jours)", "Médiane (jours)"),
                phases, null, dto.getDefinitions().get(3)));

        IndicateursImpactDto.Montants m = dto.getMontants();
        IndicateursImpactDto.TauxUtilisation t = dto.getTauxUtilisation();
        List<List<String>> montants = new ArrayList<>();
        montants.add(Arrays.asList("Crédits demandés", montant(m.getDemandes())));
        montants.add(Arrays.asList("Crédits retenus par la Commission", montant(m.getRetenus())));
        montants.add(Arrays.asList("Crédits validés (ouverts)", montant(m.getValides())));
        montants.add(Arrays.asList("Crédits consommés", montant(m.getConsommes())));
        montants.add(Arrays.asList("Crédits ajustés (avenants)", montant(m.getAjustes())));
        montants.add(Arrays.asList("Crédits annulés", montant(m.getAnnules())));
        montants.add(Arrays.asList("Soldes à la clôture", montant(m.getClotures())));
        montants.add(Arrays.asList("Taux d'utilisation TVA intérieure", pourcentage(t.getTvaInterieurePct())));
        montants.add(Arrays.asList("Taux d'utilisation Douanes", pourcentage(t.getDouanePct())));
        montants.add(Arrays.asList("Taux d'utilisation global", pourcentage(t.getGlobalPct())));
        out.add(new ReportingDossier.Section("Impact : montants et utilisation effective des crédits",
                List.of("Indicateur", "Valeur"), montants, null,
                dto.getDefinitions().get(4) + " " + dto.getDefinitions().get(5) + " " + dto.getDefinitions().get(6)));

        List<List<String>> comparaisons = new ArrayList<>();
        for (IndicateursImpactDto.Comparaison c : dto.getAvantApres()) {
            comparaisons.add(Arrays.asList(texte(c.getLibelle()), texte(c.getUnite()), montant(c.getAvant()),
                    montant(c.getApres()), pourcentage(c.getVariationPct()), texte(c.getSourceAvant()),
                    texte(c.getPeriodeAvant())));
        }
        out.add(new ReportingDossier.Section("Impact : avant et après la digitalisation",
                List.of("Indicateur", "Unité", "Avant", "Après", "Variation", "Source de l'avant", "Période de l'avant"),
                comparaisons, null, dto.getDefinitions().get(7)
                        + (dto.getDateBascule() != null ? " Date de bascule : " + dto.getDateBascule() + "." : "")));
        return out;
    }

    // ── les bénéficiaires ──────────────────────────────────────────────────────────────────────

    private List<ReportingDossier.Section> sectionsBeneficiaires(BeneficiairesDto dto) {
        List<List<String>> ventilations = new ArrayList<>();
        ventilations.add(Arrays.asList("Total", "Tous les bénéficiaires", String.valueOf(dto.getTotalBeneficiaires()),
                String.valueOf(dto.getTotalCredits()), montant(dto.getMontantAccorde()), montant(dto.getMontantConsomme())));
        ajouterVentilations(ventilations, "Nature", dto.getParNature());
        ajouterVentilations(ventilations, "Catégorie", dto.getParCategorie());
        ajouterVentilations(ventilations, "Secteur", dto.getParSecteur());
        ajouterVentilations(ventilations, "Financement", dto.getParFinancement());

        List<List<String>> annees = new ArrayList<>();
        for (BeneficiairesDto.Annee a : dto.getParAnnee()) {
            annees.add(Arrays.asList(String.valueOf(a.getAnnee()), String.valueOf(a.getNouveauxBeneficiaires()),
                    String.valueOf(a.getBeneficiaires()), String.valueOf(a.getNbCredits()),
                    montant(a.getMontantAccorde()), montant(a.getMontantConsomme())));
        }
        return List.of(
                new ReportingDossier.Section("Bénéficiaires par nature, catégorie, secteur et financement",
                        List.of("Axe", "Valeur", "Bénéficiaires", "Crédits", "Montant accordé", "Montant consommé"),
                        ventilations, null, String.join(" ", dto.getDefinitions())),
                new ReportingDossier.Section("Bénéficiaires par année fiscale",
                        List.of("Année", "Nouveaux bénéficiaires", "Bénéficiaires", "Crédits",
                                "Montant accordé", "Montant consommé"),
                        annees, null, dto.getDefinitions().get(1)));
    }

    private static void ajouterVentilations(List<List<String>> lignes, String axe,
                                            List<BeneficiairesDto.Ventilation> ventilations) {
        for (BeneficiairesDto.Ventilation v : ventilations) {
            lignes.add(Arrays.asList(axe, texte(v.getLibelle()), String.valueOf(v.getNbBeneficiaires()),
                    String.valueOf(v.getNbCredits()), montant(v.getMontantAccorde()), montant(v.getMontantConsomme())));
        }
    }

    // ── la satisfaction des usagers ────────────────────────────────────────────────────────────

    private List<ReportingDossier.Section> sectionsSatisfaction(FeedbackDtos.TableauDeBord t) {
        List<List<String>> agregats = new ArrayList<>();
        agregats.add(Arrays.asList("Ensemble", "Tous les avis", String.valueOf(t.getNbAvis()),
                montant(t.getNoteMoyenne()), pourcentage(t.getSatisfaitsPct())));
        ajouterAgregats(agregats, "Profil", t.getParProfil());
        ajouterAgregats(agregats, "Sujet", t.getParCategorie());
        ajouterAgregats(agregats, "Processus", t.getParProcessus());

        List<List<String>> enquetes = new ArrayList<>();
        for (FeedbackDtos.ResultatEnquete e : t.getEnquetes()) {
            enquetes.add(Arrays.asList(texte(e.getTitre()), e.getDateDebut() + " – " + e.getDateFin(),
                    String.valueOf(e.getNbReponses()), String.valueOf(e.getNbCibles()),
                    pourcentage(e.getTauxReponsePct()), montant(e.getNoteMoyenne())));
        }

        List<List<String>> commentaires = new ArrayList<>();
        for (FeedbackDtos.Commentaire c : t.getCommentaires()) {
            commentaires.add(Arrays.asList(date(c.getDate()), c.getProfil() != null ? c.getProfil().name() : ABSENT,
                    c.getCategorie() != null ? c.getCategorie().name() : ABSENT,
                    c.getNote() != null ? String.valueOf(c.getNote()) : ABSENT, texte(c.getSource()), texte(c.getTexte())));
        }

        String forts = t.getPointsForts().stream().map(FeedbackDtos.Agregat::getLibelle).reduce((a, b) -> a + ", " + b).orElse("aucun");
        String difficiles = t.getDifficultes().stream().map(FeedbackDtos.Agregat::getLibelle).reduce((a, b) -> a + ", " + b).orElse("aucune");
        return List.of(
                new ReportingDossier.Section("Satisfaction des usagers",
                        List.of("Axe", "Valeur", "Notes", "Note moyenne (sur 5)", "Satisfaits (4 ou 5)"),
                        agregats, null, "Points forts : " + forts + ". Difficultés : " + difficiles
                        + ". Avis spontanés et réponses aux enquêtes, notes de 1 à 5."),
                new ReportingDossier.Section("Enquêtes de satisfaction",
                        List.of("Enquête", "Période", "Réponses", "Usagers ciblés", "Taux de réponse", "Note moyenne"),
                        enquetes, null, null),
                new ReportingDossier.Section("Commentaires des usagers",
                        List.of("Date", "Profil", "Sujet", "Note", "Source", "Commentaire"),
                        commentaires, null, "Les commentaires sont présentés avec le profil de leur auteur, sans son nom."));
    }

    private static void ajouterAgregats(List<List<String>> lignes, String axe, List<FeedbackDtos.Agregat> agregats) {
        for (FeedbackDtos.Agregat a : agregats) {
            lignes.add(Arrays.asList(axe, texte(a.getLibelle()), String.valueOf(a.getNbNotes()),
                    montant(a.getNoteMoyenne()), pourcentage(a.getSatisfaitsPct())));
        }
    }

    private static boolean aLAutorite(String autorite) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities() != null
                && auth.getAuthorities().stream().anyMatch(a -> autorite.equals(a.getAuthority()));
    }

    // ── mise en forme, une seule fois pour les trois formats ───────────────────────────────────

    private static String troncature(boolean tronque, int rendues, long total) {
        return tronque ? rendues + " lignes affichées sur " + total : null;
    }

    private static String perimetre(Long acId, Long entId) {
        if (acId != null && entId != null) {
            return "autorité contractante " + acId + ", entreprise " + entId;
        }
        if (acId != null) {
            return "autorité contractante " + acId;
        }
        if (entId != null) {
            return "entreprise " + entId;
        }
        return "périmètre de l'utilisateur";
    }

    /** Type de dossier en clair : l'état est lu en séance, pas un nom de classe (« CertificatCredit »). */
    static String libelleType(String type) {
        if (type == null || type.isBlank()) {
            return ABSENT;
        }
        return switch (type) {
            case "CertificatCredit" -> "Crédit";
            case "DemandeCorrection" -> "Demande de correction";
            case "UtilisationCredit" -> "Utilisation";
            case "TransfertCredit" -> "Transfert";
            case "Convention" -> "Convention";
            case "Marche" -> "Marché";
            case "ModificationCredit" -> "Avenant";
            case "SousTraitance" -> "Sous-traitance";
            case "ClotureCredit" -> "Clôture";
            default -> type;
        };
    }

    private static String texte(String v) {
        return v != null && !v.isBlank() ? v : ABSENT;
    }

    private static String date(Instant v) {
        return v != null ? JOUR_HEURE.format(v) : ABSENT;
    }

    /** Point décimal et aucun séparateur de milliers : le tableur doit y voir un nombre. */
    private static String montant(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP).toPlainString() : ABSENT;
    }

    private static String decimal(Double v) {
        return v != null ? BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString()
                : ABSENT;
    }

    private static String nombre(Long v) {
        return v != null ? String.valueOf(v) : ABSENT;
    }

    private static String pourcentage(Double v) {
        return v != null ? decimal(v) + " %" : ABSENT;
    }
}
