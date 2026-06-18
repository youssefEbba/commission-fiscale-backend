# Documentation Frontend ? Demande d'Utilisation de Crédit d'Impôt Extérieur (Douanière)

> **Destinataire** : Équipe Frontend  
> **Mise à jour** : Juin 2026

**Validation pré-soumission (certificat éligible, soldes, transfert)** : voir [UTILISATION_CREDIT_VALIDATION_FRONT.md](./UTILISATION_CREDIT_VALIDATION_FRONT.md).

---

## 1. Workflow complet

```
ENTREPRISE / COMMISSION RELAIS        DGD                          DGTCP
??????????????????????????????????????????????????????????????????????????
1. Crée la demande + saisit les                                      
   lignes du bulletin                                                
   [statut ? DEMANDEE]                                               
                                                                     
2. Dépose les documents requis                                       
   (facultatif avant visa DGD)                                       
                                                                     
                              3a. (Optionnel) Prend en charge       
                                  [statut ? EN_VERIFICATION]        
                                                                     
                              3b. Annote chaque ligne du           
                                  bulletin : AU_CI ou A_PAYER       
                                  POST /{id}/visa-dgd               
                                  [statut ? VISE]                   
                                                                     
                                                    4. Exécute la liquidation
                                                       POST /{id}/liquidation-douane
                                                       ? débite soldeCordon de
                                                         (totalPrisEnCharge - TVA)
                                                       ? décrémente quota TVA importation
                                                       ? alimente stock TVA d?ductible TVA
                                                       [statut ? LIQUIDEE]
```

### Règle financière fondamentale

| Opération | Formule |
|---|---|
| Débit solde cordon | `totalPrisEnCharge ? TVA_AU_CI` |
| Débit quota TVA importation douane | `TVA_AU_CI` (lignes codées "TVA" avec affectation AU_CI) |
| Alimentation stock TVA d?ductible TVA déductible | `TVA_AU_CI` |
| Montant à payer comptant | `totalAPayer` (affiché, pas de débit CI) |

---

## 2. Statuts

| Statut | Acteur | Description |
|---|---|---|
| `DEMANDEE` | Entreprise | Demande soumise, en attente DGD |
| `BROUILLON` | Entreprise | Brouillon non soumis (optionnel) |
| `INCOMPLETE` | Services | Pièces complémentaires demandées |
| `A_RECONTROLER` | Système | Toutes les réponses au rejet temporaire fournies |
| `EN_VERIFICATION` | DGD | DGD a pris en charge (optionnel) |
| `VISE` | DGD | DGD a annoté les lignes et visé |
| `LIQUIDEE` | DGTCP | Liquidation financière exécutée |
| `REJETEE` | DGD / DGTCP | Demande refusée définitivement |

### Transitions autorisées

```
DEMANDEE ??????????????????????????????? EN_VERIFICATION
DEMANDEE ??????????????????????????????? VISE  (DGD peut passer directement)
DEMANDEE / EN_VERIFICATION / A_RECONTROLER ??? INCOMPLETE
EN_VERIFICATION ???????????????????????? VISE
VISE ???????????????????????????????????? LIQUIDEE  (DGTCP uniquement)
VISE / EN_VERIFICATION ????????????????? REJETEE
```

---

## 3. Types et enums

### `TypeLigneTaxe`
| Valeur | Section du bulletin |
|---|---|
| `GLOBALE` | Taxes en en-tête (ex. Taxe sur Tonnage, Redevance Informatique) |
| `ARTICLE` | Taxes par ligne de marchandise (ex. DD, TVA, RS, PSC, IMF) |

### `AffectationTaxe` (décision DGD)
| Valeur | Signification |
|---|---|
| `AU_CI` | Pris en charge par le crédit d'impôt extérieur |
| `A_PAYER` | À payer comptant par l'entreprise |

---

## 4. Endpoints

### Base URL : `/api/utilisations-credit`

---

### 4.1 Créer une demande (ENTREPRISE / COMMISSION RELAIS)

**POST** `/api/utilisations-credit`  
**Permission** : `utilisation.douane.submit`

```json
{
  "type": "DOUANIER",
  "certificatCreditId": 12,
  "entrepriseId": 5,
  "numeroDeclaration": "2023 C 24768",
  "numeroBulletin": "L 26324",
  "dateDeclaration": "2023-10-10T00:00:00Z",
  "enregistreeSYDONIA": true,
  "brouillon": false,
  "lignes": [
    {
      "codeTaxe": "TST",
      "denominationTaxe": "Taxe sur Tonnage Importé",
      "typeLigne": "GLOBALE",
      "valeurTaxe": 3919.90
    },
    {
      "codeTaxe": "RI",
      "denominationTaxe": "Redevance Informatique",
      "typeLigne": "GLOBALE",
      "valeurTaxe": 300.00
    },
    {
      "codeTaxe": "DD",
      "denominationTaxe": "Droit de Douane",
      "typeLigne": "ARTICLE",
      "valeurTaxe": 93873.98
    },
    {
      "codeTaxe": "PSC",
      "denominationTaxe": "Promotion Sports et Culture",
      "typeLigne": "ARTICLE",
      "valeurTaxe": 2693.69
    },
    {
      "codeTaxe": "PC",
      "denominationTaxe": "Prélèvement Communautaire",
      "typeLigne": "ARTICLE",
      "valeurTaxe": 1346.93
    },
    {
      "codeTaxe": "RS",
      "denominationTaxe": "Redevance Statistique",
      "typeLigne": "ARTICLE",
      "valeurTaxe": 2693.69
    },
    {
      "codeTaxe": "IMF",
      "denominationTaxe": "Impôt Minimum Forfaitaire",
      "typeLigne": "ARTICLE",
      "valeurTaxe": 6599.54
    },
    {
      "codeTaxe": "TVA",
      "denominationTaxe": "Taxe sur Valeur Ajoutée",
      "typeLigne": "ARTICLE",
      "valeurTaxe": 52796.32
    }
  ]
}
```

**Réponse** : `201 Created` ? `UtilisationCreditDto` (voir §6)

---

### 4.2 Modifier une demande (brouillon / DEMANDEE)

**PUT** `/api/utilisations-credit/{id}`  
**Permission** : `utilisation.douane.submit`

Même corps que la création. Remplace toutes les lignes.

---

### 4.3 Soumettre un brouillon

**POST** `/api/utilisations-credit/{id}/soumettre`  
**Permission** : `utilisation.douane.submit`

---

### 4.4 Changer le statut (DGD ? prise en charge optionnelle)

**PATCH** `/api/utilisations-credit/{id}/statut?statut=EN_VERIFICATION`  
**Permission** : `utilisation.douane.dgd.verify`

> Étape facultative. Le DGD peut passer directement à `visa-dgd` sans EN_VERIFICATION.

---

### 4.5 ? Visa DGD ? annotation des lignes + visa

**POST** `/api/utilisations-credit/{id}/visa-dgd`  
**Permission** : `utilisation.douane.dgd.quittance.visa`  
**Statut résultant** : `VISE`

Le DGD soumet ses décisions ligne par ligne. **Toutes les lignes doivent être couvertes.**

```json
{
  "decisions": [
    { "ligneId": 101, "affectation": "AU_CI" },
    { "ligneId": 102, "affectation": "AU_CI" },
    { "ligneId": 103, "affectation": "AU_CI" },
    { "ligneId": 104, "affectation": "AU_CI" },
    { "ligneId": 105, "affectation": "AU_CI" },
    { "ligneId": 106, "affectation": "AU_CI" },
    { "ligneId": 107, "affectation": "A_PAYER" },
    { "ligneId": 108, "affectation": "A_PAYER" }
  ]
}
```

> **Exemple du bulletin :**  
> - DD + RS + PSC + PC + TVA ? `AU_CI` (totalPrisEnCharge = 153 404,61)  
> - IMF + TST + RI ? `A_PAYER` (totalAPayer = 10 819,44)

**Réponse** : `200 OK` ? `UtilisationCreditDto` avec `statut: "VISE"`, `totalPrisEnCharge` et `totalAPayer` renseignés.

> **Aucune opération financière à cette étape.** Les valeurs sont juste calculées et sauvegardées.

---

### 4.6 ? Liquidation DGTCP ? exécution financière

**POST** `/api/utilisations-credit/{id}/liquidation-douane`  
**Permission** : `utilisation.douane.dgtcp.impute` ou `utilisation.douane.dgtcp.solde.update`  
**Prérequis** : statut `VISE` (visa DGD obligatoire)  
**Statut résultant** : `LIQUIDEE`  
**Corps** : aucun (pas de body)

Le DGTCP exécute automatiquement :
1. Débite `soldeCordon` du certificat de `totalPrisEnCharge - TVA_AU_CI`
2. Décrémente `tvaImportationDouane` du certificat de `TVA_AU_CI`
3. Crée une entrée dans le stock TVA d?ductible de TVA déductible de `TVA_AU_CI`

**Réponse** : `200 OK` ? `UtilisationCreditDto` avec `statut: "LIQUIDEE"`.

---

### 4.7 Obtenir les lignes du bulletin

**GET** `/api/utilisations-credit/{id}/lignes-bulletin`  
**Permission** : `utilisation.douane.dgd.queue.view` (ou toute autre permission de lecture)

```json
[
  {
    "id": 103,
    "codeTaxe": "DD",
    "denominationTaxe": "Droit de Douane",
    "typeLigne": "ARTICLE",
    "valeurTaxe": 93873.98,
    "affectation": "AU_CI"
  },
  {
    "id": 107,
    "codeTaxe": "IMF",
    "denominationTaxe": "Impôt Minimum Forfaitaire",
    "typeLigne": "ARTICLE",
    "valeurTaxe": 6599.54,
    "affectation": "A_PAYER"
  }
]
```

> `affectation` est `null` avant le visa DGD.

---

### 4.8 Consulter une demande

**GET** `/api/utilisations-credit/{id}`  
Les lignes sont incluses dans le champ `lignes` du DTO.

---

## 5. Permissions par rôle

| Rôle | Permissions |
|---|---|
| **ENTREPRISE / COMMISSION RELAIS** | `utilisation.douane.submit` (créer, modifier, soumettre) |
| **DGD** | `utilisation.douane.dgd.verify` (EN_VERIFICATION), `utilisation.douane.dgd.quittance.visa` (visa-dgd), `utilisation.douane.dgd.reject` (rejeter), `utilisation.douane.dgd.queue.view` (consulter) |
| **DGTCP** | `utilisation.douane.dgtcp.impute` (liquidation-douane), `utilisation.douane.dgtcp.queue.view` (consulter) |

---

## 6. DTO de retour ? `UtilisationCreditDto`

```typescript
interface UtilisationCreditDto {
  id: number;
  type: "DOUANIER" | "TVA_INTERIEURE";
  dateDemande: string;           // ISO-8601
  montant: number | null;        // Montant débité du solde cordon (après liquidation)
  statut: StatutUtilisation;
  dateLiquidation: string | null;
  certificatCreditId: number;
  entrepriseId: number;
  certificatTitulaireEntrepriseId: number | null;
  certificatTitulaireRaisonSociale: string | null;
  demandeurEstSousTraitant: boolean;

  // ?? Champs DOUANIER ?????????????????????????????????????????????????
  numeroDeclaration: string | null;
  numeroBulletin: string | null;
  dateDeclaration: string | null;
  enregistreeSYDONIA: boolean | null;
  soldeCordonAvant: number | null;   // Renseigné après liquidation DGTCP
  soldeCordonApres: number | null;   // Renseigné après liquidation DGTCP

  /** Lignes du bulletin ? présentes dès la création */
  lignes: LigneBulletinDto[];

  /** Somme des lignes AU_CI ? renseigné après visa DGD */
  totalPrisEnCharge: number | null;

  /** Somme des lignes A_PAYER ? renseigné après visa DGD */
  totalAPayer: number | null;

  /** Part TVA des lignes AU_CI (pour info comptable) */
  montantTVADouane: number | null;

  /** Part hors-TVA des lignes AU_CI (= montant débité solde cordon) */
  montantDroits: number | null;

  // ?? Champs TVA_INTERIEURE ????????????????????????????????????????????
  typeAchat: "ACHAT_LOCAL" | "DECOMPTE" | null;
  numeroFacture: string | null;
  dateFacture: string | null;
  montantTVAInterieure: number | null;
  numeroDecompte: string | null;
  tvaDeductibleUtilisee: number | null;
  tvaNette: number | null;
  creditInterieurUtilise: number | null;
  paiementEntreprise: number | null;
  reportANouveau: number | null;
  soldeTVAAvant: number | null;
  soldeTVAApres: number | null;
}

interface LigneBulletinDto {
  id: number;
  codeTaxe: string;           // "DD", "TVA", "RS", "PSC", "IMF", "PC", "TST", "RI"...
  denominationTaxe: string;
  typeLigne: "GLOBALE" | "ARTICLE";
  valeurTaxe: number;         // Valeur saisie par l'entreprise (MRU)
  affectation: "AU_CI" | "A_PAYER" | null;  // null avant le visa DGD
}
```

---

## 7. Guide UI ? composants à implémenter

### 7.1 Formulaire de création (ENTREPRISE / COMMISSION RELAIS)

**Partie A ? En-tête**

| Champ | Type | Obligatoire |
|---|---|---|
| Certificat de crédit | Sélecteur | Oui |
| Numéro de déclaration | Texte | Non |
| Numéro de bulletin | Texte | Non |
| Date de déclaration | Date | Non |
| Enregistrée SYDONIA | Checkbox | Non |

**Partie B ? Tableau des lignes (dynamique)**

| Colonne | Type | Obligatoire |
|---|---|---|
| Code taxe | Texte court (?20 car.) | Oui |
| Dénomination | Texte (?120 car.) | Oui |
| Type | Radio : `GLOBALE` / `ARTICLE` | Oui |
| Valeur (MRU) | Numérique ? 0 | Oui |
| _(Supprimer)_ | Bouton | ? |

Bouton **"+ Ajouter une ligne"**  
Afficher le **Total** en temps réel (somme des valeurTaxe).

**Codes courants** (suggestion auto-complétion) :

| Code | Libellé | Type habituel |
|---|---|---|
| `DD` | Droit de Douane | ARTICLE |
| `TVA` | Taxe sur Valeur Ajoutée | ARTICLE |
| `RS` | Redevance Statistique | ARTICLE |
| `PSC` | Promotion Sports et Culture | ARTICLE |
| `PC` | Prélèvement Communautaire | ARTICLE |
| `IMF` | Impôt Minimum Forfaitaire | ARTICLE |
| `TST` | Taxe sur Tonnage Importé | GLOBALE |
| `RI` | Redevance Informatique | GLOBALE |

---

### 7.2 Vue DGD ? Annotation du bulletin (POST visa-dgd)

Afficher le tableau du bulletin avec une colonne de décision pour chaque ligne :

```
???????????????????????????????????????????????????????????????????????????
?  BULLETIN DE LIQUIDATION ? Visa DGD                                     ?
????????????????????????????????????????????????????????????????????????  ?
? Taxe                   ? Valeur (MRU) ? Décision DGD                    ?
???????????????????????????????????????????????????????????????????????? ??
? [Section GLOBALES]                                                       ?
? Taxe sur Tonnage       ?   3 919,90   ?  ? AU_CI    ? A_PAYER           ?
? Redevance Informatique ?     300,00   ?  ? AU_CI    ? A_PAYER           ?
????????????????????????????????????????????????????????????????????????? ?
? [Section ARTICLE]                                                        ?
? Droit de Douane        ?  93 873,98   ?  ? AU_CI    ? A_PAYER           ?
? Promotion Sports       ?   2 693,69   ?  ? AU_CI    ? A_PAYER           ?
? Prélèvement Comm.      ?   1 346,93   ?  ? AU_CI    ? A_PAYER           ?
? Redevance Statistique  ?   2 693,69   ?  ? AU_CI    ? A_PAYER           ?
? IMF                    ?   6 599,54   ?  ? AU_CI    ? A_PAYER           ?
? TVA                    ?  52 796,32   ?  ? AU_CI    ? A_PAYER           ?
???????????????????????????????????????????????????????????????????????????
? Total AU_CI (Cordon CI)? 153 404,61   ?                                  ?
? Total À PAYER (Comptant?  10 819,44   ?                                  ?
???????????????????????????????????????????????????????????????????????????
                                              [ Confirmer le Visa ]
```

- Bloquer le bouton "Confirmer" si une ligne n'a pas de décision sélectionnée.
- Afficher un résumé AU_CI / A_PAYER mis à jour en temps réel.

Appel : `POST /{id}/visa-dgd` avec la liste `decisions`.

---

### 7.3 Vue DGTCP ? Exécution de la liquidation (POST liquidation-douane)

Afficher en lecture seule :
1. Le tableau des lignes avec les affectations DGD (récupérées via `GET /{id}` ou `GET /{id}/lignes-bulletin`)
2. Le résumé financier :

```
Total pris en charge AU_CI  : 153 404,61 MRU
  dont TVA (cordon douanier): 52 796,32 MRU  ? alimentera le stock TVA d?ductible
  dont hors-TVA             : 100 608,29 MRU ? débitéra le solde cordon
Total à payer comptant      :  10 819,44 MRU
```

Bouton **"Exécuter la liquidation"** ? `POST /{id}/liquidation-douane` (pas de body)

Après exécution, afficher :
- `soldeCordonAvant` / `soldeCordonApres`
- Confirmation de l'alimentation du stock TVA d?ductible TVA

---

### 7.4 Vue récapitulatif de liquidation (statut LIQUIDEE)

Afficher :
1. **En-tête** : numéro déclaration, numéro bulletin, date, bureau de douane
2. **Tableau** des lignes groupées (GLOBALES puis ARTICLE) avec la colonne `affectation` DGD
3. **Récapitulatif final** :

| Catégorie | Montant (MRU) |
|---|---|
| Pris en charge par CI (AU_CI) | `totalPrisEnCharge` |
| ? dont TVA (stock TVA d?ductible) | `montantTVADouane` |
| ? dont hors-TVA (solde cordon) | `montantDroits` |
| À payer comptant | `totalAPayer` |
| **Total bulletin** | `totalPrisEnCharge + totalAPayer` |

---

## 8. Codes d'erreur fréquents

| HTTP | `errorCode` | Cause |
|---|---|---|
| `400` | `VALIDATION_FAILED` | Décision manquante pour une ligne, liste vide |
| `400` | `BUSINESS_RULE_VIOLATION` | Aucune ligne trouvée ; total AU_CI = 0 ; visa DGD manquant avant liquidation |
| `400` | `BUSINESS_RULE_VIOLATION` | Solde cordon insuffisant ; quota TVA insuffisant |
| `403` | `ROLE_FORBIDDEN` | Rôle non autorisé pour cette action |
| `409` | `WORKFLOW` | Transition de statut invalide (ex. DEMANDEE ? LIQUIDEE sans visa) |

---

## 9. Règles métier ? vérifications côté front

1. Au moins une ligne est requise pour soumettre une demande `DOUANIER`.
2. Lors du visa DGD : toutes les lignes doivent avoir une affectation (bloquer le bouton si une ligne n'en a pas).
3. `totalPrisEnCharge` doit être > 0 (sinon le backend rejette avec 400).
4. Après le visa, les lignes ne sont plus modifiables par l'entreprise.
5. La liquidation DGTCP n'est possible que si statut = `VISE`.
6. Si erreur "Solde cordon insuffisant", afficher le solde disponible (récupérable via le certificat de crédit).
