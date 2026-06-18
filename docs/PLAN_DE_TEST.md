# Plan de test post-livraison — SGCI
**Version 4.0 — Phase finale avant mise en production**

---

## Ordre d'exécution recommandé

```
Phase 1 (Infra) → Phase 2 (Admin/Réf) → Phase 3 (Convention/Marché)
→ Phase 4 (Correction) → Phase 5 (Certificat)
→ Phase 6 (Utilisation Douane) → Phase 7 (Utilisation TVA)
→ Phase 8 (Transfert) → Phase 9 (Sous-traitance)
→ Phase 10 (Clôture) → Phase 11 (GED) → Phase 12 (Notif/Relais)
→ Phase 13 (Reporting/Audit) → Phase 14 (Régression)
```

**Comptes de test minimum requis :** `admin`, `president`, `dgtcp`, `dgd`, `dgi`, `dgb`, `ac`, `entreprise`, `sous_traitant`, `commission_relais`

---

## PHASE 1 — Infrastructure & Sécurité (Pré-requis)

> Objectif : valider que la plateforme démarre correctement et que les bases de sécurité fonctionnent avant tout test fonctionnel.

### 1.1 Démarrage & Santé

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 1.1.1 | Health check | `GET /actuator/health` | `status: UP` |
| 1.1.2 | Base de données | Vérifier les logs au démarrage | Aucune erreur Hibernate / migration |
| 1.1.3 | Migration schéma | Vérifier colonnes `notification.type` et `entity_type` | `VARCHAR(64)` minimum |
| 1.1.4 | Données seed | Connexion admin → lister utilisateurs | Comptes DGD, DGI, DGB, DGTCP, Président, AC, Entreprise présents |

### 1.2 Authentification

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 1.2.1 | Login valide | `POST /api/auth/login` avec identifiants corrects | `200 OK` + token JWT |
| 1.2.2 | Login invalide | Mauvais mot de passe | `401 UNAUTHORIZED` |
| 1.2.3 | Token expiré | Appel avec JWT expiré | `401 UNAUTHORIZED` |
| 1.2.4 | Sans token | `GET /api/certificats-credit` sans Authorization | `401 UNAUTHORIZED` |
| 1.2.5 | `/api/auth/me` | Appel avec token valide | Retourne `userId`, `role`, `authorities` |
| 1.2.6 | Mauvais rôle | Entreprise tente `GET /api/demandes-correction` avec filtre admin | Résultat filtré à son périmètre uniquement |

### 1.3 Changement de mot de passe

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 1.3.1 | Changement valide | `PATCH /api/utilisateurs/me/password` avec ancien MDP correct + nouveau ≥ 8 chars | `200 OK` |
| 1.3.2 | Ancien MDP incorrect | Mauvais `currentPassword` | `400 BAD_REQUEST` |
| 1.3.3 | Nouveau MDP trop court | `newPassword` < 8 caractères | `400 BAD_REQUEST` |
| 1.3.4 | Reconnexion | Se connecter avec le nouveau MDP | `200 OK` + token |

---

## PHASE 2 — Référentiels & Administration

> Objectif : valider que l'Admin peut gérer tous les référentiels avant que les workflows fonctionnels soient testés.

### 2.1 Gestion des utilisateurs (ADMIN_SI)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 2.1.1 | Lister utilisateurs | `GET /api/utilisateurs` en tant qu'Admin | Liste complète |
| 2.1.2 | Créer utilisateur DGD | `POST /api/auth/register` avec `role: DGD` | `201 CREATED` |
| 2.1.3 | Activer compte | Activer le compte créé | Compte passe à `actif=true` |
| 2.1.4 | Désactiver compte | Désactiver le compte | Login refusé |
| 2.1.5 | Modifier utilisateur | `PATCH` informations | `200 OK` |

### 2.2 Réinitialisation de mot de passe

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 2.2.1 | Vérification email | `POST /api/auth/password-reset/check-email` avec email connu | `found: true` |
| 2.2.2 | Email inconnu | Même endpoint avec email inexistant | `found: false` |
| 2.2.3 | Demande de reset | `POST /api/auth/password-reset/request` | `202 ACCEPTED` + notification DB |
| 2.2.4 | Traitement Admin | Admin approuve la demande | Notification `PASSWORD_RESET_TRAITEE` créée |
| 2.2.5 | E-mail reset | Vérifier boîte mail du demandeur | E-mail reçu avec les nouvelles informations |

### 2.3 Gestion des permissions (ADMIN_SI)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 2.3.1 | Lister permissions | `GET /api/permissions` | Liste complète des permissions |
| 2.3.2 | Permissions par rôle | `GET /api/permissions/by-role?role=DGD` | Permissions DGD exactes |
| 2.3.3 | Modifier permission | Retirer une permission d'un rôle | Utilisateur concerné perd l'accès immédiatement (après reconnexion) |

### 2.4 Types de documents & Exigences (ADMIN_SI)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 2.4.1 | Lister types de docs | `GET /api/referentiel-type-document` | Liste des codes de documents |
| 2.4.2 | Créer type de doc | `POST` avec code + libellé | `201 CREATED` |
| 2.4.3 | Lister exigences | `GET /api/document-requirements` | Exigences par processus |
| 2.4.4 | Créer exigence | Ajouter un document obligatoire à un processus | Document devient requis |

### 2.5 Référentiels (Devises, Taux, Projets, Bailleurs)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 2.5.1 | Taux de change forex | `GET /api/forex/rate?from=USD&to=MRU` | Taux retourné |
| 2.5.2 | Conversion montant | `GET /api/forex/convert?from=USD&to=MRU&amount=1000` | Montant converti |
| 2.5.3 | Taux interne | `GET /api/taux-change?devise=USD` | Taux MRU retourné |
| 2.5.4 | Lister devises | `GET /api/devises` | Liste des devises |
| 2.5.5 | Lister bailleurs | `GET /api/bailleurs` | Liste des bailleurs |

---

## PHASE 3 — Workflow Convention & Marché

> Comptes nécessaires : AC (ou UPM/UEP), DGB, Président.

### 3.1 Créer une convention (AC)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 3.1.1 | Créer convention | `POST /api/conventions` avec bailleur, montant, devise | `201 CREATED` + statut `EN_ATTENTE` |
| 3.1.2 | Lister conventions AC | `GET /api/conventions` en tant qu'AC | Uniquement ses conventions |
| 3.1.3 | Consulter détail | `GET /api/conventions/{id}` | Détail complet |
| 3.1.4 | Téléverser document | `POST /api/conventions/{id}/documents` multipart | Document enregistré |
| 3.1.5 | Remplacer document | `PUT /api/conventions/{id}/documents/{docId}` | Nouvelle version `v2` créée, ancienne inactive |
| 3.1.6 | Supprimer document | `DELETE /api/conventions/{id}/documents/{docId}` | `200 OK` |

### 3.2 Valider la convention (DGB / DGI / Président)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 3.2.1 | Valider (DGB) | `PATCH /api/conventions/{id}/statut` avec `statut=VALIDE` | `200 OK` + statut `VALIDE` |
| 3.2.2 | Rejeter (DGB) | `PATCH` avec `statut=REJETE` + motif | Statut `REJETE` + motif enregistré |
| 3.2.3 | Annuler (AC) | `PATCH` avec `statut=ANNULEE` | Statut `ANNULEE` |
| 3.2.4 | Notification | Vérifier `GET /api/notifications` (AC) | Notification `CONVENTION_STATUT_CHANGE` reçue |
| 3.2.5 | E-mail | Vérifier boîte mail AC | E-mail de validation/rejet reçu |

### 3.3 Gérer les marchés (AC)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 3.3.1 | Créer marché | `POST /api/marches` lié à convention validée | `201 CREATED` |
| 3.3.2 | Modifier marché | `PUT /api/marches/{id}` | `200 OK` |
| 3.3.3 | Lister marchés | `GET /api/marches` | Liste filtrée à l'AC |
| 3.3.4 | Vérif correction active | `GET /api/marches/{id}/demande-correction-active` | `false` si aucune demande |
| 3.3.5 | Documents marché | `POST /api/marches/{id}/documents` multipart | Document enregistré |

### 3.4 Gérer les délégués (AC)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 3.4.1 | Créer délégué | `POST /api/delegues` | `201 CREATED` |
| 3.4.2 | Affecter au marché | `PATCH /api/marches/{id}/assign` | Délégué principal affecté |
| 3.4.3 | Ajouter délégué supp. | `POST /api/marches/{id}/delegues` | `201 CREATED` |
| 3.4.4 | Lister marchés délégué | `GET /api/delegues/{id}/marches` | Marchés affectés |
| 3.4.5 | Désactiver délégué | `PATCH /api/delegues/{id}/actif?actif=false` | Login refusé pour le délégué |
| 3.4.6 | Retirer délégué | `DELETE /api/marches/{id}/delegues/{delegueId}` | `200 OK` |

---

## PHASE 4 — Workflow Demande de Correction

> Comptes : AC, DGD, DGTCP, DGI, DGB, Président, Entreprise.

### 4.1 Soumission (AC)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 4.1.1 | Créer brouillon | `POST /api/demandes-correction` | `201 CREATED` + statut `BROUILLON` |
| 4.1.2 | Modifier brouillon | `PUT /api/demandes-correction/{id}` | `200 OK` |
| 4.1.3 | Joindre offre | `POST /api/demandes-correction/{id}/documents` | Document code `OFFRE` enregistré |
| 4.1.4 | Soumettre | `POST /api/demandes-correction/{id}/soumettre` | Statut → `RECUE` |
| 4.1.5 | Supprimer brouillon | `DELETE` sur brouillon non soumis | `200 OK` |
| 4.1.6 | Supprimer soumis | Tenter DELETE sur demande `RECUE` | `403 FORBIDDEN` |

### 4.2 Circuit DGD

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 4.2.1 | Visa DGD + offre corrigée | DGD : Visa sans `OFFRE_FISCALE_CORRIGEE` → modale upload → visa | Document actif + décision VISA DGD |
| 4.2.2 | Rejet définitif DGD | `decision=REJET_DEFINITIF` | Statut → `REJETEE` |
| 4.2.3 | Rejet temporaire DGD | `decision=REJET_TEMPORAIRE` avec motif | Statut → `INCOMPLETE` |
| 4.2.4 | Réponse AC | `POST /api/demandes-correction/decisions/{decId}/rejet-temp/reponses` | Réponse enregistrée |
| 4.2.5 | Résolution | `PUT /api/demandes-correction/decisions/{decId}/resolve` | Statut → `A_RECONTROLER` |
| 4.2.6 | Notification | `GET /api/notifications` (AC) | `REJET_TEMP_DECISION` + `REJET_TEMP_RESOLU` reçus |

### 4.3 Circuit DGTCP → DGI → DGB

| # | Test | Compte | Résultat attendu |
|---|---|---|---|
| 4.3.1 | Visa DGTCP | `decision=VISA` (DGTCP) | Décision enregistrée |
| 4.3.2 | Visa DGI + upload crédit intérieur | DGI : clic Visa sans `CREDIT_INTERIEUR` → modale upload → visa | Document `CREDIT_INTERIEUR` actif + décision VISA DGI |
| 4.3.3 | Visa DGB | `decision=VISA` (DGB) | Décision enregistrée |
| 4.3.4 | Historique décisions | `GET /api/demandes-correction/{id}/decisions` | 3 visas listés |
| 4.3.5 | Garde upload DGI | DGI tente `POST .../documents?codeDocument=LETTRE_SAISINE` | `403` — seul `CREDIT_INTERIEUR` autorisé |
| 4.3.6 | Garde visa DGI | DGI tente `decision=VISA` sans document actif | `400` — document `CREDIT_INTERIEUR` requis |

### 4.4 Décision Président

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 4.4.1 | Adopter | `PATCH /api/demandes-correction/{id}/statut?statut=ADOPTEE` | Statut `ADOPTEE` |
| 4.4.2 | Générer lettre | Action de génération de lettre | Document lettre généré |
| 4.4.3 | Déposer signature | Upload document signé | Document signé enregistré |
| 4.4.4 | Notifier | `statut=NOTIFIEE` | Statut `NOTIFIEE` |
| 4.4.5 | Rejeter | `statut=REJETEE` | Statut `REJETEE` |
| 4.4.6 | Notifications | Vérifier AC + Entreprise | `CORRECTION_ADOPTEE` reçu |
| 4.4.7 | E-mail adoption | Vérifier boîte mail | E-mails envoyés aux destinataires |

### 4.5 Réclamations (après adoption)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 4.5.1 | Déposer réclamation | `POST /api/demandes-correction/{id}/reclamations` (Entreprise) | `201 CREATED` |
| 4.5.2 | Lister réclamations | `GET /api/demandes-correction/{id}/reclamations` | Réclamation listée |
| 4.5.3 | Annuler réclamation | `POST .../reclamations/{recId}/annuler` (avant traitement) | `200 OK` |
| 4.5.4 | Accepter réclamation | `PATCH .../reclamations/{recId}` avec `acceptee=true` (DGTCP) | `200 OK` |
| 4.5.5 | Rejeter réclamation | `acceptee=false` + motif | `200 OK` |

### 4.6 Demandes d'explication

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 4.6.1 | Créer explication | `POST /api/demandes-explication` (DGD) avec `contexte=CORRECTION` | `201 CREATED` |
| 4.6.2 | Répondre | `POST /api/demandes-explication/{id}/messages` (AC) | Message ajouté |
| 4.6.3 | Fermer | `PUT /api/demandes-explication/{id}/fermer` | Statut `FERMEE` |

---

## PHASE 5 — Workflow Mise en place du Certificat

> Comptes : AC/UPM, DGTCP, DGI, DGD, Président.

### 5.1 Création & soumission (AC)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 5.1.1 | Créer certificat | `POST /api/certificats-credit` lié à demande `ADOPTEE` | `201 CREATED` + statut `ENVOYEE` |
| 5.1.2 | Modifier brouillon | `PUT /api/certificats-credit/{id}` | `200 OK` |
| 5.1.3 | Joindre documents | `POST /api/certificats-credit/{id}/documents` | Documents requis joints |
| 5.1.4 | Supprimer brouillon | `DELETE /api/certificats-credit/{id}` | `200 OK` |
| 5.1.5 | Double soumission | Créer 2 certificats sur la même demande | `409` ou erreur métier |

### 5.2 Contrôle & Visa (DGD / DGI / DGTCP)

| # | Test | Compte | Résultat attendu |
|---|---|---|---|
| 5.2.1 | Prise en charge DGD | `POST /api/certificats-credit/{id}/prendre-en-charge` | Statut → `EN_CONTROLE` |
| 5.2.2 | Définir montants DGTCP | `PATCH /api/certificats-credit/{id}/montants` avec `montantCordon` + `montantTVAInterieure` | Montants enregistrés |
| 5.2.3 | Visa DGI | `POST .../decisions` avec `decision=VISA` | `201 CREATED` |
| 5.2.4 | Visa DGD | Idem | `201 CREATED` |
| 5.2.5 | Visa DGTCP | Idem | `201 CREATED` + statut → `EN_VALIDATION_PRESIDENT` |
| 5.2.6 | Rejet temporaire DGD | `decision=REJET_TEMPORAIRE` | Statut → `INCOMPLETE` |
| 5.2.7 | Réponse entreprise | Upload compléments | Documents ajoutés |
| 5.2.8 | Résolution rejet | `PUT .../decisions/{decId}/resolve` | Statut → `A_RECONTROLER` |

### 5.3 Validation Président & Ouverture DGTCP

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 5.3.1 | Valider (Président) | `PATCH .../statut?statut=VALIDE_PRESIDENT` | Statut → `VALIDE_PRESIDENT` |
| 5.3.2 | Générer document | Génération du certificat signé | Document PDF généré |
| 5.3.3 | Ouvrir crédit DGTCP | `PATCH .../statut?statut=OUVERT` (DGTCP) | Statut → `OUVERT` + `soldeCordon` initialisé |
| 5.3.4 | Vérifier soldes | `GET /api/certificats-credit/{id}` | `soldeCordon` = `montantCordon`, `soldeTVA` = `montantTVAInterieure` |
| 5.3.5 | Stock TVA | `GET /api/certificats-credit/{id}/tva-stock` | Stock TVA déductible initialisé |
| 5.3.6 | Notification | Vérifier notifications Entreprise | `CERTIFICAT_OUVERT` reçu |
| 5.3.7 | Annuler | `statut=ANNULE` (Président) | Statut → `ANNULE`, soldes à 0 |

---

## PHASE 6 — Workflow Utilisation Douane

> Comptes : Entreprise, DGD, DGTCP.

### 6.1 Création & soumission (Entreprise)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 6.1.1 | Créer utilisation | `POST /api/utilisations-credit` avec `type=DOUANIER` | `201 CREATED` + statut `DEMANDEE` |
| 6.1.2 | Certificat fermé | Tenter utilisation sur certificat `CLOTURE` | Erreur métier |
| 6.1.3 | Montant > solde | `montantDroits` > `soldeCordon` du certificat | Erreur métier / `400` |
| 6.1.4 | Solde = 0 | Tenter utilisation avec solde à 0 | Erreur métier |
| 6.1.5 | Modifier brouillon | `PUT /api/utilisations-credit/{id}` | `200 OK` |
| 6.1.6 | Supprimer brouillon | `DELETE /api/utilisations-credit/{id}` | `200 OK` |
| 6.1.7 | Documents | `POST .../documents` (BL, déclaration SYDONIA) | Documents enregistrés |

### 6.2 Contrôle DGD & Visa bulletin

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 6.2.1 | Prise en charge | `PATCH .../statut?statut=EN_VERIFICATION` | Statut → `EN_VERIFICATION` |
| 6.2.2 | Rejet définitif | `PATCH .../statut?statut=REJETEE` | Statut → `REJETEE`, solde non débité |
| 6.2.3 | Rejet temporaire | `POST .../decisions` avec `REJET_TEMPORAIRE` | Statut → `INCOMPLETE` |
| 6.2.4 | Réponse entreprise | Upload compléments | Documents ajoutés |
| 6.2.5 | Résolution | `PUT .../decisions/{decId}/resolve` | Statut → `A_RECONTROLER` |
| 6.2.6 | Visa DGD bulletin | `POST /api/utilisations-credit/{id}/visa-dgd` avec annotations lignes | Lignes `AU_CI` / `A_PAYER` mises à jour |
| 6.2.7 | Consulter lignes | `GET /api/utilisations-credit/{id}/lignes-bulletin` | Lignes avec montants DGD |
| 6.2.8 | Notification visa | Vérifier notifications Entreprise | `UTIL_DOUANE_VISA_DGD` reçu |

### 6.3 Saisie chèque & Envoi Trésor (Entreprise + DGTCP)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 6.3.1 | Saisir chèque | `POST /api/utilisations-credit/{id}/cheque` (Entreprise) | Statut → `CHEQUE_SAISI` |
| 6.3.2 | Chèque sans visa | Tenter saisie avant visa DGD | Erreur métier |
| 6.3.3 | Envoyer au Trésor | `POST /api/utilisations-credit/{id}/envoyer-au-tresor` (DGTCP) | Statut → `ENVOYEE_AU_TRESOR` |
| 6.3.4 | Saisir quittances | `POST /api/utilisations-credit/{id}/quittances` (DGTCP) | Statut → `QUITTANCES_ENREGISTREES` |
| 6.3.5 | Consulter quittances | `GET /api/utilisations-credit/{id}/quittances` | Quittances listées |

### 6.4 Liquidation & Clôture

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 6.4.1 | Liquidation douane | `POST /api/utilisations-credit/{id}/liquidation-douane` (DGTCP) | Statut → `LIQUIDEE` + solde cordon débité |
| 6.4.2 | Vérifier solde | `GET /api/certificats-credit/{id}` | `soldeCordon` = ancienSolde − montantLiquidé |
| 6.4.3 | Solde négatif | Vérifier que solde ≥ 0 après liquidation | Impossible si montant > solde |
| 6.4.4 | Accuser réception | `POST /api/utilisations-credit/{id}/cloturer-reception` (Entreprise) | Statut → `CLOTUREE` |
| 6.4.5 | Notification clôture | Vérifier notifications Entreprise | `UTIL_DOUANE_CLOTUREE` reçu |
| 6.4.6 | E-mail liquidation | Vérifier boîte mail | E-mail de liquidation reçu |

---

## PHASE 7 — Workflow Utilisation TVA Intérieure

> Comptes : Entreprise, DGI, DGTCP.

| # | Test | Compte | Action | Résultat attendu |
|---|---|---|---|---|
| 7.1 | Créer utilisation TVA | Entreprise | `POST /api/utilisations-credit` avec `type=TVA_INTERIEURE` | `201 CREATED` + statut `DEMANDEE` |
| 7.2 | Montant > soldeTVA | Entreprise | Montant > `soldeTVA` du certificat | Erreur métier |
| 7.3 | TVA déductible stock | Entreprise | `GET /api/certificats-credit/{id}/tva-stock` | Stock TVA disponible retourné |
| 7.4 | Instruction DGI | DGI | `POST .../decisions` avec `decision=VISA` | Décision enregistrée |
| 7.5 | Rejet temporaire DGI | DGI | `REJET_TEMPORAIRE` + motif | Statut → `INCOMPLETE` |
| 7.6 | Réponse + résolution | Entreprise / DGI | Upload + `resolve` | Statut → `A_RECONTROLER` |
| 7.7 | Vérification DGTCP | DGTCP | `PATCH .../statut?statut=EN_VERIFICATION` | Statut → `EN_VERIFICATION` |
| 7.8 | Validation DGTCP | DGTCP | `statut=VISE` | Statut → `VISE` |
| 7.9 | Apurement TVA | DGTCP | `POST /api/utilisations-credit/{id}/apurement-tva` | Statut → `APUREE` + soldeTVA débité |
| 7.10 | Vérifier soldeTVA | Tous | `GET /api/certificats-credit/{id}` | `soldeTVA` = ancien − montant apuré |
| 7.11 | Rejet définitif | DGTCP | `statut=REJETEE` | Solde non débité |
| 7.12 | Notification | Entreprise | `GET /api/notifications` | `UTIL_TVA_APUREE` reçu |

---

## PHASE 8 — Workflow Transfert de Crédit

> Comptes : Entreprise, DGTCP, Président.

| # | Test | Compte | Action | Résultat attendu |
|---|---|---|---|---|
| 8.1 | Créer transfert | Entreprise | `POST /api/transferts-credit` | `201 CREATED` + statut `DEMANDE` |
| 8.2 | Déposer pièces | Entreprise | `POST /api/transferts-credit/{id}/documents` | Statut → `EN_COURS` automatique |
| 8.3 | Lister transferts | DGTCP | `GET /api/transferts-credit` | File DGTCP visible |
| 8.4 | Validation DGTCP | DGTCP | `POST /api/transferts-credit/{id}/valider` | Statut → `TRANSFERE` + soldes réaffectés |
| 8.5 | Vérifier soldes | Tous | `GET /api/certificats-credit/{id}` | Soldes mis à jour après transfert |
| 8.6 | Rejet définitif | DGTCP | `POST /api/transferts-credit/{id}/rejeter` | Statut → `REJETE` |
| 8.7 | Rejet temporaire | DGTCP | `POST .../decisions` `REJET_TEMPORAIRE` | Statut → `INCOMPLETE` |
| 8.8 | Réponse entreprise | Entreprise | Upload compléments | Réponse enregistrée |
| 8.9 | Résolution | DGTCP | `PUT .../decisions/{decId}/resolve` | Statut → `A_RECONTROLER` |
| 8.10 | Validation Président | Président | `POST /api/transferts-credit/{id}/valider` | `200 OK` |
| 8.11 | Annulation | Entreprise | `POST /api/transferts-credit/{id}/annuler` | Statut → `ANNULEE` |
| 8.12 | Annuler après exec. | Entreprise | Annuler un transfert `TRANSFERE` | Erreur métier |
| 8.13 | Notification | Entreprise | `GET /api/notifications` | `TRANSFERT_VALIDE` / `TRANSFERT_REJETE` reçu |

---

## PHASE 9 — Workflow Sous-traitance

> Comptes : Entreprise (titulaire), DGTCP, Sous-traitant.

| # | Test | Compte | Action | Résultat attendu |
|---|---|---|---|---|
| 9.1 | Onboarding | Entreprise | `POST /api/sous-traitances/onboarding` | Compte sous-traitant + dossier créés |
| 9.2 | Soumettre demande | Entreprise | `POST /api/sous-traitances` | `201 CREATED` + statut `DEMANDE` |
| 9.3 | Lister (DGTCP) | DGTCP | `GET /api/sous-traitances` | File DGTCP visible |
| 9.4 | Autoriser | DGTCP | `POST /api/sous-traitances/{id}/autoriser` | Statut → `AUTORISEE` |
| 9.5 | Refuser | DGTCP | `POST /api/sous-traitances/{id}/refuser` | Statut → `REFUSEE` |
| 9.6 | Suspendre | Entreprise | `POST .../suspendre-titulaire` | Statut → `SUSPENDUE` |
| 9.7 | Réactiver | Entreprise | `POST .../reactiver-titulaire` | Statut → `AUTORISEE` |
| 9.8 | Révoquer | Entreprise | `POST .../revoquer-titulaire` | Statut → `REVOQUEE` (irréversible) |
| 9.9 | Accès sous-traitant | Sous-traitant | `GET /api/certificats-credit` | Voit uniquement le certificat partagé |
| 9.10 | Utilisation ST | Sous-traitant | Créer utilisation sur certificat partagé | Utilisation dans les limites du solde alloué |
| 9.11 | Lister entreprises ST | Entreprise | `GET /api/sous-traitances/entreprises-sous-traitantes` | Liste des sous-traitants du titulaire |

---

## PHASE 10 — Workflow Clôture du Certificat

> Comptes : DGTCP, Président.

| # | Test | Compte | Action | Résultat attendu |
|---|---|---|---|---|
| 10.1 | File éligibles | DGTCP | `GET /api/clotures-credit/queue` | Certificats avec motifs de blocage |
| 10.2 | Certificats clôturables | DGTCP | `GET /api/clotures-credit/eligible` | IDs des certificats immédiatement clôturables |
| 10.3 | Proposer clôture | DGTCP | `POST /api/clotures-credit` | Proposition créée |
| 10.4 | Documents clôture | DGTCP | `POST /api/clotures-credit/{id}/documents` | Documents joints |
| 10.5 | File Président | Président | `GET /api/clotures-credit/propositions` | Proposition visible |
| 10.6 | Valider clôture | Président | `POST /api/clotures-credit/{id}/valider` | `200 OK` |
| 10.7 | Rejeter clôture | Président | `POST /api/clotures-credit/{id}/rejeter` | `200 OK` |
| 10.8 | Finaliser | DGTCP | `POST /api/clotures-credit/{id}/finaliser` | Certificat → `CLOTURE` |
| 10.9 | Vérif post-clôture | Tous | Tenter utilisation sur certificat `CLOTURE` | Erreur métier |
| 10.10 | Notification | Entreprise | `GET /api/notifications` | `CLOTURE_FINALISEE` reçu + e-mail |

---

## PHASE 11 — GED & Documents (versionnage)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 11.1 | Upload v1 | `POST .../documents` (document quelconque) | Version 1 créée, `actif=true` |
| 11.2 | Remplacer → v2 | `PUT .../documents/{id}` avec nouveau fichier | Version 2 créée, v1 passe à `actif=false` |
| 11.3 | Versions conservées | Consulter dossier GED | V1 et V2 présentes (v1 inactive) |
| 11.4 | Dossier GED | `GET /api/dossiers` | Dossiers accessibles selon rôle |
| 11.5 | Dossier détail | `GET /api/dossiers/{id}` | Arborescence complète avec versions |
| 11.6 | Admin config types | Créer type doc + exigence | Document devient obligatoire dans le processus ciblé |

---

## PHASE 12 — Notifications & Commission Relais

### 12.1 Notifications in-app

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 12.1.1 | Lister notifications | `GET /api/notifications` | Notifications de la session |
| 12.1.2 | Compteur non lus | `GET /api/notifications/unread-count` | Nombre correct |
| 12.1.3 | Marquer lue | `PATCH /api/notifications/{id}/read` | `isRead=true` |
| 12.1.4 | Tout marquer lu | `PATCH /api/notifications/read-all` | Toutes `isRead=true` |

### 12.2 E-mails (vérification manuelle ou logs)

| # | Événement | Destinataires attendus |
|---|---|---|
| 12.2.1 | Correction soumise | Président + DGD + membres Commission |
| 12.2.2 | Correction adoptée | AC + Entreprise |
| 12.2.3 | Certificat ouvert | Entreprise |
| 12.2.4 | Utilisation Douane liquidée | Entreprise |
| 12.2.5 | Transfert validé / rejeté | Entreprise |
| 12.2.6 | Clôture finalisée | Entreprise |
| 12.2.7 | Reset MDP approuvé | Utilisateur demandeur |

### 12.3 Commission Relais (impersonation)

| # | Test | Action | Résultat attendu |
|---|---|---|---|
| 12.3.1 | Lister entreprises | `GET /api/commission-relais/entreprises` | Liste paginée |
| 12.3.2 | Impersonate entreprise | `POST /api/commission-relais/impersonate/entreprise` | Nouveau JWT rôle entreprise |
| 12.3.3 | Action en tant qu'entreprise | Créer utilisation avec le JWT impersoné | `201 CREATED` |
| 12.3.4 | Release | `POST /api/commission-relais/release` | JWT Commission Relais restauré |
| 12.3.5 | Impersonate AC | `POST /api/commission-relais/impersonate/autorite-contractante` | Nouveau JWT rôle AC |

---

## PHASE 13 — Reporting & Audit

| # | Test | Compte | Action | Résultat attendu |
|---|---|---|---|---|
| 13.1 | Synthèse nationale | Président | `GET /api/reporting/summary` | Agrégats complets toutes AC |
| 13.2 | Synthèse filtrée | DGTCP | `GET .../summary?entrepriseId=X` | Données filtrées à l'entreprise X |
| 13.3 | Synthèse AC | AC | `GET /api/reporting/summary` | Données limitées à son périmètre |
| 13.4 | Synthèse Entreprise | Entreprise | `GET /api/reporting/summary` | Uniquement ses propres dossiers |
| 13.5 | Série temporelle | Président | `GET /api/reporting/timeseries/demandes` | Points mensuels |
| 13.6 | Période filtrée | Admin | `?from=2026-01-01T00:00:00Z&to=2026-06-01T00:00:00Z` | Données dans la fenêtre |
| 13.7 | Journal audit | Admin | `GET /api/audit-logs` | Actions sensibles tracées |
| 13.8 | Snapshot objet | Admin | Consulter un log | `objectSnapshot` JSON présent |

---

## PHASE 14 — Tests de régression & Cas limites

| # | Cas limite | Test | Résultat attendu |
|---|---|---|---|
| 14.1 | Solde exact | Montant utilisation = solde exact restant | Accepté, solde → 0 |
| 14.2 | Double soumission | Soumettre deux fois le même brouillon | Erreur à la 2e tentative |
| 14.3 | Workflow hors ordre | DGTCP tente de viser avant DGI | Erreur métier |
| 14.4 | Certificat non ouvert | Utilisation sur certificat `EN_CONTROLE` | Erreur métier |
| 14.5 | Upload fichier vide | `POST .../documents` avec fichier 0 bytes | `400 BAD_REQUEST` |
| 14.6 | Token autre user | Utiliser le token de l'entreprise A sur ressources de B | `403 FORBIDDEN` |
| 14.7 | Pagination reporting | `?from=` très ancienne date | Résultats corrects sans erreur |
| 14.8 | Mail timeout | Serveur SMTP indisponible | Transaction métier réussie, mail en échec logué uniquement |
| 14.9 | Rejet temp x2 | Deux rejets temporaires ouverts simultanément | Les deux doivent être résolus avant de continuer |
| 14.10 | Solde négatif impossible | Liquidation > solde restant | Rejet avec message d'erreur clair |
| 14.11 | Révocation sous-traitance | Tenter utilisation après révocation | Accès refusé |
| 14.12 | Demande explication fermée | Envoyer un message sur demande `FERMEE` | `400 BAD_REQUEST` |
