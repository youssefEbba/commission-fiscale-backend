package mr.gov.finances.sgci.config.seed;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.config.PermissionsRevoqueesMigration;
import mr.gov.finances.sgci.domain.entity.Permission;
import mr.gov.finances.sgci.domain.entity.RolePermission;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.repository.PermissionRepository;
import mr.gov.finances.sgci.repository.RolePermissionRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeedRbac {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public void seed() {
        seedPermissions();
        seedRolePermissions();
    }

    private void seedPermissions() {
        createPermission("projet.create", "Créer un dossier projet");
        createPermission("projet.document.upload", "Déposer les documents projet");
        createPermission("projet.view", "Consulter ses projets");
        createPermission("projet.update", "Modifier les informations projet");
        createPermission("projet.validate", "Valider le référentiel projet");
        createPermission("projet.reject", "Rejeter le référentiel projet");
        createPermission("projet.view.all", "Consulter tous les référentiels");

        createPermission("convention.create", "Créer une convention");
        createPermission("convention.update", "Modifier une convention avant sa validation par la DGB");
        createPermission("convention.annuler", "Annuler sa convention avant sa validation par la DGB");
        createPermission("convention.view", "Consulter ses conventions");
        createPermission("convention.view.all", "Consulter toutes les conventions");
        createPermission("convention.validate", "Valider une convention");
        createPermission("convention.reject", "Rejeter une convention");
        createPermission("convention.activate", "Activer ou désactiver une convention");
        createPermission("convention.document.upload", "Déposer les documents convention");

        createPermission("correction.submit", "Soumettre une demande de correction fiscale");
        createPermission("correction.offer.upload", "Uploader l'offre fiscale");
        createPermission("correction.offer.view", "Visualiser l'offre fiscale");
        createPermission("correction.complement.add", "Déposer des pièces complémentaires");
        createPermission("correction.visa.history.view", "Consulter l'historique des visas");
        createPermission("correction.status.update", "Changer le statut d'une demande de correction");
        createPermission("correction.entreprise.queue.view", "Consulter ses demandes de correction");
        createPermission("correction.dgd.queue.view", "Consulter la file des dossiers à traiter");
        createPermission("correction.dgd.evaluate.nomenclature", "Évaluer la nomenclature douanière");
        createPermission("correction.dgd.evaluate.valeur", "Évaluer la valeur en douane");
        createPermission("correction.dgd.calculate", "Calculer la composante Douane/TVA");
        createPermission("correction.dgd.save", "Enregistrer l'évaluation");
        createPermission("correction.dgd.transmit", "Transmettre au visa Trésor");
        createPermission("correction.dgtcp.queue.view", "Consulter les dossiers visa Trésor");
        createPermission("correction.dgtcp.review", "Vérifier les calculs");
        createPermission("correction.dgtcp.finalize", "Arrêter le montant définitif");
        createPermission("correction.dgtcp.visa", "Apposer le visa Trésor");
        createPermission("correction.dgtcp.request_complements", "Demander des compléments");
        createPermission("correction.dgtcp.reject", "Rejeter la demande correction");
        createPermission("correction.dgi.queue.view", "Consulter les dossiers visa Impôts");
        createPermission("correction.dgi.visa", "Apposer le visa Impôts");
        createPermission("correction.dgi.reject", "Rejeter le visa Impôts");
        createPermission("correction.dgi.document.upload", "Téléverser le document crédit intérieur (visa DGI)");
        createPermission("correction.dgb.queue.view", "Consulter les dossiers visa Budget");
        createPermission("correction.dgb.visa", "Apposer le visa Budget");
        createPermission("correction.dgb.reject", "Rejeter le visa Budget");
        createPermission("correction.president.queue.view", "Consulter les dossiers à valider");
        createPermission("correction.president.history.view", "Visualiser l'historique complet");
        createPermission("correction.president.arbitrate", "Arbitrer un dossier");
        createPermission("correction.president.validate", "Valider la correction fiscale");
        createPermission("correction.president.letter.generate", "Éditer la lettre de correction");
        createPermission("correction.president.signature.upload", "Déposer le scan de signature");
        createPermission("correction.president.reject", "Rejeter la correction fiscale");
        createPermission("correction.view.audit", "Consulter correction (audit)");
        createPermission("correction.reclamation.submit", "Déposer une réclamation sur une correction adoptée ou notifiée");
        createPermission("correction.reclamation.annuler", "Annuler une réclamation en cours avant DGTCP (demande inchangée)");
        createPermission("correction.reclamation.traiter", "Accepter ou rejeter une réclamation sur une demande de correction");
        createPermission("correction.demande.reactivate", "Réactiver une demande de correction annulée (retour RECUE, AC)");
        createPermission("correction.demande.reactivate_rejetee", "Annuler un rejet définitif d'une demande de correction (retour EN_VALIDATION, ADMIN_SI)");
        createPermission("correction.admin_override", "Corriger un document ou une information d'une demande de correction, à tout moment (ADMIN_SI, motif obligatoire)");
        createPermission("correction.visa.admin_override", "Viser une demande de correction à la place d'un membre de la commission (DGD/DGTCP/DGI/DGB) ou adopter à la place du Président (ADMIN_SI, motif obligatoire, document exigé téléversé)");
        createPermission("signature.manage", "Gérer les images de signature PNG (créer/remplacer/désactiver) — sa propre signature, ou toute signature pour ADMIN_SI");
        createPermission("parametre.manage",
                "Gérer le paramétrage de l'application (coordonnées de contact du pied de page)");
        createPermission("certificat.admin_override", "Corriger un document ou une information d'un certificat de crédit, à tout moment (ADMIN_SI, motif obligatoire)");
        createPermission("certificat.visa.admin_override", "Viser un certificat de crédit à la place d'un membre de la commission (DGI/DGD/DGTCP) ou valider à la place du Président (ADMIN_SI, motif obligatoire, certificat signé téléversé)");
        // Séparée de la précédente : l'ouverture du crédit initialise les soldes, donc produit un
        // effet financier qu'on doit pouvoir accorder indépendamment du simple déblocage des visas.
        createPermission("certificat.ouverture.admin_override", "Ouvrir le crédit d'un certificat à la place du Président ou de la DGTCP (ADMIN_SI, motif obligatoire)");
        createPermission("utilisation.admin_override", "Corriger un document ou une information d'une demande d'utilisation, à tout moment (ADMIN_SI, motif obligatoire)");

        createPermission("mise_en_place.submit", "Soumettre une demande de mise en place");
        createPermission("mise_en_place.document.upload", "Déposer les pièces justificatives");
        createPermission("mise_en_place.view", "Consulter l'état d'avancement");
        createPermission("mise_en_place.annuler", "Annuler une demande de mise en place");
        createPermission("mise_en_place.entreprise.queue.view", "Consulter ses demandes de mise en place");
        createPermission("mise_en_place.dgd.queue.view", "Consulter les dossiers contrôle fiscal");
        createPermission("mise_en_place.dgd.validate", "Valider l'éligibilité");
        createPermission("mise_en_place.dgd.reject", "Rejeter la demande de mise en place");
        createPermission("mise_en_place.dgd.resolve", "Résoudre un rejet temporaire DGD");

        createPermission("mise_en_place.dgi.queue.view", "Consulter les dossiers contrôle fiscal");
        createPermission("mise_en_place.dgi.validate", "Valider l'éligibilité");
        createPermission("mise_en_place.dgi.reject", "Rejeter la demande de mise en place");
        createPermission("mise_en_place.dgi.resolve", "Résoudre un rejet temporaire DGI");

        createPermission("mise_en_place.dgtcp.queue.view", "Consulter les demandes de mise en place");
        createPermission("mise_en_place.dgtcp.validate", "Apposer le visa DGTCP");
        createPermission("mise_en_place.dgtcp.reject", "Rejeter temporairement (DGTCP)");
        createPermission("mise_en_place.dgtcp.open_credit", "Ouvrir le crédit d'impôt");
        createPermission("mise_en_place.dgtcp.allocate", "Ventiler le crédit");
        createPermission("mise_en_place.dgtcp.certificate.generate", "Générer le certificat");
        createPermission("mise_en_place.dgtcp.certificate.send", "Transmettre le certificat pour signature");
        createPermission("mise_en_place.dgtcp.resolve", "Résoudre un rejet temporaire DGTCP");

        createPermission("mise_en_place.dgb.queue.view", "Consulter les dossiers Budget");
        createPermission("mise_en_place.dgb.resolve", "Résoudre un rejet temporaire DGB");

        createPermission("mise_en_place.president.queue.view", "Consulter les certificats en attente");
        createPermission("mise_en_place.president.validate", "Valider le certificat");
        createPermission("mise_en_place.president.document.generate", "Déclencher l'édition du document officiel");
        createPermission("mise_en_place.president.signature.upload", "Déposer le scan signé");
        createPermission("mise_en_place.president.reject", "Rejeter le certificat");
        createPermission("mise_en_place.president.resolve", "Résoudre un rejet temporaire Président");

        createPermission("certificat.verification.scan",
                "Vérifier un certificat de crédit par scan du code-barres (numéro)");

        createPermission("archive.import",
                "Reprendre un relevé de crédit d'impôt de l'ancienne application");

        createPermission("utilisation.douane.submit", "Soumettre une demande d'utilisation Douane");
        createPermission("utilisation.douane.document.upload", "Déposer les pièces import");
        createPermission("utilisation.douane.solde.view", "Consulter le solde Douane");
        createPermission("utilisation.douane.history.view", "Consulter l'historique des imputations");
        createPermission("utilisation.douane.dgd.queue.view", "Consulter les demandes d'utilisation Douane");
        createPermission("utilisation.douane.dgd.verify", "Vérifier la comptabilité matière");
        createPermission("utilisation.douane.dgd.quittance.visa", "Viser le bulletin de liquidation");
        createPermission("utilisation.douane.dgd.reject", "Rejeter la demande Douane");
        createPermission("utilisation.douane.dgtcp.queue.view", "Consulter les demandes validées DGD");
        createPermission("utilisation.douane.dgtcp.impute", "Imputer les droits et taxes");
        createPermission("utilisation.douane.dgtcp.solde.update", "Mettre à jour le solde Douane");
        createPermission("utilisation.douane.entreprise.cheque", "Saisir le chèque certifié (après visa DGD)");
        createPermission("utilisation.douane.dgtcp.envoyer.tresor", "Envoyer la demande au Trésor");
        createPermission("utilisation.douane.dgtcp.transmettre.president",
                "Contrôler le dossier (bulletin visé, chèque certifié) et le transmettre au Président");
        createPermission("utilisation.douane.dgtcp.quittances", "Saisir les quittances Trésor");
        createPermission("utilisation.dgtcp.cloturer",
                "Clôturer une utilisation une fois le crédit débité (liquidation ou apurement)");
        createPermission("utilisation.douane.dgtcp.history.view", "Consulter l'historique des liquidations");

        createPermission("utilisation.interieur.submit", "Soumettre une demande d'utilisation Intérieur");
        createPermission("utilisation.interieur.document.upload", "Déposer les justificatifs TVA");
        createPermission("utilisation.interieur.solde.view", "Consulter le solde Intérieur");
        createPermission("utilisation.interieur.history.view", "Consulter l'historique des apurements");
        createPermission("utilisation.interieur.dgtcp.queue.view", "Consulter les demandes d'utilisation Intérieur");
        createPermission("utilisation.interieur.dgtcp.verify", "Vérifier les justificatifs");
        createPermission("utilisation.interieur.dgtcp.validate", "Valider l'apurement");
        createPermission("utilisation.interieur.dgtcp.solde.update", "Mettre à jour le solde Intérieur");
        createPermission("utilisation.interieur.dgtcp.reject", "Rejeter la demande Intérieur");
        createPermission("utilisation.interieur.dgi.view", "Consulter les utilisations Intérieur");
        createPermission("utilisation.interieur.dgi.quittance", "Déposer la quittance DGI attestant le paiement de la TVA intérieure (étape préalable à l'apurement)");
        createPermission("utilisation.ac.view",
                "Consulter les utilisations de crédit liées aux certificats de son périmètre (AC / délégué)");
        createPermission("utilisation.interieur.dgi.decision", "Enregistrer visa ou rejet temporaire (DGI, TVA intérieure)");

        createPermission("utilisation.douane.dgd.resolve", "Résoudre un rejet temporaire (DGD, utilisation douane)");
        createPermission("utilisation.douane.dgtcp.resolve", "Résoudre un rejet temporaire (DGTCP, utilisation douane)");
        createPermission("utilisation.interieur.dgtcp.resolve", "Résoudre un rejet temporaire (DGTCP, TVA intérieure)");
        createPermission("utilisation.interieur.dgi.resolve", "Résoudre un rejet temporaire (DGI, TVA intérieure)");

        createPermission("utilisation.entreprise.rejet.repondre",
                "Répondre à un rejet temporaire sur une utilisation (message ou complément lié au dépôt de pièces)");

        createPermission("utilisation.president.certificat.emettre",
                "Émettre le certificat d'utilisation (numérotation CU-, acte réservé au Président)");
        createPermission("utilisation.certificat.admin_override",
                "Émettre le certificat d'utilisation à la place du Président (motif obligatoire)");

        createPermission("modification.submit", "Soumettre une demande de modification");
        createPermission("modification.document.upload", "Déposer les documents justificatifs");
        createPermission("modification.view", "Consulter le statut de modification");
        createPermission("modification.dgtcp.queue.view", "Consulter les demandes de modification");
        createPermission("modification.dgtcp.analyze", "Analyser l'impact sur les composantes");
        createPermission("modification.dgtcp.propose", "Proposer un ajustement des crédits");
        createPermission("modification.president.queue.view", "Consulter les propositions de modification");
        createPermission("modification.president.validate", "Valider la modification");
        createPermission("modification.president.reject", "Rejeter la modification");
        createPermission("modification.president.document.generate", "Déclencher l'édition du document");

        createPermission("transfert.submit", "Soumettre une demande de transfert de solde");
        createPermission("transfert.amount.set", "Indiquer le montant à transférer");
        createPermission("transfert.solde.view", "Consulter les soldes disponibles");
        createPermission("transfert.dgtcp.queue.view", "Consulter les demandes de transfert");
        createPermission("transfert.dgtcp.verify", "Vérifier la disponibilité du solde");
        createPermission("transfert.dgd.visa", "Viser un transfert de crédit : la DGD atteste que le solde douanier ne sera pas nécessaire");
        createPermission("transfert.dgi.visa", "Viser un transfert de crédit : la DGI confirme les besoins en fiscalité intérieure");
        createPermission("transfert.dgtcp.prepare", "Préparer l'opération de transfert");
        createPermission("transfert.dgtcp.update", "Mettre à jour les composantes");
        createPermission("transfert.president.validate", "Valider le transfert de solde");
        createPermission("transfert.president.reject", "Rejeter le transfert");
        createPermission("transfert.annuler", "Annuler une demande de transfert avant exécution par DGTCP / Président");
        createPermission("transfert.entreprise.rejet.repondre", "Répondre à un rejet temporaire sur une demande de transfert");

        createPermission("sous_traitance.submit", "Soumettre une demande de sous-traitance");
        createPermission("sous_traitance.solde.view", "Consulter ses demandes / autorisations de sous-traitance");
        createPermission("sous_traitance.dgtcp.queue.view", "Consulter les demandes de sous-traitance (DGTCP)");
        createPermission("sous_traitance.dgtcp.update", "Autoriser/refuser une sous-traitance (DGTCP)");

        createPermission("sous_traitant.list", "Lister les comptes sous-traitants");

        createPermission("cloture.queue.view", "Consulter les dossiers éligibles à clôture");
        createPermission("cloture.prepare", "Préparer la décision de clôture/annulation");
        createPermission("cloture.report.view", "Consulter les rapports et états statistiques");
        createPermission("cloture.report.generate", "Générer les rapports de suivi");
        createPermission("cloture.president.queue.view", "Consulter les propositions de clôture");
        createPermission("cloture.president.validate", "Valider la clôture/annulation");
        createPermission("cloture.president.reject", "Rejeter la clôture/annulation");

        createPermission("demande.explication.view", "Consulter les demandes d'explication (discussion commission)");
        createPermission("demande.explication.create", "Ouvrir une demande d'explication");
        createPermission("demande.explication.reply", "Répondre à une demande d'explication");
        createPermission("demande.explication.close", "Fermer une demande d'explication");
        createPermission("archivage.view", "Accéder aux archives complètes");

        createPermission("referentiel.taxe.manage", "Gérer le référentiel des taxes douanières (CRUD)");

        createPermission("user.create", "Créer un compte utilisateur");
        createPermission("user.update", "Modifier un compte utilisateur");
        createPermission("user.disable", "Désactiver ou réactiver un compte");
        createPermission("user.reset", "Réinitialiser un accès");
        createPermission("user.list", "Consulter la liste des utilisateurs");
        createPermission("user.role.assign", "Attribuer un rôle");
        createPermission("role.create", "Créer un rôle");
        createPermission("role.permissions.update", "Modifier les permissions d'un rôle");
        createPermission("role.list", "Consulter la liste des rôles");
        createPermission("role.disable", "Désactiver un rôle");
        createPermission("security.audit.view", "Consulter le journal d'activité");
        createPermission("security.logins.view", "Consulter le journal des connexions");
        createPermission("permissions.manage", "Gérer les permissions d'un rôle");
        createPermission("permissions.view", "Consulter les permissions");
        createPermission("document.requirements.view", "Consulter la configuration des documents requis");
        createPermission("document.types.view", "Consulter le référentiel des types de documents GED");
        createPermission("document.types.manage", "Gérer le référentiel et les exigences documentaires par processus");
        createPermission("entreprise.list", "Consulter la liste des entreprises");
        createPermission("entreprise.view.own", "Consulter sa propre entreprise");
        createPermission("entreprise.create", "Créer une entreprise");
        createPermission("entreprise.update", "Modifier une entreprise");
        createPermission("entreprise.delete", "Supprimer une entreprise");
        createPermission("autorite.create", "Créer une autorité contractante");
        createPermission("autorite.update", "Modifier une autorité contractante");
        createPermission("autorite.delete", "Supprimer une autorité contractante");
        createPermission("rattachement.validate", "Approuver ou refuser une demande de rattachement");
        createPermission("groupement.list", "Consulter la liste des groupements");
        createPermission("groupement.create", "Créer un groupement");
        createPermission("groupement.update", "Modifier un groupement");
        createPermission("groupement.delete", "Supprimer un groupement");
        createPermission("marche.manage", "Gérer les marchés");
        createPermission("marche.view", "Consulter les marchés (lecture)");

        createPermission("bailleur.list", "Consulter la liste des bailleurs");
        createPermission("bailleur.create", "Créer un bailleur");
        createPermission("bailleur.update", "Modifier un bailleur (Président et administrateur)");
        createPermission("modification.admin_override",
                "Se substituer à la DGTCP ou au Président sur un avenant (motif obligatoire)");
        createPermission("devise.list", "Consulter la liste des devises");
        createPermission("devise.create", "Créer une devise");
        createPermission("taux_change.view", "Consulter le taux de change");

        createPermission("delegue.list", "Consulter la liste des délégués");
        createPermission("delegue.create", "Créer un délégué");
        createPermission("delegue.update", "Modifier un délégué (identité, e-mail, mot de passe)");
        createPermission("delegue.disable", "Activer/désactiver un délégué");

        createPermission("reporting.view", "Consulter les tableaux de bord et statistiques agrégées");
        createPermission("referentiel.secteur.manage", "Gérer le référentiel des secteurs d'activité");
        createPermission("feedback.submit", "Donner son avis et répondre aux enquêtes de satisfaction");
        createPermission("feedback.enquete.manage", "Concevoir, publier et relancer les enquêtes de satisfaction");
        createPermission("feedback.dashboard.view", "Consulter le tableau de bord de la satisfaction des usagers");
        createPermission("reporting.reference.manage",
                "Saisir les valeurs de référence d'avant la digitalisation (indicateurs d'impact)");

        createPermission("commission.relais.list.entreprises", "Commission relais : lister les entreprises (choix d’impersonation)");
        createPermission("commission.relais.list.autorites", "Commission relais : lister les autorités contractantes");
        createPermission("commission.relais.impersonate.entreprise", "Commission relais : impersonation entreprise");
        createPermission("commission.relais.impersonate.autorite", "Commission relais : impersonation autorité contractante");
        createPermission("commission.relais.release", "Commission relais : quitter l’impersonation");
    }
    private void seedRolePermissions() {
        // Qui peut créer une convention peut la fermer : les trois rôles côté autorité contractante
        // reçoivent convention.activate, borné à leur périmètre par ConventionService.
        assign(Role.AUTORITE_CONTRACTANTE, "convention.activate");
        assign(Role.AUTORITE_UPM, "convention.activate");
        assign(Role.AUTORITE_UEP, "convention.activate");
        // Les mêmes rôles que la création. Le service restreint ensuite à l'autorité titulaire ou
        // créatrice, et aux conventions en attente de validation.
        assign(Role.AUTORITE_CONTRACTANTE, "convention.update");
        assign(Role.AUTORITE_UPM, "convention.update");
        assign(Role.AUTORITE_UEP, "convention.update");
        assign(Role.AUTORITE_CONTRACTANTE, "convention.annuler");
        assign(Role.AUTORITE_UPM, "convention.annuler");
        assign(Role.AUTORITE_UEP, "convention.annuler");

        assign(Role.AUTORITE_CONTRACTANTE,
                "projet.create",
                "projet.document.upload",
                "projet.view",
                "projet.update",
                "convention.create",
                "convention.view",
                "convention.document.upload",
                "document.requirements.view",
                "entreprise.list",
                "entreprise.create",
                "entreprise.update",
                "groupement.list",
                "groupement.create",
                "groupement.update",
                "groupement.delete",
                "bailleur.list",
                "bailleur.create",
                "devise.list",
                "devise.create",
                "taux_change.view",
                "delegue.list",
                // Plus de "delegue.create" : rattacher une personne à une entité relève du
                // Président depuis la réunion de recette. L'AC garde la lecture, la mise à jour des
                // coordonnées et la désactivation immédiate d'un agent qui part.
                "delegue.update",
                "delegue.disable",
                "correction.submit",
            "transfert.submit",
                "correction.offer.upload",
                "correction.offer.view",
                "correction.complement.add",
                "correction.visa.history.view",
                "correction.reclamation.submit",
                "correction.reclamation.annuler",
                "correction.demande.reactivate",
                "marche.manage",
                "mise_en_place.submit",
                "mise_en_place.document.upload",
                "mise_en_place.view",
                "modification.submit",
                "modification.document.upload",
                "modification.view",
                "utilisation.ac.view",
                "certificat.verification.scan",
                "reporting.view"
        );

        assign(Role.AUTORITE_UPM,
                "projet.create",
                "projet.document.upload",
                "projet.view",
                "projet.update",
                "convention.create",
                "convention.view",
                "convention.document.upload",
                "document.requirements.view",
                "entreprise.list",
                "entreprise.create",
                "entreprise.update",
                "groupement.list",
                "groupement.create",
                "groupement.update",
                "groupement.delete",
                "bailleur.list",
                "bailleur.create",
                "devise.list",
                "devise.create",
                "taux_change.view",
                "correction.submit",
                "correction.offer.upload",
                "correction.offer.view",
                "correction.complement.add",
                "correction.visa.history.view",
                "correction.reclamation.submit",
                "correction.reclamation.annuler",
                "marche.manage",
                "mise_en_place.submit",
                "mise_en_place.document.upload",
                "mise_en_place.view",
                "modification.submit",
                "modification.document.upload",
                "modification.view",
                "certificat.verification.scan",
                "reporting.view"
        );

        assign(Role.AUTORITE_UEP,
                "mise_en_place.annuler",
                "projet.create",
                "projet.document.upload",
                "projet.view",
                "projet.update",
                "convention.create",
                "convention.view",
                "convention.document.upload",
                "document.requirements.view",
                "entreprise.list",
                "entreprise.create",
                "entreprise.update",
                "groupement.list",
                "groupement.create",
                "groupement.update",
                "groupement.delete",
                "bailleur.list",
                "bailleur.create",
                "devise.list",
                "devise.create",
                "taux_change.view",
                "correction.submit",
                "correction.offer.upload",
                "correction.offer.view",
                "correction.complement.add",
                "correction.visa.history.view",
                "correction.reclamation.submit",
                "correction.reclamation.annuler",
                "marche.manage",
                "mise_en_place.submit",
                "mise_en_place.document.upload",
                "mise_en_place.view",
                "modification.submit",
                "modification.document.upload",
                "modification.view",
                "utilisation.ac.view",
                "certificat.verification.scan",
                "reporting.view"
        );

        assign(Role.ENTREPRISE,
                "mise_en_place.annuler",
                "correction.entreprise.queue.view",
                "correction.reclamation.submit",
                "correction.reclamation.annuler",
                "mise_en_place.entreprise.queue.view",
                "document.requirements.view",
                "entreprise.view.own",
                "utilisation.douane.submit",
                "utilisation.douane.document.upload",
                "utilisation.douane.solde.view",
                "utilisation.douane.history.view",
                "utilisation.douane.entreprise.cheque",
                "utilisation.interieur.submit",
                "utilisation.interieur.document.upload",
                "utilisation.interieur.solde.view",
                "utilisation.interieur.history.view",
                "utilisation.entreprise.rejet.repondre",
                "modification.submit",
                "modification.document.upload",
                "modification.view",
                "transfert.submit",
                "transfert.amount.set",
                "transfert.solde.view",
                "transfert.annuler",
                "transfert.entreprise.rejet.repondre",
                "sous_traitance.submit",
                "sous_traitance.solde.view",
                "sous_traitant.list",
                "certificat.verification.scan",
                "reporting.view"
        );

        assign(Role.SOUS_TRAITANT,
                "document.requirements.view",
                "entreprise.view.own",
                "utilisation.douane.submit",
                "utilisation.douane.document.upload",
                "utilisation.douane.solde.view",
                "utilisation.douane.history.view",
                "utilisation.douane.entreprise.cheque",
                "utilisation.interieur.submit",
                "utilisation.interieur.document.upload",
                "utilisation.interieur.solde.view",
                "utilisation.interieur.history.view",
                "utilisation.entreprise.rejet.repondre",
                "sous_traitance.submit",
                "sous_traitance.solde.view",
                "sous_traitant.list",
                "certificat.verification.scan",
                "reporting.view"
        );

        assign(Role.DGD,
                "signature.manage",
                "document.requirements.view",
                "convention.view.all",
                "marche.view",
                "entreprise.list",
                "correction.dgd.queue.view",
                "correction.offer.view",
                "correction.offer.upload",
                "correction.visa.history.view",
                "correction.status.update",
                "correction.dgd.evaluate.nomenclature",
                "correction.dgd.evaluate.valeur",
                "correction.dgd.calculate",
                "correction.dgd.save",
                "correction.dgd.transmit",
                "utilisation.douane.dgd.queue.view",
                "utilisation.douane.dgd.verify",
                "utilisation.douane.dgd.quittance.visa",
                "utilisation.douane.dgd.reject",
            "transfert.dgd.visa",
                "utilisation.douane.dgd.resolve",
                "mise_en_place.dgd.queue.view",
                "mise_en_place.dgd.validate",
                "mise_en_place.dgd.reject",
                "mise_en_place.dgd.resolve",
                "reporting.view",
                "demande.explication.view",
                "demande.explication.create",
                "demande.explication.reply",
                "demande.explication.close",
                "certificat.verification.scan"
        );

        assign(Role.DGI,
                "signature.manage",
                "document.requirements.view",
                "marche.view",
                "correction.dgi.queue.view",
                "correction.dgi.visa",
                "correction.dgi.reject",
                "correction.dgi.document.upload",
                "correction.status.update",
                "mise_en_place.dgi.queue.view",
                "mise_en_place.dgi.validate",
                "mise_en_place.dgi.reject",
                "mise_en_place.dgi.resolve",
                "utilisation.interieur.dgi.view",
                "utilisation.interieur.dgi.decision",
            "utilisation.interieur.dgi.quittance",
            "transfert.dgi.visa",
                "utilisation.interieur.dgi.resolve",
                "correction.offer.view",
                "convention.view.all",
                "convention.validate",
                "convention.reject",
                "correction.view.audit",
                "archivage.view",
                // Retirés : user.*, role.*, permissions.manage et le CRUD entreprise. Avec
                // user.role.assign et permissions.manage, la DGI pouvait s'attribuer n'importe quel
                // droit — très probablement un copier-coller du bloc administrateur. Elle garde la
                // consultation des entreprises et de la traçabilité, qui relèvent de son métier.
                "security.audit.view",
                "security.logins.view",
                "permissions.view",
                "entreprise.list",
                "groupement.list",
                "groupement.create",
                "groupement.update",
                "groupement.delete",
                "reporting.view",
                "demande.explication.view",
                "demande.explication.create",
                "demande.explication.reply",
                "demande.explication.close",
                "certificat.verification.scan"
        );

        assign(Role.DGB,
                "signature.manage",
                "document.requirements.view",
                "convention.view.all",
                "convention.validate",
                "convention.reject",
                "marche.view",
                "entreprise.list",
                "projet.validate",
                "projet.reject",
                "projet.view",
                "correction.dgb.queue.view",
                "correction.dgb.visa",
                "correction.dgb.reject",
                "correction.offer.view",
                "reporting.view",
                "demande.explication.view",
                "demande.explication.create",
                "demande.explication.reply",
                "demande.explication.close",
                "certificat.verification.scan");

        // Président : accès complet (toutes les permissions enregistrées)
        assignAllPermissions(Role.PRESIDENT);

        assign(Role.DGTCP,
                "signature.manage",
                "convention.view.all",
                "convention.activate",
                "marche.view",
                "correction.dgtcp.queue.view",
                "correction.dgtcp.review",
                "correction.dgtcp.finalize",
                "correction.dgtcp.visa",
                "correction.dgtcp.request_complements",
                "correction.dgtcp.reject",
                "correction.offer.view",
                "correction.status.update",
                "correction.reclamation.traiter",
                "mise_en_place.dgtcp.queue.view",
                "mise_en_place.dgtcp.validate",
                "mise_en_place.dgtcp.reject",
                "mise_en_place.dgtcp.open_credit",
                "mise_en_place.dgtcp.allocate",
                "mise_en_place.dgtcp.certificate.generate",
                "mise_en_place.dgtcp.certificate.send",
                "mise_en_place.dgtcp.resolve",
                "mise_en_place.view",
                "document.requirements.view",
                "utilisation.douane.dgtcp.queue.view",
                "utilisation.douane.dgtcp.impute",
                "utilisation.douane.dgtcp.solde.update",
                "utilisation.douane.dgtcp.history.view",
                "utilisation.douane.dgtcp.resolve",
                "utilisation.douane.dgtcp.envoyer.tresor",
                "utilisation.douane.dgtcp.quittances",
                "utilisation.douane.dgtcp.transmettre.president",
                "utilisation.dgtcp.cloturer",
                "utilisation.interieur.dgtcp.queue.view",
                "utilisation.interieur.dgtcp.verify",
                "utilisation.interieur.dgtcp.validate",
                "utilisation.interieur.dgtcp.solde.update",
                "utilisation.interieur.dgtcp.reject",
                "utilisation.interieur.dgtcp.resolve",
                "modification.dgtcp.queue.view",
                "modification.dgtcp.analyze",
                "modification.dgtcp.propose",
                "transfert.dgtcp.queue.view",
                "transfert.dgtcp.verify",
                "transfert.dgtcp.prepare",
                "transfert.dgtcp.update",
                "sous_traitance.dgtcp.queue.view",
                "sous_traitance.dgtcp.update",
                "entreprise.list",
                "reporting.view",
                "cloture.queue.view",
                "cloture.prepare",
                "cloture.report.view",
                "demande.explication.view",
                "demande.explication.create",
                "demande.explication.reply",
                "demande.explication.close",
                "certificat.verification.scan"
        );

        assign(Role.COMMISSION_RELAIS,
                "commission.relais.list.entreprises",
                "commission.relais.list.autorites",
                // L'entité à représenter peut ne pas encore exister : la commission relais la crée.
                "entreprise.create",
                "autorite.create",
                "commission.relais.impersonate.entreprise",
                "commission.relais.impersonate.autorite",
                "commission.relais.release",
                "document.requirements.view",
                "utilisation.douane.entreprise.cheque",
                "referentiel.taxe.manage"
        );

    assign(Role.ADMIN_SI, "reporting.reference.manage", "referentiel.secteur.manage",
            "feedback.enquete.manage", "feedback.dashboard.view");
    // Tout usager donne son avis ; l'administrateur SI exploite la plateforme, il n'en est pas l'usager.
    for (Role role : Role.values()) {
        if (role != Role.ADMIN_SI && role != Role.COMMISSION_RELAIS) {
            assign(role, "feedback.submit");
        }
    }
    assign(Role.ADMIN_SI,
        // Gestion des conventions, de leurs autorités et bailleurs, et des marchés (équipe, octobre 2026).
        "convention.update",
        "bailleur.list",
        "bailleur.update",
        // Avenants : consultation et substitution à la DGTCP ou au Président, motif obligatoire.
        "modification.view",
        "modification.admin_override",
        "signature.manage",
        "parametre.manage",
        "document.requirements.view",
        "document.types.view",
        "document.types.manage",
        "mise_en_place.annuler",
            "projet.view.all",
            "projet.validate",
            "projet.reject",
            "convention.view.all",
            "marche.view",
            "convention.validate",
            "convention.reject",
            "correction.view.audit",
            "correction.demande.reactivate_rejetee",
            "correction.admin_override",
            "correction.visa.admin_override",
            "certificat.admin_override",
            "certificat.visa.admin_override",
            "certificat.ouverture.admin_override",
            "utilisation.admin_override",
            "utilisation.certificat.admin_override",
            "archivage.view",
            "archive.import",
            // Reprise d'archive : l'administrateur doit pouvoir créer à la volée les entités
            // manquantes (bailleur → convention → marché) auxquelles rattacher le dossier importé.
            "bailleur.create",
            "convention.create",
            "marche.manage",
            "referentiel.taxe.manage",
            "user.create",
            "user.update",
            "user.disable",
            "user.reset",
            "user.list",
            "user.role.assign",
            "role.create",
            "role.permissions.update",
            "role.list",
            "role.disable",
            "security.audit.view",
            "security.logins.view",
            "permissions.manage",
            "permissions.view",
            "entreprise.list",
            "entreprise.create",
            "entreprise.update",
            "entreprise.delete",
            "groupement.list",
            "groupement.create",
            "groupement.update",
            "groupement.delete",
            "reporting.view",
            "certificat.verification.scan"
    );

        seedReferentielReadPermissions();
        seedCertificatVerificationScanPermission();
        retirerPermission("utilisation.douane.entreprise.reception");
    }
    private void retirerPermission(String code) {
        permissionRepository.findByCode(code).ifPresent(permission -> {
            for (Role role : Role.values()) {
                rolePermissionRepository.findByRoleAndPermission(role, permission)
                        .ifPresent(rolePermissionRepository::delete);
            }
            permissionRepository.delete(permission);
        });
    }
    private void seedReferentielReadPermissions() {
        for (Role role : Role.values()) {
            assign(role, "entreprise.list", "groupement.list", "marche.view");
            if (role == Role.ENTREPRISE || role == Role.SOUS_TRAITANT) {
                assign(role, "convention.view", "projet.view");
            } else {
                assign(role, "convention.view.all", "projet.view.all");
            }
        }
    }
    private void seedCertificatVerificationScanPermission() {
        for (Role role : Role.values()) {
            assign(role, "certificat.verification.scan");
        }
    }
private void createPermission(String code, String description) {
    if (!permissionRepository.existsByCode(code)) {
        permissionRepository.save(Permission.builder()
                .code(code)
                .description(description)
                .build());
    }
}
private void assign(Role role, String... permissionCodes) {
    for (String code : permissionCodes) {
        Permission permission = permissionRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Permission manquante: " + code));
        rolePermissionRepository.findByRoleAndPermission(role, permission)
                .orElseGet(() -> rolePermissionRepository.save(RolePermission.builder()
                        .role(role)
                        .permission(permission)
                        .build()));
    }
}
private void assignAllPermissions(Role role) {
    for (Permission permission : permissionRepository.findAll()) {
        if (PermissionsRevoqueesMigration.estSuppleanceAdministrateur(permission.getCode())) {
            continue;
        }
        rolePermissionRepository.findByRoleAndPermission(role, permission)
                .orElseGet(() -> rolePermissionRepository.save(RolePermission.builder()
                        .role(role)
                        .permission(permission)
                        .build()));
    }
}
}
