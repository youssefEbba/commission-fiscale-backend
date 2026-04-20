# Front — certificat de crédit (montants, récapitulatif, transfert)

Guide court pour brancher l’UI sur l’API. Base URL typique : `/api`.

---

## 1. Lecture d’un certificat

- **`GET /api/certificats-credit/{id}`** — détail (permissions de consultation existantes).

Champs utiles côté écran :

| Champ JSON | Rôle métier |
|------------|-------------|
| `montantCordon` | Enveloppe **crédit cordon / extérieur** (récap : **e = b + d**). |
| `montantTVAInterieure` | Enveloppe **TVA intérieure** (récap : **h = g − d**). |
| `soldeCordon` | Reste utilisable côté **douane / cordon**. |
| `soldeTVA` | Reste utilisable côté **TVA intérieure** (décomptes, achats locaux). |

Récapitulatif fiscal **optionnel** (saisi en création / brouillon ou par DGTCP avec les montants) :

| Champ | Ligne tableau type |
|-------|---------------------|
| `valeurDouaneFournitures` | (a) |
| `droitsEtTaxesDouaneHorsTva` | (b) |
| `tvaImportationDouaneAccordee` | (d) **accord initial** (figé à la saisie) |
| `tvaImportationDouane` | (d) **restant** — diminue à chaque liquidation douanière (TVA import imputée) |
| `montantMarcheHt` | (f) |
| `tvaCollecteeTravaux` | (g) |

Le back calcule en **lecture seule** (pour affichage / contrôle), avec **(d) = accord** pour les formules :

| Champ | Formule |
|-------|---------|
| `creditExterieurRecap` | `b + d` (d = accord) si b et d présents |
| `creditInterieurNetRecap` | `g − d` (d = accord) si g et d présents |
| `totalCreditImpotRecap` | somme des deux si les deux sont calculables |

---

## 2. Saisie des montants et du récap (DGTCP)

- **`PATCH /api/certificats-credit/{id}/montants`** — corps `UpdateCertificatCreditMontantsRequest` :
  - **Obligatoires** : `montantCordon`, `montantTVAInterieure`.
  - **Optionnels** : les cinq champs récap ci-dessus (mêmes noms qu’à la lecture).

Si le récap **et** les montants agrégés sont fournis, le back vérifie (tolérance ~1 MRU) :

- `montantCordon` ≈ `droitsEtTaxesDouaneHorsTva` + `tvaImportationDouane`
- `montantTVAInterieure` ≈ `tvaCollecteeTravaux` − `tvaImportationDouane`

En cas d’écart : HTTP **400**, `code` souvent **`BUSINESS_RULE_VIOLATION`**.

---

## 3. Création / brouillon (récap optionnel)

- **`POST /api/certificats-credit`** — `CreateCertificatCreditRequest` : mêmes noms pour le récap (`valeurDouaneFournitures`, etc.) en plus des montants et de `brouillon`.

Pour les brouillons, édition, soumission et **suppression définitive** : voir **`docs/DEMANDES_BROUILLON_EDITION_FRONT.md`**.

---

## 4. Transfert cordon → intérieur

Après clôture des opérations douanières, l’entreprise peut demander à **déplacer** une partie du **solde cordon** vers le **solde TVA intérieure** (même certificat).

| Action | Méthode | Corps / remarque |
|--------|---------|-------------------|
| Créer la demande | `POST /api/transferts-credit` | `{ "certificatCreditId", "montant", "operationsDouaneCloturees": true }` — permission `transfert.submit` |
| Valider (exécution du transfert) | `POST /api/transferts-credit/{id}/valider` | DGTCP / Président — débite `soldeCordon`, crédite `soldeTVA` du même montant |
| Rejeter | `POST /api/transferts-credit/{id}/rejeter` | |
| Voir la demande liée au certificat | `GET /api/transferts-credit/by-certificat/{certificatCreditId}` | 404 si aucune demande |

Prérequis métier côté back : certificat **OUVERT**, documents requis du processus transfert, `operationsDouaneCloturees` à **true**, montant ≤ `soldeCordon`.

**Après exécution d’un transfert** (`StatutTransfert.TRANSFERE` suite à validation DGTCP / Président) : **plus aucune demande d’utilisation douanière** (y compris brouillon) ne peut être créée ou soumise sur ce certificat — réponse **409** / `BUSINESS_RULE_VIOLATION`. Les utilisations **TVA intérieure** restent possibles selon les règles habituelles.

---

## 5. Erreurs utiles

| Code API (`ErrorResponse.code`) | Contexte |
|----------------------------------|----------|
| `BUSINESS_RULE_VIOLATION` | Récap incohérent avec les montants, transfert impossible, etc. |
| `RESOURCE_NOT_FOUND` | Ressource absente |
| `ACCESS_DENIED` / `ROLE_FORBIDDEN` | Permissions |

---

## 6. Fichier complémentaire

- Brouillons (correction, utilisation, certificat), `DELETE`, annulation des demandes soumises : **`docs/DEMANDES_BROUILLON_EDITION_FRONT.md`**.
