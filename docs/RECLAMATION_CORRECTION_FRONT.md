# Réclamation sur demande de correction — guide front-end

Ce document décrit le comportement métier et les API à brancher après une **réclamation** sur une demande de correction **adoptée** ou **notifiée**.

## Règles métier (résumé)

1. **Dépôt** : l’AC, les délégués UPM/UEP ou l’entreprise (même périmètre que la demande) déposent une réclamation lorsque le statut de la demande est **`ADOPTEE`** ou **`NOTIFIEE`**.
2. **Pièce jointe** : chaque réclamation **doit** inclure un fichier (multipart). Les métadonnées sont exposées dans le DTO (`pieceJointeNomFichier`, `pieceJointeChemin`, etc.).
3. **Une réclamation ouverte** : impossible d’en créer une deuxième tant qu’une réclamation est au statut **`SOUMISE`**.
4. **Annulation** (`SOUMISE` → `ANNULEE`, **avant** traitement DGTCP) : l’**auteur** de la réclamation, ou l’**AC** du même dossier, peut retirer la réclamation. Le **statut de la demande** (ex. `ADOPTEE`, `NOTIFIEE`) et les **visas** restent **inchangés**. Permission : `correction.reclamation.annuler`.
5. **Acceptation** : **seul le DGTCP** peut accepter (`acceptee: true`). **Rejet** (`acceptee: false`) : DGTCP ou Président ; **motif + document de réponse obligatoires** ; la **demande** et les **visas** restent **inchangés**. Détail du `PATCH` multipart : [RECLAMATION_TRAITEMENT_FRONT.md](./RECLAMATION_TRAITEMENT_FRONT.md).
6. **Après acceptation DGTCP** :
   - La demande repasse au statut **`RECUE`** (début de circuit).
   - Les **validations parallèles** (DGD, DGTCP, DGI, DGB) sont **remises à zéro** : les acteurs doivent **viser à nouveau**.
   - Les documents actifs de type **`LETTRE_ADOPTION`**, **`OFFRE_FISCALE_CORRIGEE`** et **`OFFRE_CORRIGEE`** sont **archivés** (`actif = false`). Ils restent en **historique** (version n−1, n−2…).
   - L’**AC** doit **retéléverser** une nouvelle **lettre d’adoption** et une nouvelle **offre corrigée** via l’upload habituel des documents de correction. Le backend attribue la **version suivante** (après la dernière version connue pour ce type), même s’il n’y a plus de document actif.

## Endpoints

### Lister les réclamations

`GET /api/demandes-correction/{id}/reclamations`

- Authentification JWT.
- Autorisations : alignées sur la consultation de la demande (files d’attente, audit, `correction.visa.history.view`, `correction.reclamation.*`, etc.).

Réponse : tableau de `ReclamationDemandeCorrectionDto`.

### Créer une réclamation (multipart obligatoire)

`POST /api/demandes-correction/{id}/reclamations`

- `Content-Type: multipart/form-data`
- Champs :
  - **`texte`** (string, obligatoire) : motif / exposé (max 4000 caractères côté serveur).
  - **`file`** (obligatoire) : pièce justificative.

Permission : `correction.reclamation.submit`.

### Annuler une réclamation en cours

`POST /api/demandes-correction/{demandeId}/reclamations/{reclamationId}/annuler`

- Uniquement si la réclamation est **`SOUMISE`**.
- **Qui** : auteur de la réclamation, ou utilisateur **AC** rattaché à la **même autorité contractante** que la demande.
- **Effet** : réclamation → **`ANNULEE`** uniquement ; **aucun** changement sur le statut de la demande ni sur les visas.
- Permission : `correction.reclamation.annuler`.

### Traiter une réclamation (DGTCP / Président)

`PATCH /api/demandes-correction/{demandeId}/reclamations/{reclamationId}`

- **`Content-Type: multipart/form-data`** (champs `acceptee`, `motifReponse`, `file` selon les cas).
- Voir le guide détaillé : **[RECLAMATION_TRAITEMENT_FRONT.md](./RECLAMATION_TRAITEMENT_FRONT.md)**.
- Permission : `correction.reclamation.traiter`.

### Documents de la demande (après acceptation)

Continuer d’utiliser :

`POST /api/demandes-correction/{id}/documents`

- `multipart/form-data` : `type` = `LETTRE_ADOPTION` ou `OFFRE_FISCALE_CORRIGEE` / `OFFRE_CORRIGEE` selon votre référentiel, + `file`.
- Les anciennes versions actives ont été désactivées à l’acceptation ; le prochain upload crée la **nouvelle version active** avec **numéro de version incrémenté**.

`GET /api/demandes-correction/{id}/documents` : retourne **toutes** les lignes (actives et historiques) avec `version` et `actif` pour l’UI (badges « courant » / « historique »).

## DTO `ReclamationDemandeCorrectionDto` (champs utiles front)

| Champ | Description |
|--------|-------------|
| `id` | Identifiant réclamation |
| `demandeCorrectionId` | Id demande |
| `statut` | `SOUMISE` \| `ACCEPTEE` \| `REJETEE` \| `ANNULEE` |
| `texte` | Texte saisi |
| `pieceJointeNomFichier` | Nom du fichier joint |
| `pieceJointeChemin` | URL / clé de stockage (affichage téléchargement selon votre couche API) |
| `pieceJointeTaille` | Taille en octets |
| `pieceJointeDateUpload` | Date de dépôt du fichier |
| `dateCreation` / `dateTraitement` | Cycle de vie |
| `auteurUserId` / `auteurNom` | Auteur |
| `traiteParUserId` / `motifReponse` | Traitement |
| `reponseRejetChemin` / `reponseRejetNomFichier` / … | Document de réponse si **rejet** |

## UX recommandée

1. **Écran détail demande** (`ADOPTEE` / `NOTIFIEE`) : bouton « Déposer une réclamation » → formulaire **texte + fichier** → `POST` multipart.
2. **File DGTCP** : file d’attente ou badge sur dossiers avec réclamation `SOUMISE` → détail → **Accepter** (multipart, sans fichier) / **Rejeter** (multipart : **motif + fichier** obligatoires).
3. **Après acceptation** (notification / refresh) : si `statut === 'RECUE'` :
   - Afficher un **bandeau** : nouvelle lettre d’adoption et offre corrigée **obligatoires** avant de poursuivre le circuit.
   - Proposer les uploads `LETTRE_ADOPTION` et offre corrigée (`OFFRE_FISCALE_CORRIGEE` ou `OFFRE_CORRIGEE` selon vos écrans existants).
   - Dans la liste des documents, filtrer ou styliser `actif === false` comme **historique (v{n})**.

## Codes d’erreur usuels

- **403** : rôle non autorisé (ex. Président qui tente `acceptee: true`).
- **409** : réclamation `SOUMISE` déjà présente sur la demande.
- **400** : fichier manquant à la création ; motif manquant au rejet ; statut de la demande incompatible avec une réclamation.

## Changement par rapport à une version antérieure de l’API

- La création de réclamation n’est plus en **JSON seul** : elle est **`multipart/form-data`** avec **`texte`** + **`file`** obligatoires.
- L’acceptation qui rouvre le dossier place la demande en **`RECUE`** (plus `EN_EVALUATION`).
- Le **traitement** (acceptation / rejet) du `PATCH` réclamation est en **multipart**, avec **motif + fichier obligatoires** pour le **rejet** ; le **rejet** ne modifie **ni** le statut **ni** les visas de la demande.
