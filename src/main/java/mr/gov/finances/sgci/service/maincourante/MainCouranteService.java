package mr.gov.finances.sgci.service.maincourante;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.DecisionCorrectionType;
import mr.gov.finances.sgci.domain.enums.RejetTempStatus;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutAvenant;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutConvention;
import mr.gov.finances.sgci.domain.enums.StatutSousTraitance;
import mr.gov.finances.sgci.domain.enums.StatutDemandeRattachement;
import mr.gov.finances.sgci.domain.enums.StatutDemandeResetPassword;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.domain.enums.StatutDemande;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ClotureCreditService;
import mr.gov.finances.sgci.service.DecisionTransfertCreditService;
import mr.gov.finances.sgci.service.PermissionService;
import mr.gov.finances.sgci.service.reporting.ReportingPredicates;
import mr.gov.finances.sgci.service.reporting.ReportingQueryRunner;
import mr.gov.finances.sgci.service.reporting.ReportingScope;
import mr.gov.finances.sgci.service.reporting.ReportingScopeFactory;
import mr.gov.finances.sgci.web.dto.CertificatClotureQueueItemDto;
import mr.gov.finances.sgci.web.dto.maincourante.LigneMainCouranteDto;
import mr.gov.finances.sgci.web.dto.maincourante.MainCouranteDto;
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
 * La main courante : ce qui attend l'utilisateur connecté, tous processus confondus.
 *
 * <p>Remplace le tableau de bord, et ne répond pas à la même question que le bloc « blocages » du
 * reporting. Celui-ci dit <em>où le système est arrêté</em>, vu de haut, et sert au pilotage ; la
 * main courante dit <em>ce que moi je dois faire maintenant</em>. Un dossier arrêté chez quelqu'un
 * d'autre est un blocage, pas une tâche — et les confondre produirait une liste de travail où
 * l'agent cherche en vain ce qu'il peut faire.
 *
 * <p><b>Une seule définition, côté serveur.</b> Les files de travail n'existaient qu'à l'écran, qui
 * les reconstituait à partir de listes filtrées : autant de définitions de « nécessite une
 * intervention » que de composants, et aucune testable. Elle vit désormais ici.
 *
 * <p><b>Le périmètre est celui du reporting, délibérément réutilisé.</b> {@link ReportingScopeFactory}
 * et {@link ReportingPredicates} définissent déjà « ce que ce rôle a le droit de voir », avec un
 * périmètre fermé pour un rôle sans rattachement, et ils sont verrouillés par des tests. En écrire
 * un troisième aurait garanti qu'il divergerait. La fenêtre temporelle, en revanche, n'est pas
 * appliquée : une tâche en attente depuis deux ans est toujours une tâche.
 *
 * <p><b>Ce qui n'est pas couvert est publié.</b> Les utilisations de crédit comptent dix-huit
 * statuts et aucun arbitre canonique du « à qui le tour » : encoder la règle ici reviendrait à
 * l'inventer, et une règle fausse ferait manquer du travail à un agent qui s'y fie. Le processus est
 * donc nommé dans la réponse comme non couvert, plutôt que silencieusement absent.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MainCouranteService {

    private static final int MAX_LIGNES = 300;

    private static final String DEFINITION_ATTENTE =
            "Jours écoulés depuis la dernière étape connue du dossier — son dépôt, son émission, "
                    + "la proposition de clôture ou la décision de rejet selon le cas. Ce n'est pas "
                    + "l'ancienneté dans le statut actuel, qu'aucune donnée ne permet encore de "
                    + "calculer.";

    /**
     * Le compte n'est rattaché à rien, donc aucune tâche ne peut lui échoir.
     *
     * <p>Deux chemins y mènent : une autorité contractante sans rattachement, pour qui le cadrage
     * lève, et une entreprise sans rattachement, pour qui il rend un périmètre fermé. Les deux
     * produisent une liste vide — et une liste vide sans explication se lit « rien à faire », alors
     * que le compte est inutilisable. D'où ce message, dans les deux cas.
     */
    private static final String COMPTE_NON_RATTACHE =
            "Votre compte n'est rattaché à aucune entité : aucune tâche ne peut vous être attribuée "
                    + "tant que ce rattachement n'est pas fait. Signalez-le à l'administrateur du système.";

    /**
     * Les directions qui visent une demande de correction, et le drapeau que la validation
     * parallèle lève encore pour leur visa.
     */
    private static final Map<Role, String> COLONNE_VISA_CORRECTION = Map.of(
            Role.DGD, "validationDgd",
            Role.DGTCP, "validationDgtcp",
            Role.DGI, "validationDgi",
            Role.DGB, "validationDgb");

    /** Les directions qui visent une mise en place : la DGB n'en fait pas partie (anomalie A28). */
    private static final Set<Role> VISA_CERTIFICAT = Set.of(Role.DGI, Role.DGD, Role.DGTCP);

    /**
     * Les statuts d'une demande où un visa de direction est encore attendu. EN_VALIDATION n'en est
     * pas : la demande y arrive précisément quand tous les visas sont posés, et la proposer encore
     * aux directions leur demandait un visa déjà donné (anomalie A17).
     */
    private static final Set<StatutDemande> DEMANDE_EN_INSTRUCTION =
            Set.of(StatutDemande.RECUE, StatutDemande.RECEVABLE, StatutDemande.EN_EVALUATION);

    /** Les statuts d'une mise en place où les visas des directions sont attendus. */
    private static final Set<StatutCertificat> CERTIFICAT_EN_CONTROLE =
            Set.of(StatutCertificat.EN_CONTROLE, StatutCertificat.A_RECONTROLER);

    /** Les statuts d'un transfert terminé : plus aucun visa n'y est attendu (anomalie A17). */
    private static final Set<StatutTransfert> TRANSFERT_TERMINE =
            Set.of(StatutTransfert.TRANSFERE, StatutTransfert.REJETE, StatutTransfert.ANNULEE);

    private final ReportingScopeFactory scopeFactory;
    private final ReportingPredicates predicates;
    private final ReportingQueryRunner runner;
    private final ClotureCreditService clotureCreditService;
    private final PermissionService permissionService;

    public MainCouranteDto pourMoi(AuthenticatedUser auth) {
        ReportingScope scope;
        try {
            // Pas de fenêtre temporelle : une tâche en attente depuis deux ans est toujours une tâche.
            scope = scopeFactory.build(auth, Instant.EPOCH, Instant.now(), null, null);
        } catch (IllegalStateException e) {
            // Un compte dont le rôle suppose un rattachement mais qui n'en a pas. Le cadrage lève,
            // et c'est juste pour le reporting : y afficher des zéros laisserait croire à une
            // activité nulle. Mais la main courante est la page d'accueil — la laisser échouer
            // donne un mur blanc et un « impossible de charger » qui ne dit rien de la cause.
            // On rend donc une liste vide qui NOMME le problème, plutôt que de le taire.
            return MainCouranteDto.builder()
                    .definitionAttente(DEFINITION_ATTENTE)
                    .processusNonCouverts(List.of(COMPTE_NON_RATTACHE))
                    .build();
        }
        Role role = auth != null ? auth.getRole() : null;
        Instant maintenant = Instant.now();

        List<LigneMainCouranteDto> lignes = new ArrayList<>();
        List<String> couverts = new ArrayList<>();

        if (COLONNE_VISA_CORRECTION.containsKey(role)) {
            lignes.addAll(demandesAViser(scope, role, maintenant));
            couverts.add("CORRECTION");
        }
        if (VISA_CERTIFICAT.contains(role)) {
            // Une mise en place envoyée attend d'être prise en charge par l'une de ces directions avant
            // tout visa : sans cette ligne, elle n'apparaissait dans aucune main courante.
            lignes.addAll(certificatsAuStatut(scope, maintenant, StatutCertificat.ENVOYEE,
                    "Prendre en charge la mise en place"));
            lignes.addAll(certificatsAViser(scope, role, maintenant));
            couverts.add("MISE_EN_PLACE");
        }
        if (role == Role.PRESIDENT) {
            // Les propositions de clôture attendent la décision du Président (absentes jusqu'ici).
            lignes.addAll(cloturesADecider(scope, maintenant));
            lignes.addAll(demandesAAdopter(scope, maintenant));
            lignes.addAll(certificatsAValider(scope, maintenant));
            lignes.addAll(avenantsAuStatut(scope, maintenant, StatutAvenant.EN_COURS_TRAITEMENT, true,
                    "Valider ou rejeter l'avenant"));
            couverts.add("CORRECTION");
            couverts.add("MISE_EN_PLACE");
            couverts.add("AVENANT");
        }
        if (role == Role.DGTCP) {
            lignes.addAll(certificatsAOuvrir(scope, maintenant));
            lignes.addAll(avenantsAuStatut(scope, maintenant, StatutAvenant.DEPOSE, false,
                    "Prendre en charge l'avenant"));
            lignes.addAll(avenantsAuStatut(scope, maintenant, StatutAvenant.ADOPTE, false,
                    "Appliquer l'avenant au crédit"));
            couverts.add("AVENANT");
        }
        if (role != null && DecisionTransfertCreditService.ORDRE_VISAS.contains(role)) {
            lignes.addAll(transfertsAViser(scope, role, maintenant));
            couverts.add("TRANSFERT");
        }
        if (role == Role.ENTREPRISE || role == Role.SOUS_TRAITANT
                || role == Role.AUTORITE_CONTRACTANTE || role == Role.AUTORITE_UPM
                || role == Role.AUTORITE_UEP) {
            // Le déposant, quel qu'il soit : l'entreprise dépose ses utilisations, l'autorité
            // contractante ses demandes de correction. Les deux reçoivent des demandes de pièce.
            lignes.addAll(mesRejetsAResoudre(scope, maintenant));
            lignes.addAll(mesBrouillonsASoumettre(scope, maintenant));
            couverts.add("REJET_TEMPORAIRE");
            couverts.add("BROUILLON");
        }
        if (role != null && EtapesUtilisation.intervientDansLeCircuit(role)) {
            lignes.addAll(utilisationsAttendantLeRole(scope, role, maintenant));
            couverts.add("UTILISATION");
        }
        if (role != null && role != Role.ADMIN_SI
                && permissionService.findPermissionCodesByRole(role).contains("convention.validate")) {
            // La DGB et la DGI valident les conventions : sans cette ligne, une convention déposée
            // n'apparaissait dans aucune main courante.
            lignes.addAll(conventionsAValider(scope, maintenant));
            couverts.add("CONVENTION");
        }
        if (role != null && role != Role.ADMIN_SI
                && permissionService.findPermissionCodesByRole(role).contains("sous_traitance.dgtcp.update")) {
            // Une demande de sous-traitance attend l'autorisation de la DGTCP : elle n'apparaissait nulle part.
            lignes.addAll(sousTraitancesADecider(scope));
            couverts.add("SOUS_TRAITANCE");
        }
        if (role == Role.ADMIN_SI || role == Role.PRESIDENT) {
            lignes.addAll(demandesAdministratives(scope, maintenant));
            couverts.add("ADMINISTRATION");
        }
        if (role == Role.DGTCP) {
            // La file de clôture n'est servie qu'à la DGTCP, et elle le fait savoir en levant.
            // Ne pas l'appeler hors de ce rôle est la seule façon correcte de s'en abstenir :
            // voir le commentaire de cloturesAApprouver.
            lignes.addAll(cloturesAApprouver(auth, maintenant));
            lignes.addAll(cloturesAFinaliser(scope, maintenant));
            couverts.add("CLOTURE");
        }

        lignes.sort(Comparator.comparingLong(LigneMainCouranteDto::getJoursDAttente).reversed());

        Map<String, Long> parProcessus = new LinkedHashMap<>();
        for (LigneMainCouranteDto l : lignes) {
            parProcessus.merge(l.getProcessus(), 1L, Long::sum);
        }

        boolean tronquee = lignes.size() > MAX_LIGNES;
        List<String> nonCouverts = new ArrayList<>();
        if (scope.perimetreVide()) {
            // L'autre chemin vers une liste vide : le cadrage n'a pas levé, il s'est fermé. Sans
            // ce message, l'écran dirait « rien n'attend votre intervention » à quelqu'un dont le
            // compte ne peut rien recevoir.
            nonCouverts.add(COMPTE_NON_RATTACHE);
        }

        return MainCouranteDto.builder()
                .lignes(tronquee ? new ArrayList<>(lignes.subList(0, MAX_LIGNES)) : lignes)
                .total(lignes.size())
                .parProcessus(parProcessus)
                .processusCouverts(couverts.stream().distinct().toList())
                .processusNonCouverts(nonCouverts)
                .definitionAttente(DEFINITION_ATTENTE)
                .tronquee(tronquee)
                .build();
    }

    // ── les demandes de correction ─────────────────────────────────────────────────────────────

    /**
     * En instruction, et le visa de ma direction n'est pas encore posé.
     *
     * <p>Le visa se lit sur {@code DecisionCorrection}, que le circuit écrit, et aussi sur les
     * drapeaux {@code validationXxx}, que la validation parallèle tient à jour. Ne lire que les
     * drapeaux laissait « à viser » une demande visée par le circuit (anomalie A17). Le séquencement de
     * {@code VisaRequirementResolver.firstVisaRoleForCorrection} est repris : quand la DGD (crédit
     * extérieur) ou la DGI (crédit intérieur seul) doit viser en premier, les autres directions
     * n'ont pas encore la main.
     */
    private List<LigneMainCouranteDto> demandesAViser(ReportingScope scope, Role role, Instant maintenant) {
        StringBuilder hql = new StringBuilder()
                .append("select d.id, coalesce(d.reference, d.numero), d.statut, ent.raisonSociale, ")
                .append("coalesce(d.dateDepot, d.dateCreation) ")
                .append("from DemandeCorrection d left join d.entreprise ent ")
                .append("where ").append(predicates.demande("d", scope, false))
                .append("   and d.statut in (:enInstruction)")
                .append("   and d.").append(COLONNE_VISA_CORRECTION.get(role)).append(" = false")
                .append("   and not exists (select 1 from DecisionCorrection dec")
                .append("                     where dec.demandeCorrection = d and dec.role = :role")
                .append("                       and dec.decision = :visa)");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("enInstruction", DEMANDE_EN_INSTRUCTION);
        params.put("role", role);
        params.put("visa", DecisionCorrectionType.VISA);
        if (role != Role.DGD) {
            hql.append("   and (coalesce(d.creditExterieur, 0) <= 0")
                    .append("        or exists (select 1 from DecisionCorrection dgd")
                    .append("                    where dgd.demandeCorrection = d and dgd.role = :dgd")
                    .append("                      and dgd.decision = :visa))");
            params.put("dgd", Role.DGD);
        }
        if (role != Role.DGI) {
            hql.append("   and (coalesce(d.creditExterieur, 0) > 0 or coalesce(d.creditInterieur, 0) <= 0")
                    .append("        or exists (select 1 from DecisionCorrection dgi")
                    .append("                    where dgi.demandeCorrection = d and dgi.role = :dgi")
                    .append("                      and dgi.decision = :visa))");
            params.put("dgi", Role.DGI);
        }
        return lignes(hql.toString(), scope, params, maintenant,
                "CORRECTION", "DemandeCorrection", "Poser le visa " + role.name());
    }

    private List<LigneMainCouranteDto> demandesAAdopter(ReportingScope scope, Instant maintenant) {
        String hql = "select d.id, coalesce(d.reference, d.numero), d.statut, ent.raisonSociale, "
                + "coalesce(d.dateDepot, d.dateCreation) "
                + "from DemandeCorrection d left join d.entreprise ent "
                + "where " + predicates.demande("d", scope, false)
                + "   and d.statut = :statut";
        return lignes(hql, scope, Map.of("statut", StatutDemande.EN_VALIDATION), maintenant,
                "CORRECTION", "DemandeCorrection", "Adopter ou rejeter la demande");
    }

    // ── les certificats ────────────────────────────────────────────────────────────────────────

    /**
     * En contrôle, et ma direction n'a pas encore posé son visa.
     *
     * <p>L'absence de visa se lit sur {@code DecisionCertificatCredit} et non sur un drapeau : c'est
     * cette table qui porte la décision, avec son auteur et son horodatage.
     *
     * <p>Les directions requises suivent {@code VisaRequirementResolver.requiredRolesForCertificat} :
     * la DGI n'intervient que s'il y a un crédit intérieur, la DGD que s'il y a un crédit extérieur
     * (sans crédit renseigné, les deux).
     */
    private List<LigneMainCouranteDto> certificatsAViser(ReportingScope scope, Role role, Instant maintenant) {
        String requis = switch (role) {
            case DGI -> "   and (coalesce(dc.creditInterieur, 0) > 0 or coalesce(dc.creditExterieur, 0) <= 0)";
            case DGD -> "   and (coalesce(dc.creditExterieur, 0) > 0 or coalesce(dc.creditInterieur, 0) <= 0)";
            default -> "";
        };
        String hql = "select c.id, coalesce(c.reference, c.numero), c.statut, ent.raisonSociale, "
                + "c.dateEmission "
                + "from CertificatCredit c left join c.entreprise ent left join c.demandeCorrection dc "
                + "where " + predicates.certificat("c", scope, false)
                + "   and c.statut in (:statuts)"
                + requis
                + "   and not exists (select 1 from DecisionCertificatCredit dec"
                + "                     where dec.certificatCredit = c and dec.role = :role"
                + "                       and dec.decision = :visa)";
        return lignes(hql, scope,
                Map.of("statuts", CERTIFICAT_EN_CONTROLE, "role", role,
                        "visa", DecisionCorrectionType.VISA),
                maintenant, "MISE_EN_PLACE", "CertificatCredit", "Poser le visa " + role.name());
    }

    private List<LigneMainCouranteDto> certificatsAValider(ReportingScope scope, Instant maintenant) {
        return certificatsAuStatut(scope, maintenant, StatutCertificat.EN_VALIDATION_PRESIDENT,
                "Valider ou rejeter le certificat");
    }

    private List<LigneMainCouranteDto> certificatsAOuvrir(ReportingScope scope, Instant maintenant) {
        return certificatsAuStatut(scope, maintenant, StatutCertificat.EN_OUVERTURE_DGTCP,
                "Ouvrir le crédit");
    }

    private List<LigneMainCouranteDto> certificatsAuStatut(ReportingScope scope, Instant maintenant,
                                                           StatutCertificat statut, String action) {
        String hql = "select c.id, coalesce(c.reference, c.numero), c.statut, ent.raisonSociale, "
                + "c.dateEmission "
                + "from CertificatCredit c left join c.entreprise ent "
                + "where " + predicates.certificat("c", scope, false)
                + "   and c.statut = :statut";
        return lignes(hql, scope, Map.of("statut", statut), maintenant,
                "MISE_EN_PLACE", "CertificatCredit", action);
    }

    // ── les avenants ───────────────────────────────────────────────────────────────────────────

    /**
     * Les avenants qui attendent ce rôle (anomalie A22) : la DGTCP les prend en charge (DEPOSE) puis
     * les applique (ADOPTE) ; le Président valide ceux qui ne passent pas par une demande de
     * correction (EN_COURS_TRAITEMENT, sans demande liée). Les avenants financiers suivent le
     * circuit de leur demande, déjà présent dans la main courante.
     */
    private List<LigneMainCouranteDto> avenantsAuStatut(ReportingScope scope, Instant maintenant,
                                                        StatutAvenant statut, boolean sansDemande,
                                                        String action) {
        String hql = "select a.id, a.numero, a.statut, ent.raisonSociale, coalesce(a.dateDepot, a.dateSignature) "
                + "from Avenant a join a.certificatCredit c left join c.entreprise ent "
                + "where " + predicates.certificat("c", scope, false)
                + "   and a.statut = :statut"
                + (sansDemande ? "   and a.demandeCorrection is null" : "");
        return lignes(hql, scope, Map.of("statut", statut), maintenant, "AVENANT", "Avenant", action);
    }

    // ── les transferts ─────────────────────────────────────────────────────────────────────────

    /**
     * Mon rang est atteint : tous mes prédécesseurs ont visé, et moi pas encore.
     *
     * <p>L'ordre vient de {@code DecisionTransfertCreditService.ORDRE_VISAS}, la seule définition
     * du circuit dans le dépôt. Un transfert dont un prédécesseur manque n'est pas ma tâche : il
     * figure dans les blocages, pas ici.
     */
    private List<LigneMainCouranteDto> transfertsAViser(ReportingScope scope, Role role, Instant maintenant) {
        List<Role> ordre = DecisionTransfertCreditService.ORDRE_VISAS;
        int rang = ordre.indexOf(role);
        List<Role> precedents = ordre.subList(0, rang);

        StringBuilder hql = new StringBuilder()
                .append("select t.id, concat(coalesce(cert.reference, cert.numero), ' — transfert n° ', cast(t.id as string)), ")
                .append("t.statut, ent.raisonSociale, t.dateDemande ")
                .append("from TransfertCredit t left join t.certificatCredit cert left join cert.entreprise ent ")
                .append("where ").append(predicates.transfert("t", scope, false))
                .append("   and t.statut not in (:termines)")
                .append("   and not exists (select 1 from DecisionTransfertCredit rej")
                .append("                     where rej.transfertCredit = t and rej.decision = :rejetTemp")
                .append("                       and rej.rejetTempStatus = :ouvert)")
                .append("   and not exists (select 1 from DecisionTransfertCredit dec")
                .append("                     where dec.transfertCredit = t and dec.role = :role")
                .append("                       and dec.decision = :visa)");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("role", role);
        params.put("visa", DecisionCorrectionType.VISA);
        params.put("termines", TRANSFERT_TERMINE);
        params.put("rejetTemp", DecisionCorrectionType.REJET_TEMP);
        params.put("ouvert", RejetTempStatus.OUVERT);
        for (int i = 0; i < precedents.size(); i++) {
            String p = "precedent" + i;
            hql.append("   and exists (select 1 from DecisionTransfertCredit dec").append(i)
                    .append("                 where dec").append(i).append(".transfertCredit = t")
                    .append("                   and dec").append(i).append(".role = :").append(p)
                    .append("                   and dec").append(i).append(".decision = :visa)");
            params.put(p, precedents.get(i));
        }
        return lignes(hql.toString(), scope, params, maintenant,
                "TRANSFERT", "TransfertCredit", "Poser le visa " + role.name());
    }

    // ── les propositions de clôture ─────────────────────────────────────────────────────────────

    /** Proposition déposée par la DGTCP, pas encore décidée par le Président. */
    private List<LigneMainCouranteDto> cloturesADecider(ReportingScope scope, Instant maintenant) {
        String hql = "select cc.id, coalesce(c.reference, c.numero), c.statut, ent.raisonSociale, cc.dateProposition "
                + "from ClotureCredit cc join cc.certificatCredit c left join c.entreprise ent "
                + "where cc.approuvee is null";
        return lignes(hql, scope, Map.of(), maintenant, "CLOTURE", "ClotureCredit",
                "Valider ou rejeter la proposition de clôture");
    }

    /** Proposition approuvée par le Président, que la DGTCP doit encore finaliser. */
    private List<LigneMainCouranteDto> cloturesAFinaliser(ReportingScope scope, Instant maintenant) {
        String hql = "select cc.id, coalesce(c.reference, c.numero), c.statut, ent.raisonSociale, cc.dateProposition "
                + "from ClotureCredit cc join cc.certificatCredit c left join c.entreprise ent "
                + "where cc.approuvee = true and c.statut not in (:termines)";
        return lignes(hql, scope, Map.of("termines", Set.of(StatutCertificat.CLOTURE, StatutCertificat.ANNULE)), maintenant,
                "CLOTURE", "ClotureCredit", "Finaliser la clôture du crédit");
    }

    // ── les sous-traitances ────────────────────────────────────────────────────────────────────

    /**
     * Les demandes de sous-traitance en attente de décision. La sous-traitance ne porte pas de date
     * de demande : la ligne n'a donc pas de durée d'attente (0), plutôt qu'une date inventée.
     */
    private List<LigneMainCouranteDto> sousTraitancesADecider(ReportingScope scope) {
        String hql = "select st.id, coalesce(c.reference, c.numero), st.statut, ent.raisonSociale "
                + "from SousTraitance st join st.certificatCredit c left join c.entreprise ent "
                + "where st.statut = :demande";
        List<LigneMainCouranteDto> out = new ArrayList<>();
        for (Object[] row : runner.lignes(hql, scope, Map.of("demande", StatutSousTraitance.DEMANDE))) {
            out.add(LigneMainCouranteDto.builder()
                    .processus("SOUS_TRAITANCE").type("SousTraitance")
                    .id(nombre(row[0])).reference(texte(row[1])).statut(texte(row[2]))
                    .entrepriseRaisonSociale(texte(row[3]))
                    .action("Autoriser ou refuser la sous-traitance")
                    .joursDAttente(0L)
                    .build());
        }
        return out;
    }

    // ── les conventions ────────────────────────────────────────────────────────────────────────

    /** Les conventions actives encore en attente de validation, datées de leur dépôt. */
    private List<LigneMainCouranteDto> conventionsAValider(ReportingScope scope, Instant maintenant) {
        String hql = "select c.id, c.reference, c.statut, ac.nom, c.dateCreation "
                + "from Convention c left join c.autoriteContractante ac "
                + "where c.statut = :attente and (c.actif is null or c.actif = true)";
        return lignes(hql, scope, Map.of("attente", StatutConvention.EN_ATTENTE), maintenant,
                "CONVENTION", "Convention", "Valider ou rejeter la convention");
    }

    // ── les clôtures ───────────────────────────────────────────────────────────────────────────

    /**
     * Les propositions de clôture en attente, reprises de la file existante.
     *
     * <p>Seul processus à disposer déjà d'une vraie file côté serveur : on l'appelle plutôt que de
     * reformuler sa règle, pour que les deux écrans ne puissent pas se contredire.
     *
     * <p><b>Aucun rattrapage ici, et c'est délibéré.</b> Cette méthode refuse tout rôle autre que
     * la DGTCP en levant. Entourer l'appel d'un {@code try/catch} semblait prudent et était le
     * défaut : l'exception d'une méthode transactionnelle marque la transaction courante
     * « rollback-only » avant même d'être attrapée, et l'avaler ne l'en démarque pas. Le commit
     * échouait alors en {@code UnexpectedRollbackException}, rendue en 400 à l'écran — pour tous
     * les rôles sauf la DGTCP. La seule façon correcte de s'abstenir est de ne pas appeler.
     */
    private List<LigneMainCouranteDto> cloturesAApprouver(AuthenticatedUser auth, Instant maintenant) {
        List<LigneMainCouranteDto> out = new ArrayList<>();
        List<CertificatClotureQueueItemDto> file = clotureCreditService.findClotureQueue(auth);
        for (CertificatClotureQueueItemDto item : file) {
            // La file mêle deux situations, et le geste attendu n'est pas le même : une proposition
            // déjà déposée s'examine, un crédit seulement éligible se propose. Les confondre sous
            // un libellé unique ferait chercher à l'agent un bouton qui n'est pas là.
            String action = item.isPropositionEnCours()
                    ? "Examiner la proposition de clôture"
                    : "Proposer la clôture du crédit";
            // Tout crédit sans opération en cours peut être proposé, mais seul celui que plus rien
            // ne retient est une tâche : sinon chaque crédit ouvert encombrerait la main courante.
            if (!item.isPropositionEnCours() && !item.isDetecteAutomatiquement()) {
                continue;
            }
            out.add(LigneMainCouranteDto.builder()
                    .processus("CLOTURE")
                    .type("CertificatCredit")
                    .id(item.getCertificatCreditId())
                    .reference(item.getNumero())
                    .statut(item.getStatut() != null ? item.getStatut().name() : null)
                    .entrepriseRaisonSociale(item.getEntrepriseRaisonSociale())
                    .action(action)
                    // La file ne porte pas la date de proposition : on date depuis l'émission du
                    // certificat, seule date de départ disponible, et la définition le dit.
                    .dateReference(item.getDateEmission())
                    .joursDAttente(jours(item.getDateEmission(), maintenant))
                    .build());
        }
        return out;
    }

    // ── les rejets temporaires, vus du déposant ────────────────────────────────────────────────

    /**
     * Ce que la Commission attend de moi : les rejets temporaires encore ouverts sur mes dossiers.
     *
     * <p>C'est la main courante d'une entreprise. L'état est explicite en base et sa valeur par
     * défaut est {@code RESOLU}, donc « ouvert » n'est jamais déduit d'une donnée absente.
     */
    private List<LigneMainCouranteDto> mesRejetsAResoudre(ReportingScope scope, Instant maintenant) {
        List<LigneMainCouranteDto> out = new ArrayList<>();

        String hqlDemande = "select o.id, coalesce(o.reference, o.numero), o.statut, ent.raisonSociale, "
                + "dec.dateDecision, dec.motifRejet, dec.role "
                + "from DecisionCorrection dec join dec.demandeCorrection o "
                + "  left join o.entreprise ent "
                + "where dec.rejetTempStatus = :ouvert and " + predicates.demande("o", scope, false);
        for (Object[] row : runner.lignes(hqlDemande, scope, Map.of("ouvert", RejetTempStatus.OUVERT))) {
            out.add(rejet(row, maintenant, "DemandeCorrection"));
        }

        String hqlCertificat = "select o.id, coalesce(o.reference, o.numero), o.statut, ent.raisonSociale, "
                + "dec.dateDecision, dec.motifRejet, dec.role "
                + "from DecisionCertificatCredit dec join dec.certificatCredit o "
                + "  left join o.entreprise ent "
                + "where dec.rejetTempStatus = :ouvert and " + predicates.certificat("o", scope, false);
        for (Object[] row : runner.lignes(hqlCertificat, scope, Map.of("ouvert", RejetTempStatus.OUVERT))) {
            out.add(rejet(row, maintenant, "CertificatCredit"));
        }
        return out;
    }

    private LigneMainCouranteDto rejet(Object[] row, Instant maintenant, String type) {
        Instant dateDecision = (Instant) row[4];
        String motif = texte(row[5]);
        return LigneMainCouranteDto.builder()
                .processus("REJET_TEMPORAIRE")
                .type(type)
                .id(nombre(row[0]))
                .reference(texte(row[1]))
                .statut(texte(row[2]))
                .entrepriseRaisonSociale(texte(row[3]))
                .action("Fournir la pièce demandée par la " + texte(row[6])
                        + (motif != null ? " : " + motif : ""))
                .dateReference(dateDecision)
                .joursDAttente(jours(dateDecision, maintenant))
                .build();
    }

    // ── les utilisations de crédit ─────────────────────────────────────────────────────────────

    /**
     * Les utilisations dont le statut attend précisément ce rôle.
     *
     * <p>La table des étapes n'est pas écrite à la main : elle compose le graphe des transitions et
     * l'arbitre des rôles du service. Voir {@link EtapesUtilisation}.
     */
    private List<LigneMainCouranteDto> utilisationsAttendantLeRole(ReportingScope scope, Role role,
                                                                   Instant maintenant) {
        List<LigneMainCouranteDto> out = new ArrayList<>();
        for (TypeUtilisation type : TypeUtilisation.values()) {
            Set<StatutUtilisation> attendus = EtapesUtilisation.statutsAttendantLeRole(type, role);
            if (attendus.isEmpty()) {
                continue;
            }
            String hql = "select u.id, coalesce(u.reference, concat('#', cast(u.id as string))), "
                    + "u.statut, ent.raisonSociale, u.dateDemande "
                    + "from UtilisationCredit u left join u.entreprise ent "
                    + "where " + predicates.utilisation("u", scope, false)
                    + "   and u.type = :type and u.statut in (:attendus)";
            for (Object[] row : runner.lignes(hql, scope,
                    Map.of("type", type, "attendus", attendus))) {
                StatutUtilisation statut = StatutUtilisation.valueOf(texte(row[2]));
                Instant reference = (Instant) row[4];
                out.add(LigneMainCouranteDto.builder()
                        .processus("UTILISATION")
                        .type("UtilisationCredit")
                        .id(nombre(row[0]))
                        .reference(texte(row[1]))
                        .statut(statut.name())
                        .entrepriseRaisonSociale(texte(row[3]))
                        .action(EtapesUtilisation.action(type, statut))
                        .dateReference(reference)
                        .joursDAttente(jours(reference, maintenant))
                        .build());
            }
        }
        return out;
    }

    // ── ce que le déposant n'a pas encore envoyé ───────────────────────────────────────────────

    /**
     * Les brouillons qui attendent d'être soumis.
     *
     * <p>Un dossier laissé en brouillon n'attend personne d'autre que son auteur, et rien ne le lui
     * rappelle aujourd'hui : il ne figure dans aucune file, puisqu'aucune direction ne l'a reçu.
     * C'est précisément le genre de dossier qui dort six mois.
     */
    private List<LigneMainCouranteDto> mesBrouillonsASoumettre(ReportingScope scope, Instant maintenant) {
        String hql = "select d.id, coalesce(d.reference, d.numero), d.statut, ent.raisonSociale, "
                + "coalesce(d.dateDepot, d.dateCreation) "
                + "from DemandeCorrection d left join d.entreprise ent "
                + "where " + predicates.demande("d", scope, false)
                + "   and d.statut = :brouillon";
        return lignes(hql, scope, Map.of("brouillon", StatutDemande.BROUILLON), maintenant,
                "BROUILLON", "DemandeCorrection", "Soumettre la demande de correction");
    }

    // ── les files d'administration ─────────────────────────────────────────────────────────────

    /**
     * Ce qui attend une décision d'administration : rattachements et réinitialisations.
     *
     * <p>Ces deux files n'apparaissaient sur aucun écran d'accueil. Un compte en attente de
     * rattachement est quelqu'un qui ne peut pas travailler ; une réinitialisation en attente est
     * quelqu'un qui ne peut pas se connecter. Les deux méritent mieux qu'un écran auquel il faut
     * penser.
     *
     * <p>Aucun périmètre métier ne s'y applique : ces demandes ne sont rattachées à aucun dossier,
     * et les rôles qui les voient ici sont nationaux.
     */
    private List<LigneMainCouranteDto> demandesAdministratives(ReportingScope scope, Instant maintenant) {
        List<LigneMainCouranteDto> out = new ArrayList<>();
        if (scope.perimetreVide()) {
            return out;
        }

        for (Object[] row : runner.lignes(
                "select r.id, r.reference, r.statut, r.nomComplet, r.dateCreation "
                        + "from DemandeRattachement r where r.statut = :attente",
                scope, Map.of("attente", StatutDemandeRattachement.EN_ATTENTE))) {
            Instant reference = (Instant) row[4];
            out.add(LigneMainCouranteDto.builder()
                    .processus("ADMINISTRATION")
                    .type("DemandeRattachement")
                    .id(nombre(row[0]))
                    .reference(texte(row[1]))
                    .statut(texte(row[2]))
                    .entrepriseRaisonSociale(texte(row[3]))
                    .action("Approuver ou refuser la création de compte")
                    .dateReference(reference)
                    .joursDAttente(jours(reference, maintenant))
                    .build());
        }

        for (Object[] row : runner.lignes(
                "select p.id, p.email, p.statut, u.nomComplet, p.dateCreation "
                        + "from DemandeResetPassword p left join p.utilisateur u "
                        + "where p.statut = :attente",
                scope, Map.of("attente", StatutDemandeResetPassword.EN_ATTENTE))) {
            Instant reference = (Instant) row[4];
            out.add(LigneMainCouranteDto.builder()
                    .processus("ADMINISTRATION")
                    .type("DemandeResetPassword")
                    .id(nombre(row[0]))
                    .reference(texte(row[1]))
                    .statut(texte(row[2]))
                    .entrepriseRaisonSociale(texte(row[3]))
                    .action("Traiter la réinitialisation de mot de passe")
                    .dateReference(reference)
                    .joursDAttente(jours(reference, maintenant))
                    .build());
        }
        return out;
    }

    // ── utilitaires ────────────────────────────────────────────────────────────────────────────

    private List<LigneMainCouranteDto> lignes(String hql, ReportingScope scope,
                                              Map<String, Object> params, Instant maintenant,
                                              String processus, String type, String action) {
        List<LigneMainCouranteDto> out = new ArrayList<>();
        for (Object[] row : runner.lignes(hql, scope, params)) {
            Instant reference = (Instant) row[4];
            out.add(LigneMainCouranteDto.builder()
                    .processus(processus)
                    .type(type)
                    .id(nombre(row[0]))
                    .reference(texte(row[1]))
                    .statut(texte(row[2]))
                    .entrepriseRaisonSociale(texte(row[3]))
                    .action(action)
                    .dateReference(reference)
                    .joursDAttente(jours(reference, maintenant))
                    .build());
        }
        return out;
    }

    private static long jours(Instant depuis, Instant maintenant) {
        return depuis != null ? Math.max(0, Duration.between(depuis, maintenant).toDays()) : 0L;
    }

    private static Long nombre(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    private static String texte(Object v) {
        return v != null ? v.toString() : null;
    }
}
