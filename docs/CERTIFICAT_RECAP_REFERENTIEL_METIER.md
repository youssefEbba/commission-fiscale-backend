# Certificat de crédit — référentiel métier (récapitulatif réel) et correspondance technique

Ce document aligne la **structure fonctionnelle** du certificat (telle qu’issue de l’offre fiscale validée et du tableau d’attribution) sur le **modèle actuel** du backend SGCI, et indique les **écarts** à combler pour une couverture complète de la piste d’audit et de la réconciliation finale.

---

## 1. Lecture du récapitulatif : deux blocs à la création

Le certificat repose sur **deux blocs** construits à partir de l’**offre fiscale validée** (liée à la demande de correction / marché).

### Bloc « cordon douanier » (crédit extérieur)

| Ligne | Libellé (document type) | Exemple | Rôle |
|-------|-------------------------|---------|------|
| **(a)** | Valeur en douane des fournitures à importer | 9 746 681 | Base douanière des fournitures |
| **(b)** | Taxes douanières (droits, hors ventilation TVA import) | 2 241 737 | Volet fiscal douane « hors TVA import » |
| **(c)** | Montant total **(a + b)** | 11 988 417 | Agrégat informatif ; **non stocké** en colonne dédiée côté API (dérivable : a + b) |
| **(d)** | TVA déductible sur cordon douanier (TVA à l’import) | 1 918 147 | **Lien** entre les deux blocs : utilisée dans **e** et **h**, et pour le **transfert**. Côté système : **(d) accord** est figé (`tvaImportationDouaneAccordee`) ; le **restant (d)** (`tvaImportationDouane`) baisse à chaque **liquidation douanière** d’autant de TVA d’import réellement imputée. |
| **(e)** | Total taxes sur cordon **(b + d)** = **crédit extérieur** | 4 159 883 | Enveloppe **plafond** du volet douanier (ne peut que baisser à l’usage) |

### Bloc « TVA intérieure » (crédit intérieur)

| Ligne | Libellé | Exemple | Rôle |
|-------|---------|---------|------|
| **(f)** | Montant de l’offre / marché **HT** | 12 502 300 | Base travaux |
| **(g)** | TVA collectée (ex. 16 % × **f**) | 2 000 368 | TVA théorique sur l’ensemble du marché |
| **(h)** | TVA nette à payer **(g − d)** = **crédit intérieur** | 82 221 | Enveloppe intérieure **théorique** fixée à partir de l’offre |

### Total accordé

| Agrégat | Formule | Exemple |
|---------|---------|---------|
| **Total crédit d’impôt** | **(e) + (h)** | **4 242 105** |

À la **clôture**, le système doit pouvoir **réconcilier** : consommations (droits, TVA import, TVA intérieure, reports, transferts, etc.) + soldes résiduels ↔ ce **total initial** accordé (voir § 6).

---

## 2. Interprétation métier (comportement dans le temps)

- **Crédit intérieur initial (h)** : calculé dès la création à partir de **(g)** et **(d)** ; c’est la TVA nette théorique de l’ensemble du marché dans le référentiel « accord ».
- **(d) — TVA déductible sur cordon** : connue à la création ; lors du **transfert** validé, le **restant (d)** (`tvaImportationDouane`) est **versé** sur le **solde TVA intérieure** (`soldeTVA`) sur le **même certificat** ; le stock **FIFO** n’est pas modifié par le transfert (voir `TransfertCreditService`, `docs/FRONT_CERTIFICAT_RECAP_TRANSFERT.md`).
- **(e) — crédit extérieur** : couvre **droits + TVA import** au sens du plafond **b + d** ; le **solde cordon** courant diminue avec les **liquidations douanières** réelles.
- **Stock FIFO TVA déductible** : la consommation progressive de **(d)** par importation est modélisée par des **lignes de stock** `TvaDeductibleStock` (FIFO par date), consultables via l’API du certificat (voir contrôleur certificat / utilisations).

---

## 3. Correspondance avec les attributs actuels (backend)

### Identification et cadrage

| Besoin métier | SGCI aujourd’hui |
|---------------|------------------|
| Référence certificat | `CertificatCredit.numero` |
| Entreprise bénéficiaire | `entreprise` → `entrepriseId` / raison sociale en DTO |
| Dates (création, validité) | `dateEmission`, `dateValidite` |
| Statut global | `statut` (`StatutCertificat`) |
| Projet, lot, bailleur, intitulés | **Partiel** : portés par **demande de correction**, **marché**, **convention**, **bailleur** en amont — **pas** tous aplatis sur le DTO certificat ; à compléter côté API d’agrégation ou champs dérivés si besoin écran unique |

### Volet cordon — valeurs « figées » (référence de l’accord)

Stockées sur l’entité certificat (saisie création / DGTCP) :

| Ligne | Champ entité / DTO |
|-------|-------------------|
| (a) | `valeurDouaneFournitures` |
| (b) | `droitsEtTaxesDouaneHorsTva` |
| (d) | `tvaImportationDouane` |
| (e) | `montantCordon` (doit être cohérent avec **b + d** si contrôle récap activé) |

**(c)** = **(a) + (b)** : **pas de colonne** ; calcul côté UI ou extension future.

### Volet cordon — valeurs « vivantes »

| Besoin métier | SGCI aujourd’hui |
|---------------|------------------|
| Solde crédit extérieur courant | `soldeCordon` |
| Stock TVA déductible (FIFO) | Entité `TvaDeductibleStock` + API de lecture stock |
| Cumuls droits / TVA import déjà couverts | **Partiel** : reconstituables via **utilisations** / écritures liées — **pas** de champs agrégés dédiés sur le certificat |
| Statut volet douanier (ouvert / transféré / clôturé) | **Non** : seul le **statut global** certificat + présence **transfert** / **clôture** |

### Volet TVA intérieure — valeurs figées

| Ligne | Champ |
|-------|--------|
| (f) | `montantMarcheHt` |
| (g) | `tvaCollecteeTravaux` |
| (h) | `montantTVAInterieure` (cohérent avec **g − d** si contrôle activé) |

### Volet TVA intérieure — valeurs vivantes

| Besoin | SGCI |
|--------|------|
| Solde TVA intérieure courant | `soldeTVA` |
| Montant reçu par transfert (audit séparé de h) | **Partiel** : le transfert porte un **montant** sur `TransfertCredit`, mais **pas** d’historique séparé « cumul transferts » sur le certificat |
| Cumuls reports, paiements cash DGI | **Hors périmètre** ou **à traçabilité** via processus métier — **non** modélisés en champs simples sur `CertificatCredit` |

### Champs dérivés exposés en lecture (`CertificatCreditDto`)

Pour affichage / contrôle sans dupliquer la logique front :

- `creditExterieurRecap` ≈ **b + d**
- `creditInterieurNetRecap` ≈ **g − d**
- `totalCreditImpotRecap` ≈ **e + h** (si les deux blocs dérivables)

Voir aussi `docs/FRONT_CERTIFICAT_RECAP_TRANSFERT.md` et `docs/MISE_EN_PLACE_MONTANTS_DGTCP.md`.

### Transfert

| Besoin | SGCI (`TransfertCredit`) |
|--------|---------------------------|
| Date demande | `dateDemande` |
| Montant exécuté | `montant` (= restant **(d)** à la validation) |
| Clôture douane (déclaratif) | `operationsDouaneCloturees` |
| Statut du dossier transfert | `statut` (`StatutTransfert`) |
| Après exécution | `tvaImportationDouane` → **0** ; `soldeTVA` += ce montant ; utilisations **douanières** ouvertes → `CLOTUREE` |
| Solde cordon au moment T, acteur validateur détaillé, observations | **Limité** : validation côté service, **pas** tous les champs d’audit listés |

### Clôture globale

| Besoin | SGCI (`ClotureCredit`) |
|--------|-------------------------|
| Dates / motif / type | `dateProposition`, `dateCloture`, `motif`, `typeOperation` |
| Solde résiduel | `soldeRestant` (champ unique — pas encore dédoublonné cordon / intérieur dans tous les cas) |
| Réconciliation totale **4 242 105** vs consommations | **À formaliser** : règles de contrôle et agrégats **non** entièrement couverts par des champs « total accordé » vs « total consommé » sur une seule vue métier |

---

## 4. Synthèse : ce que le total **4 242 105** impose à la clôture

À terme, la clôture doit permettre de vérifier que :

**Somme des consommations autorisées** (droits et TVA import reconnus, TVA intérieure utilisée, ajustements, transferts, résidus) **+ soldes résiduels** est **cohérente** avec le **total d’impôt accordé initialement** **(e) + (h)** (ici **4 242 105**).

Le backend expose aujourd’hui les **montants de référence** et les **soldes** ; la **preuve de réconciliation** complète peut nécessiter des **vues agrégées**, des **rapports** ou des **extensions de modèle** selon le niveau d’audit exigé.

---

## 5. Pistes d’évolution (hors périmètre immédiat)

- Colonnes ou snapshot **(c)** et totaux intermédiaires persistés si besoin légal d’immuable bit-à-bit.
- Champs **cumulatifs** sur certificat ou tables d’**écriture** (droits couverts, TVA couverte, cash DGI).
- **Statut de volet** douanier distinct du statut global.
- **Traçabilité transfert** : soldes avant/après, validateur, commentaires (au-delà de `TransfertCredit` actuel).
- **Clôture** : résidu cordon vs résidu intérieur, total consommé vs `totalCreditImpotRecap`.

---

## 6. Fichiers utiles (code)

- Entité : `CertificatCredit`, `TransfertCredit`, `ClotureCredit`, `TvaDeductibleStock`
- DTO : `CertificatCreditDto`, `UpdateCertificatCreditMontantsRequest`
- Services : `CertificatCreditService` (cohérence récap), `TransfertCreditService`, `UtilisationCreditService` (FIFO)
