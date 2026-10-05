package mr.gov.finances.sgci.workflow;

import org.springframework.stereotype.Component;

import mr.gov.finances.sgci.domain.enums.StatutUtilisation;

import static mr.gov.finances.sgci.domain.enums.StatutUtilisation.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Workflow Utilisation de Crédit (Processus 6-7).
 * Rejet définitif (REJETEE) possible depuis tout statut actif sauf LIQUIDEE et APUREE.
 * Rejet temporaire (INCOMPLETE) est posé via les décisions REJET_TEMP, pas via ce graphe seul.
 */
@Component
public class UtilisationCreditWorkflow {

    private static final Map<StatutUtilisation, Set<StatutUtilisation>> TRANSITIONS = Map.ofEntries(
            Map.entry(BROUILLON, EnumSet.of(DEMANDEE, CLOTUREE)),
            // Douane : DGD annote directement depuis DEMANDEE → EN_CONTROLE_DGD
            // Rétrocompatibilité : VISE et EN_VERIFICATION restent accessibles depuis DEMANDEE
            Map.entry(DEMANDEE, EnumSet.of(INCOMPLETE, EN_VERIFICATION, VISE, EN_CONTROLE_DGD, REJETEE, CLOTUREE)),
            Map.entry(INCOMPLETE, EnumSet.of(A_RECONTROLER, REJETEE, CLOTUREE)),
            // VISE en direct, par symétrie avec DEMANDEE → VISE : un dossier re-soumis après
            // résolution d'un rejet mérite le même traitement qu'un dossier soumis.
            Map.entry(A_RECONTROLER, EnumSet.of(EN_VERIFICATION, VISE, REJETEE, CLOTUREE)),
            Map.entry(EN_VERIFICATION, EnumSet.of(INCOMPLETE, VISE, EN_CONTROLE_DGD, VALIDEE, REJETEE, CLOTUREE)),
            // Douane : le visa DGD conclut le contrôle. L'auto-transition autorise une ré-annotation
            // du bulletin tant que l'entreprise n'a pas saisi le chèque.
            Map.entry(VISE, EnumSet.of(VISE, INCOMPLETE, CHEQUE_SAISI, VALIDEE, LIQUIDEE, REJETEE, CLOTUREE)),
            // Nouveau workflow douane — EN_CONTROLE_DGD peut se ré-annoter (auto-transition DGD)
            // CHEQUE_SAISI reste atteignable depuis EN_CONTROLE_DGD pour les dossiers antérieurs à
            // l'introduction de VISE ; le service exige alors qu'un visa DGD existe.
            Map.entry(EN_CONTROLE_DGD, EnumSet.of(EN_CONTROLE_DGD, VISE, INCOMPLETE, CHEQUE_SAISI, REJETEE, CLOTUREE)),
            // Le chèque couvre la part A_PAYER ; la DGTCP contrôle alors le dossier et le transmet
            // au Président. Retirer CERTIFICAT_EMIS d'ici est ce qui rend l'étape incontournable.
            Map.entry(CHEQUE_SAISI, EnumSet.of(INCOMPLETE, TRANSMISE_AU_PRESIDENT, REJETEE, CLOTUREE)),
            Map.entry(TRANSMISE_AU_PRESIDENT, EnumSet.of(CERTIFICAT_EMIS, INCOMPLETE, REJETEE, CLOTUREE)),
            Map.entry(ENVOYEE_AU_TRESOR, EnumSet.of(QUITTANCES_ENREGISTREES, REJETEE, CLOTUREE)),
            Map.entry(QUITTANCES_ENREGISTREES, EnumSet.of(LIQUIDEE, REJETEE, CLOTUREE)),
            // TVA intérieure : le certificat s'émet après la validation, et c'est lui que l'entreprise
            // présente à la DGI pour obtenir la quittance. APUREE et LIQUIDEE restent atteignables
            // depuis VALIDEE pour les parcours antérieurs ; les services exigent, eux, la quittance.
            Map.entry(VALIDEE, EnumSet.of(CERTIFICAT_EMIS, LIQUIDEE, APUREE, REJETEE, CLOTUREE)),
            // Auto-transition autorisée : un dépôt de quittance peut en remplacer un précédent.
            Map.entry(QUITTANCE_DGI_ENREGISTREE,
                    EnumSet.of(QUITTANCE_DGI_ENREGISTREE, APUREE, REJETEE, CLOTUREE)),
            // Le certificat émis est la pièce qui ouvre l'étape de paiement : au Trésor en douane,
            // à la DGI en TVA intérieure. Une seule valeur de statut pour les deux branches, que le
            // service oriente ensuite selon le type.
            Map.entry(CERTIFICAT_EMIS,
                    EnumSet.of(ENVOYEE_AU_TRESOR, QUITTANCE_DGI_ENREGISTREE, INCOMPLETE, REJETEE, CLOTUREE)),
            // Le calcul financier de la DGTCP clôt l'instruction ; l'entreprise accuse réception.
            // CLOTUREE demeure atteignable en direct : les utilisations reprises d'archive naissent
            // en APUREE sans numéro CU- et n'ont jamais traversé ce circuit. Le verrou métier vit
            // dans le service (assertCertificatEmisAvantCloture), où il peut exempter ces dossiers ;
            // ce graphe, lui, reste purement structurel.
            Map.entry(LIQUIDEE, EnumSet.of(CLOTUREE)),
            Map.entry(APUREE, EnumSet.of(CLOTUREE)),
            Map.entry(REJETEE, EnumSet.noneOf(StatutUtilisation.class)),
            Map.entry(CLOTUREE, EnumSet.noneOf(StatutUtilisation.class))
    );

    public void validateTransition(StatutUtilisation from, StatutUtilisation to) {
        Set<StatutUtilisation> allowed = TRANSITIONS.get(from);
        // Auto-transition (from == to) : autorisée uniquement si explicitement dans les transitions
        if (from == to && (allowed == null || !allowed.contains(to))) {
            throw new WorkflowTransitionException("Le statut est déjà: " + from);
        }
        if (allowed == null || !allowed.contains(to)) {
            throw new WorkflowTransitionException(from.name(), to.name(), "UtilisationCredit");
        }
    }
}
