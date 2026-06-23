# Documentation Frontend — Demande d'Utilisation de Crédit d'Impôt Extérieur (Douanière)

> **Destinataire** : Équipe Frontend  
> **Mise à jour** : Juin 2026

**Validation pré-soumission (certificat éligible, soldes, transfert)** : voir [UTILISATION_CREDIT_VALIDATION_FRONT.md](./UTILISATION_CREDIT_VALIDATION_FRONT.md).

---

## 1. Workflow complet

```
ENTREPRISE / COMMISSION RELAIS        DGD                          DGTCP
??????????????????????????????????????????????????????????????????????????
1. Cr�e la demande + saisit les                                      
   lignes du bulletin                                                
   [statut ? DEMANDEE]                                               
                                                                     
2. D�pose les documents requis                                       
   (facultatif avant visa DGD)                                       
                                                                     
                              3a. (Optionnel) Prend en charge       
                                  [statut ? EN_VERIFICATION]        
                                                                     
                              3b. Annote chaque ligne du           
                                  bulletin : AU_CI ou A_PAYER       
                                  POST /{id}/visa-dgd               
                                  [statut ? VISE]                   
                                                                     
                                                    4. Ex�cute la liquidation
                                                       POST /{id}/liquidation-douane
                                                       ? d�bite soldeCordon de
                                                         (totalPrisEnCharge - TVA)
                                                       ? d�cr�mente quota TVA importation
                                                       ? alimente stock TVA d?ductible TVA
                                                       [statut ? LIQUIDEE]
```

### R�gle financi�re fondamentale

| Op�ration | Formule |
|---|---|
| D�bit solde cordon | `totalPrisEnCharge ? TVA_AU_CI` |
| D�bit quota TVA importation douane | `TVA_AU_CI` (lignes cod�es "TVA" avec affectation AU_CI) |
| Alimentation stock TVA d?ductible TVA d�ductible | `TVA_AU_CI` |
| Montant � payer comptant | `totalAPayer` (affich�, pas de d�bit CI) |

---

## 2. Statuts

| Statut | Acteur | Description |
|---|---|---|
| `DEMANDEE` | Entreprise | Demande soumise, en attente DGD |
| `BROUILLON` | Entreprise | Brouillon non soumis (optionnel) |
| `INCOMPLETE` | Services | Pi�ces compl�mentaires demand�es |
| `A_RECONTROLER` | Syst�me | Toutes les r�ponses au rejet temporaire fournies |
| `EN_VERIFICATION` | DGD | DGD a pris en charge (optionnel) |
| `VISE` | DGD | DGD a annot� les lignes et vis� |
| `LIQUIDEE` | DGTCP | Liquidation financi�re ex�cut�e |
| `REJETEE` | DGD / DGTCP | Demande refus�e d�finitivement |

### Transitions autoris�es

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
| `GLOBALE` | Taxes en en-t�te (ex. Taxe sur Tonnage, Redevance Informatique) |
| `ARTICLE` | Taxes par ligne de marchandise (ex. DD, TVA, RS, PSC, IMF) |

### `AffectationTaxe` (d�cision DGD)
| Valeur | Signification |
|---|---|
| `AU_CI` | Pris en charge par le cr�dit d'imp�t ext�rieur |
| `A_PAYER` | � payer comptant par l'entreprise |

---

## 4. Endpoints

### Base URL : `/api/utilisations-credit`

---

### 4.1 Cr�er une demande (ENTREPRISE / COMMISSION RELAIS)

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
      "denominationTaxe": "Taxe sur Tonnage Import�",
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
      "denominationTaxe": "Pr�l�vement Communautaire",
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
      "denominationTaxe": "Imp�t Minimum Forfaitaire",
      "typeLigne": "ARTICLE",
      "valeurTaxe": 6599.54
    },
    {
      "codeTaxe": "TVA",
      "denominationTaxe": "Taxe sur Valeur Ajout�e",
      "typeLigne": "ARTICLE",
      "valeurTaxe": 52796.32
    }
  ]
}
```

**R�ponse** : `201 Created` ? `UtilisationCreditDto` (voir �6)

---

### 4.2 Modifier une demande (brouillon / DEMANDEE)

**PUT** `/api/utilisations-credit/{id}`  
**Permission** : `utilisation.douane.submit`

M�me corps que la cr�ation. Remplace toutes les lignes.

---

### 4.3 Soumettre un brouillon

**POST** `/api/utilisations-credit/{id}/soumettre`  
**Permission** : `utilisation.douane.submit`

---

### 4.4 Changer le statut (DGD ? prise en charge optionnelle)

**PATCH** `/api/utilisations-credit/{id}/statut?statut=EN_VERIFICATION`  
**Permission** : `utilisation.douane.dgd.verify`

> �tape facultative. Le DGD peut passer directement � `visa-dgd` sans EN_VERIFICATION.

---

### 4.5 ? Visa DGD ? annotation des lignes + visa

**POST** `/api/utilisations-credit/{id}/visa-dgd`  
**Permission** : `utilisation.douane.dgd.quittance.visa`  
**Statut r�sultant** : `VISE`

Le DGD soumet ses d�cisions ligne par ligne. **Toutes les lignes doivent �tre couvertes.**

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

**R�ponse** : `200 OK` ? `UtilisationCreditDto` avec `statut: "VISE"`, `totalPrisEnCharge` et `totalAPayer` renseign�s.

> **Aucune op�ration financi�re � cette �tape.** Les valeurs sont juste calcul�es et sauvegard�es.

---

### 4.6 ? Liquidation DGTCP ? ex�cution financi�re

**POST** `/api/utilisations-credit/{id}/liquidation-douane`  
**Permission** : `utilisation.douane.dgtcp.impute` ou `utilisation.douane.dgtcp.solde.update`  
**Pr�requis** : statut `VISE` (visa DGD obligatoire)  
**Statut r�sultant** : `LIQUIDEE`  
**Corps** : aucun (pas de body)

Le DGTCP ex�cute automatiquement :
1. D�bite `soldeCordon` du certificat de `totalPrisEnCharge - TVA_AU_CI`
2. D�cr�mente `tvaImportationDouane` du certificat de `TVA_AU_CI`
3. Cr�e une entr�e dans le stock TVA d?ductible de TVA d�ductible de `TVA_AU_CI`

**R�ponse** : `200 OK` ? `UtilisationCreditDto` avec `statut: "LIQUIDEE"`.

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
    "denominationTaxe": "Imp�t Minimum Forfaitaire",
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

## 5. Permissions par r�le

| R�le | Permissions |
|---|---|
| **ENTREPRISE / COMMISSION RELAIS** | `utilisation.douane.submit` (cr�er, modifier, soumettre) |
| **DGD** | `utilisation.douane.dgd.verify` (EN_VERIFICATION), `utilisation.douane.dgd.quittance.visa` (visa-dgd), `utilisation.douane.dgd.reject` (rejeter), `utilisation.douane.dgd.queue.view` (consulter) |
| **DGTCP** | `utilisation.douane.dgtcp.impute` (liquidation-douane), `utilisation.douane.dgtcp.queue.view` (consulter) |

---

## 6. DTO de retour ? `UtilisationCreditDto`

```typescript
interface UtilisationCreditDto {
  id: number;
  type: "DOUANIER" | "TVA_INTERIEURE";
  dateDemande: string;           // ISO-8601
  montant: number | null;        // Montant d�bit� du solde cordon (apr�s liquidation)
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
  soldeCordonAvant: number | null;   // Renseign� apr�s liquidation DGTCP
  soldeCordonApres: number | null;   // Renseign� apr�s liquidation DGTCP

  /** Lignes du bulletin ? pr�sentes d�s la cr�ation */
  lignes: LigneBulletinDto[];

  /** Somme des lignes AU_CI ? renseign� apr�s visa DGD */
  totalPrisEnCharge: number | null;

  /** Somme des lignes A_PAYER ? renseign� apr�s visa DGD */
  totalAPayer: number | null;

  /** Part TVA des lignes AU_CI (pour info comptable) */
  montantTVADouane: number | null;

  /** Part hors-TVA des lignes AU_CI (= montant d�bit� solde cordon) */
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

## 7. Guide UI ? composants � impl�menter

### 7.1 Formulaire de cr�ation (ENTREPRISE / COMMISSION RELAIS)

**Partie A ? En-t�te**

| Champ | Type | Obligatoire |
|---|---|---|
| Certificat de cr�dit | S�lecteur | Oui |
| Num�ro de d�claration | Texte | Non |
| Num�ro de bulletin | Texte | Non |
| Date de d�claration | Date | Non |
| Enregistr�e SYDONIA | Checkbox | Non |

**Partie B ? Tableau des lignes (dynamique)**

| Colonne | Type | Obligatoire |
|---|---|---|
| Code taxe | Texte court (?20 car.) | Oui |
| D�nomination | Texte (?120 car.) | Oui |
| Type | Radio : `GLOBALE` / `ARTICLE` | Oui |
| Valeur (MRU) | Num�rique ? 0 | Oui |
| _(Supprimer)_ | Bouton | ? |

Bouton **"+ Ajouter une ligne"**  
Afficher le **Total** en temps r�el (somme des valeurTaxe).

**Codes courants** (suggestion auto-compl�tion) :

| Code | Libell� | Type habituel |
|---|---|---|
| `DD` | Droit de Douane | ARTICLE |
| `TVA` | Taxe sur Valeur Ajout�e | ARTICLE |
| `RS` | Redevance Statistique | ARTICLE |
| `PSC` | Promotion Sports et Culture | ARTICLE |
| `PC` | Pr�l�vement Communautaire | ARTICLE |
| `IMF` | Imp�t Minimum Forfaitaire | ARTICLE |
| `TST` | Taxe sur Tonnage Import� | GLOBALE |
| `RI` | Redevance Informatique | GLOBALE |

---

### 7.2 Vue DGD ? Annotation du bulletin (POST visa-dgd)

Afficher le tableau du bulletin avec une colonne de d�cision pour chaque ligne :

```
???????????????????????????????????????????????????????????????????????????
?  BULLETIN DE LIQUIDATION ? Visa DGD                                     ?
????????????????????????????????????????????????????????????????????????  ?
? Taxe                   ? Valeur (MRU) ? D�cision DGD                    ?
???????????????????????????????????????????????????????????????????????? ??
? [Section GLOBALES]                                                       ?
? Taxe sur Tonnage       ?   3 919,90   ?  ? AU_CI    ? A_PAYER           ?
? Redevance Informatique ?     300,00   ?  ? AU_CI    ? A_PAYER           ?
????????????????????????????????????????????????????????????????????????? ?
? [Section ARTICLE]                                                        ?
? Droit de Douane        ?  93 873,98   ?  ? AU_CI    ? A_PAYER           ?
? Promotion Sports       ?   2 693,69   ?  ? AU_CI    ? A_PAYER           ?
? Pr�l�vement Comm.      ?   1 346,93   ?  ? AU_CI    ? A_PAYER           ?
? Redevance Statistique  ?   2 693,69   ?  ? AU_CI    ? A_PAYER           ?
? IMF                    ?   6 599,54   ?  ? AU_CI    ? A_PAYER           ?
? TVA                    ?  52 796,32   ?  ? AU_CI    ? A_PAYER           ?
???????????????????????????????????????????????????????????????????????????
? Total AU_CI (Cordon CI)? 153 404,61   ?                                  ?
? Total � PAYER (Comptant?  10 819,44   ?                                  ?
???????????????????????????????????????????????????????????????????????????
                                              [ Confirmer le Visa ]
```

- Bloquer le bouton "Confirmer" si une ligne n'a pas de d�cision s�lectionn�e.
- Afficher un r�sum� AU_CI / A_PAYER mis � jour en temps r�el.

Appel : `POST /{id}/visa-dgd` avec la liste `decisions`.

---

### 7.3 Vue DGTCP ? Ex�cution de la liquidation (POST liquidation-douane)

Afficher en lecture seule :
1. Le tableau des lignes avec les affectations DGD (r�cup�r�es via `GET /{id}` ou `GET /{id}/lignes-bulletin`)
2. Le r�sum� financier :

```
Total pris en charge AU_CI  : 153 404,61 MRU
  dont TVA (cordon douanier): 52 796,32 MRU  ? alimentera le stock TVA d?ductible
  dont hors-TVA             : 100 608,29 MRU ? d�bit�ra le solde cordon
Total � payer comptant      :  10 819,44 MRU
```

Bouton **"Ex�cuter la liquidation"** ? `POST /{id}/liquidation-douane` (pas de body)

Apr�s ex�cution, afficher :
- `soldeCordonAvant` / `soldeCordonApres`
- Confirmation de l'alimentation du stock TVA d?ductible TVA

---

### 7.4 Vue r�capitulatif de liquidation (statut LIQUIDEE)

Afficher :
1. **En-t�te** : num�ro d�claration, num�ro bulletin, date, bureau de douane
2. **Tableau** des lignes group�es (GLOBALES puis ARTICLE) avec la colonne `affectation` DGD
3. **R�capitulatif final** :

| Cat�gorie | Montant (MRU) |
|---|---|
| Pris en charge par CI (AU_CI) | `totalPrisEnCharge` |
| ? dont TVA (stock TVA d?ductible) | `montantTVADouane` |
| ? dont hors-TVA (solde cordon) | `montantDroits` |
| � payer comptant | `totalAPayer` |
| **Total bulletin** | `totalPrisEnCharge + totalAPayer` |

---

## 8. Codes d'erreur fr�quents

| HTTP | `errorCode` | Cause |
|---|---|---|
| `400` | `VALIDATION_FAILED` | D�cision manquante pour une ligne, liste vide |
| `400` | `BUSINESS_RULE_VIOLATION` | Aucune ligne trouv�e ; total AU_CI = 0 ; visa DGD manquant avant liquidation |
| `400` | `BUSINESS_RULE_VIOLATION` | Solde cordon insuffisant ; quota TVA insuffisant |
| `403` | `ROLE_FORBIDDEN` | R�le non autoris� pour cette action |
| `409` | `WORKFLOW` | Transition de statut invalide (ex. DEMANDEE ? LIQUIDEE sans visa) |

---

## 9. R�gles m�tier ? v�rifications c�t� front

1. Au moins une ligne est requise pour soumettre une demande `DOUANIER`.
2. Lors du visa DGD : toutes les lignes doivent avoir une affectation (bloquer le bouton si une ligne n'en a pas).
3. `totalPrisEnCharge` doit �tre > 0 (sinon le backend rejette avec 400).
4. Apr�s le visa, les lignes ne sont plus modifiables par l'entreprise.
5. La liquidation DGTCP n'est possible que si statut = `VISE`.
6. Si erreur "Solde cordon insuffisant", afficher le solde disponible (r�cup�rable via le certificat de cr�dit).
