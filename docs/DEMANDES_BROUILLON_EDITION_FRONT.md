# Brouillon, édition et soumission — guide front (correction, utilisation, mise en place)

## 1. Correction — `DemandeCorrection`

### Bug corrigé (marché à l’annulation)

L’association `DemandeCorrection` → `Marché` **ne supprime plus** le marché lors d’une annulation : le marché est **détaché** (`demande_correction_id` à `null` sur le marché) et la demande conserve `marcheIdTrace` pour la traçabilité.

### Statut `BROUILLON`

- Nouvelle valeur d’énumération : **`BROUILLON`**.
- Création en brouillon : corps `POST /api/demandes-correction` avec **`"brouillon": true`**.
  - `modeleFiscal` et `dqe` peuvent être absents ou minimaux.
  - **Pas de notification** aux services tant que le statut reste `BROUILLON`.

### Soumission brouillon → `RECUE`

- **`POST /api/demandes-correction/{id}/soumettre`** (permission `correction.submit`)  
  **ou** `PATCH /api/demandes-correction/{id}/statut?statut=RECUE` lorsque la demande est encore en **`BROUILLON`** (même rôle déposant : entreprise ou AC).

À la soumission : le backend vérifie que **modèle fiscal + DQE** sont présents, puis passe en **`RECUE`** et envoie les notifications habituelles.

### Édition du contenu

- **`PUT /api/demandes-correction/{id}`** — corps : `UpdateDemandeCorrectionRequest` (même structure que la création : AC, entreprise, convention, `marcheId`, `modeleFiscal`, `dqe`).

**Conditions (back)** :

- Statut parmi **`BROUILLON`**, **`RECUE`**, **`INCOMPLETE`** ;
- Aucun visa parallèle engagé : `validationDgd`, `validationDgtcp`, `validationDgi`, `validationDgb` tous à `false` ;
- Rôle : **entreprise dépositaire** ou **autorité contractante** liée à la demande.

**Erreur métier** : HTTP **409**, `code`: **`DEMANDE_NON_EDITABLE`**.

---

## 2. Utilisation de crédit — `UtilisationCredit`

### Statut `BROUILLON`

- Création : `POST /api/utilisations-credit` avec **`"brouillon": true`** dans `CreateUtilisationCreditRequest`.
  - Le certificat **n’a pas besoin** d’être `OUVERT` pour un **brouillon** (contrôle à la soumission).
  - **Pas de notification** DGD/DGTCP tant que `BROUILLON`.

### Soumission

- **`POST /api/utilisations-credit/{id}/soumettre`**  
  **ou** `PATCH .../statut?statut=DEMANDEE` si le statut courant est **`BROUILLON`** (délègue à la même logique).

Contrôles : certificat **`OUVERT`**, transition **`BROUILLON` → `DEMANDEE`**, puis notification comme une création directe.

### Édition

- **`PUT /api/utilisations-credit/{id}`** — même DTO que la création (`CreateUtilisationCreditRequest`).
- Le **type** (`DOUANIER` / `TVA_INTERIEURE`) ne peut pas changer.

**Conditions** : statut **`BROUILLON`** ou **`DEMANDEE`** ; même entreprise / sous-traitant que pour la création.  
Si ce n’est plus un brouillon, le certificat doit être **`OUVERT`**.

**Erreur** : **`DEMANDE_NON_EDITABLE`** si statut hors périmètre.

---

## 3. Mise en place — `CertificatCredit`

### Statut `BROUILLON`

- Création : `POST /api/certificats-credit` avec **`"brouillon": true`**.
  - `demandeCorrectionId` / `lettreCorrectionId` **optionnels** en brouillon (à compléter avant soumission).
  - **Pas** d’exécution des contrôles « mise en place » complets à la création.
  - Pas de rattachement GED tant que non soumis.

### Soumission

- **`POST /api/certificats-credit/{id}/soumettre`**  
  **ou** `PATCH .../statut?statut=EN_CONTROLE` depuis **`BROUILLON`** (même logique).

À la soumission : exécution de **`assertMiseEnPlaceTrigger`** (demande adoptée/notifiée, marché, etc.), statut **`EN_CONTROLE`**, rattachement GED si demande liée, notification.

### Édition brouillon

- **`PUT /api/certificats-credit/{id}`** — `CreateCertificatCreditRequest` (permission `mise_en_place.submit`).

Uniquement si le statut est **`BROUILLON`**. Sinon : **`DEMANDE_NON_EDITABLE`**.

---

### Suppression définitive d’un brouillon (`DELETE`)

Réservé au statut **`BROUILLON`** (réponse **204** si succès). Pour une demande / utilisation / certificat **déjà soumis**, le flux reste l’**annulation** (statuts **`ANNULEE`** / **`ANNULE`**, etc.), pas ce `DELETE`.

| Méthode | Chemin | Permission |
|--------|--------|------------|
| `DELETE` | `/api/demandes-correction/{id}` | `correction.submit` |
| `DELETE` | `/api/utilisations-credit/{id}` | `utilisation.douane.submit` ou `utilisation.interieur.submit` |
| `DELETE` | `/api/certificats-credit/{id}` | `mise_en_place.submit` |

---

## 4. Codes d’erreur utiles

| Code API (`ErrorResponse.code`) | Cas |
|----------------------------------|-----|
| `DEMANDE_NON_EDITABLE` | Édition interdite (workflow / statut) |
| `BUSINESS_RULE_VIOLATION` | Soumission sans prérequis (ex. modèle/DQE manquants, certificat non ouvert) |
| `MARCHE_DEMANDE_ACTIVE` | Marché déjà lié à une autre demande active |

Le corps d’erreur peut inclure **`details`** (objet) selon les cas existants.

---

## 5. Récap des nouveaux endpoints

| Méthode | Chemin | Rôle principal |
|--------|--------|----------------|
| `PUT` | `/api/demandes-correction/{id}` | Édition correction |
| `POST` | `/api/demandes-correction/{id}/soumettre` | Brouillon → `RECUE` |
| `DELETE` | `/api/demandes-correction/{id}` | Suppression brouillon uniquement |
| `PUT` | `/api/utilisations-credit/{id}` | Édition utilisation |
| `POST` | `/api/utilisations-credit/{id}/soumettre` | Brouillon → `DEMANDEE` |
| `DELETE` | `/api/utilisations-credit/{id}` | Suppression brouillon uniquement |
| `PUT` | `/api/certificats-credit/{id}` | Édition certificat brouillon |
| `POST` | `/api/certificats-credit/{id}/soumettre` | Brouillon → `EN_CONTROLE` |
| `DELETE` | `/api/certificats-credit/{id}` | Suppression brouillon uniquement |

Champs **`brouillon`** dans les JSON de création : **`CreateDemandeCorrectionRequest`**, **`CreateUtilisationCreditRequest`**, **`CreateCertificatCreditRequest`**.

**Voir aussi** : récapitulatif fiscal du certificat, soldes et transfert cordon → intérieur — `docs/FRONT_CERTIFICAT_RECAP_TRANSFERT.md`.
