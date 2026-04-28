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
| `soldeCordon` | Reste utilisable côté **douane / cordon** (imputations liquidations). |
| `soldeTVA` | Reste utilisable côté **TVA intérieure** (décomptes, apurements, etc.). |

Récapitulatif fiscal **optionnel** (saisi en création / brouillon ou par DGTCP avec les montants) :

| Champ | Ligne tableau type |
|-------|---------------------|
| `valeurDouaneFournitures` | (a) |
| `droitsEtTaxesDouaneHorsTva` | (b) |
| `tvaImportationDouaneAccordee` | (d) **accord initial** (figé à la saisie) |
| `tvaImportationDouane` | (d) **restant** — baisse à chaque liquidation ; **0** après exécution d’un **transfert** validé |
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

## 4. Transfert : restant TVA douane (d) → crédit TVA intérieur

Après **clôture des opérations douanières** (déclarative côté demande : `operationsDouaneCloturees: true`), l’**entreprise** initie le transfert. À la **validation** DGTCP / Président, le back :

1. **Ajoute** la totalité de **`tvaImportationDouane`** (restant (d)) à **`soldeTVA`**
2. Remet **`tvaImportationDouane`** à **0** (définitif pour le certificat après transfert exécuté)
3. **Ne modifie pas** le **stock FIFO** (`TvaDeductibleStock`) — il continue de servir l’**apurement** TVA intérieur comme avant
4. Passe toutes les **utilisations** de type **douanier** encore « ouvertes » au statut **`CLOTUREE`** (sauf déjà `LIQUIDEE` / `REJETEE`)
5. Enregistre le **montant exécuté** sur l’entité transfert = restant (d) au moment de la validation

L’**ancien** mécanisme « débiter `soldeCordon` d’un montant saisi et créditer `soldeTVA` du même montant » **n’est plus** utilisé : le transfert vise le **(d) restant**, pas le `soldeCordon`.

| Action | Méthode | Corps / remarque |
|--------|---------|------------------|
| Créer la demande | `POST /api/transferts-credit` | `certificatCreditId` (obligatoire), `operationsDouaneCloturees: true` recommandé / exigé à la validation. **`montant` optionnel** : indication UI ; à la validation le montant réel = **(d) restant** sur le certificat. |
| Valider (exécution) | `POST /api/transferts-credit/{id}/valider` | DGTCP / Président — effets ci-dessus sur le certificat + clôture utilisations douanières. |
| Rejeter | `POST /api/transferts-credit/{id}/rejeter` | |
| Voir la demande liée au certificat | `GET /api/transferts-credit/by-certificat/{certificatCreditId}` | 404 si aucune demande |

Prérequis côté back : certificat **OUVERT**, documents requis du processus **transfert**, `operationsDouaneCloturees` = **true**.

**Après exécution** (`StatutTransfert.TRANSFERE`) : **plus** de **nouvelles** demandes d’utilisation **douanière** (y compris brouillon) sur ce certificat — **409** / `BUSINESS_RULE_VIOLATION` si tentative. Les utilisations **TVA intérieure** restent possibles. Les utilisations **douanières** déjà clôturées en **`CLOTUREE`** ne sont plus modifiables selon le workflow habituel (statut terminal).

**Front — affichage utilisation douanier** : prévoir le libellé / style pour le statut **`CLOTUREE`** (ex. « Clôturée (transfert) »), distinct de `REJETEE` et `LIQUIDEE`.

---

## 5. Erreurs utiles

| Code API (`ErrorResponse.code`) | Contexte |
|----------------------------------|----------|
| `BUSINESS_RULE_VIOLATION` | Récap incohérent, transfert impossible (ex. opérations douane non clôturées), etc. |
| `RESOURCE_NOT_FOUND` | Ressource absente |
| `ACCESS_DENIED` / `ROLE_FORBIDDEN` | Permissions |

---

## 6. Fichier complémentaire

- Brouillons (correction, utilisation, certificat), `DELETE`, annulation des demandes soumises : **`docs/DEMANDES_BROUILLON_EDITION_FRONT.md`**.
