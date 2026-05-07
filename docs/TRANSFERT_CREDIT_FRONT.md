# Documentation Frontend — Transfert de Crédit d'Impôt Extérieur

> **Destinataire** : Équipe Frontend  
> **Mise à jour** : Mai 2026

---

## 1. Principe métier

Le **transfert de crédit** permet à une entreprise de clôturer ses opérations douanières et de transférer le **restant du quota TVA importation douane** vers le **stock TVA déductible**.

Ce stock sera ensuite consommé automatiquement (ordre chronologique) lors des **apurements TVA intérieure** de l'entreprise.

```
AVANT TRANSFERT                         APRÈS TRANSFERT
─────────────────────────────────────────────────────────────
Certificat de crédit                    Certificat de crédit
  tvaImportationDouane = X MRU    →       tvaImportationDouane = 0
                                          
                                        Stock TVA déductible
                                          + entrée de X MRU
                                          (consommée lors des
                                           apurements TVA intérieure)
```

---

## 2. Workflow

```
ENTREPRISE                        DGTCP / PRÉSIDENT
──────────────────────────────────────────────────────────────

1. Crée la demande
   [statut → DEMANDE]

2. Dépose les documents requis

   ← (optionnel) Rejet temporaire ──── DGTCP / PRÉSIDENT
     [statut → INCOMPLETE]              demande des pièces
   → Répond avec documents
     [statut → A_RECONTROLER]

3. (Optionnel) Annule la demande
   [statut → ANNULEE]
   
                              4. Valide ET exécute le transfert
                                 POST /{id}/valider
                                 • tvaImportationDouane → 0
                                 • crée entrée stock TVA déductible
                                 • clôture utilisations douanières
                                 [statut → TRANSFERE]

                              — OU —

                              5. Rejette définitivement
                                 POST /{id}/rejeter
                                 [statut → REJETE]
```

---

## 3. Statuts

| Statut | Acteur | Description |
|---|---|---|
| `DEMANDE` | Entreprise | Demande soumise |
| `EN_COURS` | DGTCP | En cours de traitement |
| `VALIDE` | DGTCP / Président | Validé, en attente d'exécution |
| `INCOMPLETE` | DGTCP / Président | Pièces complémentaires demandées |
| `A_RECONTROLER` | Système | Toutes les réponses fournies |
| `TRANSFERE` | DGTCP / Président | Transfert exécuté (état final) |
| `REJETE` | DGTCP / Président | Refus définitif |
| `ANNULEE` | Entreprise | Retirée avant exécution |

---

## 4. Endpoints

### Base URL : `/api/transferts-credit`

---

### 4.1 Lister les transferts

**GET** `/api/transferts-credit`  
**Permissions** : `transfert.solde.view`, `transfert.dgtcp.queue.view`, `transfert.president.validate`, `transfert.president.reject`, `archivage.view`

L'entreprise ne voit que ses propres demandes. Les services voient tout.

**Réponse** : `200 OK` → `TransfertCreditDto[]`

---

### 4.2 Obtenir un transfert

**GET** `/api/transferts-credit/{id}`  
**Permissions** : idem 4.1

**Réponse** : `200 OK` → `TransfertCreditDto`

---

### 4.3 Obtenir le transfert d'un certificat

**GET** `/api/transferts-credit/by-certificat/{certificatCreditId}`  
**Permissions** : idem 4.1

> Retourne la dernière demande de transfert pour ce certificat, ou `404` si aucune.

---

### 4.4 Créer une demande de transfert (ENTREPRISE)

**POST** `/api/transferts-credit`  
**Permission** : `transfert.submit`

```json
{
  "certificatCreditId": 12,
  "montant": null,
  "operationsDouaneCloturees": true
}
```

| Champ | Type | Description |
|---|---|---|
| `certificatCreditId` | Long | Obligatoire |
| `montant` | Decimal | Indicatif (optionnel — le système prend le restant `tvaImportationDouane`) |
| `operationsDouaneCloturees` | Boolean | Obligatoire à `true` pour que le transfert puisse être validé |

**Réponse** : `201 Created` → `TransfertCreditDto`

> **Remarque** : Si une demande précédente était `REJETE` ou `ANNULEE`, elle est réactivée (pas de doublon).

---

### 4.5 Annuler une demande (ENTREPRISE)

**POST** `/api/transferts-credit/{id}/annuler`  
**Permission** : `transfert.annuler`

Possible uniquement si statut ≠ `TRANSFERE`, `REJETE`, `ANNULEE`.

**Réponse** : `200 OK` → `TransfertCreditDto` avec `statut: "ANNULEE"`

---

### 4.6 Valider et exécuter le transfert (DGTCP / PRÉSIDENT)

**POST** `/api/transferts-credit/{id}/valider`  
**Permissions** : `transfert.dgtcp.update`, `transfert.president.validate`

Conditions préalables vérifiées par le backend :
- Statut = `DEMANDE`, `EN_COURS`, `VALIDE` ou `A_RECONTROLER`
- Aucun rejet temporaire ouvert
- Documents obligatoires présents
- `operationsDouaneCloturees = true`
- Certificat `OUVERT`

**Effets** :
1. `tvaImportationDouane` du certificat → 0
2. Création d'une entrée dans le **stock TVA déductible** de valeur = restant `tvaImportationDouane`
3. Toutes les utilisations douanières encore ouvertes → `CLOTUREE`
4. `statut` → `TRANSFERE`
5. `montant` du transfert = montant versé dans le stock

**Réponse** : `200 OK` → `TransfertCreditDto` avec `statut: "TRANSFERE"` et `montant` = TVA transférée

---

### 4.7 Rejeter définitivement (DGTCP / PRÉSIDENT)

**POST** `/api/transferts-credit/{id}/rejeter`  
**Permissions** : `transfert.dgtcp.update`, `transfert.president.reject`

**Réponse** : `200 OK` → `TransfertCreditDto` avec `statut: "REJETE"`

---

### 4.8 Lister les documents d'une demande

**GET** `/api/transferts-credit/{id}/documents`  
**Permissions** : `transfert.solde.view`, `transfert.dgtcp.queue.view`, `transfert.president.validate`, `transfert.president.reject`, `archivage.view`

**Réponse** : `200 OK` → `DocumentTransfertCreditDto[]`

---

### 4.9 Déposer un document

**POST** `/api/transferts-credit/{id}/documents`  
**Permission** : `transfert.submit`  
**Content-Type** : `multipart/form-data`

| Paramètre | Type | Description |
|---|---|---|
| `type` | `TypeDocument` | Type du document |
| `file` | Fichier | Fichier à déposer |
| `message` | String (opt.) | Message de réponse si rejet temporaire ouvert |

---

## 5. DTO — `TransfertCreditDto`

```typescript
interface TransfertCreditDto {
  id: number;
  dateDemande: string;             // ISO-8601
  certificatCreditId: number;
  certificatNumero: string;
  entrepriseSourceId: number;

  /**
   * Montant TVA transféré dans le stock TVA déductible.
   * Correspond au restant de tvaImportationDouane au moment du transfert.
   * Renseigné après statut TRANSFERE.
   */
  montant: number | null;

  operationsDouaneCloturees: boolean;
  statut: StatutTransfert;
}

type StatutTransfert =
  | "DEMANDE"
  | "EN_COURS"
  | "VALIDE"
  | "INCOMPLETE"
  | "A_RECONTROLER"
  | "TRANSFERE"
  | "REJETE"
  | "ANNULEE";
```

---

## 6. Permissions par rôle

| Rôle | Permission | Action |
|---|---|---|
| **ENTREPRISE / COMMISSION RELAIS** | `transfert.submit` | Créer, déposer documents |
| **ENTREPRISE / COMMISSION RELAIS** | `transfert.annuler` | Annuler sa demande |
| **ENTREPRISE / COMMISSION RELAIS** | `transfert.solde.view` | Consulter |
| **DGTCP** | `transfert.dgtcp.queue.view` | Consulter la file |
| **DGTCP** | `transfert.dgtcp.update` | Valider, rejeter |
| **PRÉSIDENT** | `transfert.president.validate` | Valider |
| **PRÉSIDENT** | `transfert.president.reject` | Rejeter |

---

## 7. Codes d'erreur fréquents

| HTTP | `errorCode` | Cause |
|---|---|---|
| `400` | `BUSINESS_RULE_VIOLATION` | `operationsDouaneCloturees = false`; rejet temporaire ouvert; certificat non OUVERT |
| `403` | `ROLE_FORBIDDEN` | Rôle non autorisé pour cette action |
| `404` | `RESOURCE_NOT_FOUND` | Certificat ou transfert introuvable |
| `409` | `CONFLICT` | Transfert déjà exécuté; demande déjà en cours |

---

## 8. Guide UI — composants à implémenter

### 8.1 Formulaire de création (ENTREPRISE)

| Champ | Type | Obligatoire | Note |
|---|---|---|---|
| Certificat de crédit | Sélecteur | Oui | Afficher `tvaImportationDouane` disponible |
| Opérations douane clôturées | Checkbox | Oui | Doit être coché pour que DGTCP puisse valider |
| Montant indicatif | Numérique | Non | Si vide, le système prend le restant total |

> Afficher en lecture seule le solde `tvaImportationDouane` du certificat sélectionné afin que l'entreprise sache ce qui sera transféré.

---

### 8.2 Vue DGTCP — Validation du transfert

Afficher avant validation :

```
Certificat : CC-2023-001
TVA importation douane restante : 52 796,32 MRU
                                   ↓ sera versée dans le stock TVA déductible
Opérations douane clôturées : ✓

[Valider le transfert]    [Rejeter]
```

Après validation (`statut = TRANSFERE`) :

```
✓ Transfert exécuté
Montant versé au stock TVA déductible : 52 796,32 MRU
Utilisations douanières clôturées : N demandes
```

---

### 8.3 Impact sur le stock TVA déductible

Après un transfert réussi, l'entrée nouvellement créée dans le stock TVA déductible est visible via :

**GET** `/api/utilisations-credit/tva-stock/by-certificat/{certificatCreditId}`

```typescript
interface TvaDeductibleStockDto {
  id: number;
  utilisationDouaneId: number | null; // null si origine = transfert de crédit
  numeroDeclaration: string | null;
  montantInitial: number;
  montantRestant: number;
  montantConsomme: number;
  dateCreation: string;
  epuise: boolean;
}
```

> Quand `utilisationDouaneId` est `null`, l'origine est un **transfert de crédit** (pas une liquidation douanière).

---

## 9. Relation avec les autres modules

| Module | Impact après `TRANSFERE` |
|---|---|
| **Utilisations douanières** | Toutes celles encore ouvertes → `CLOTUREE` automatiquement |
| **Nouvelles utilisations douanières** | Impossibles sur ce certificat (un transfert exécuté bloque de nouvelles demandes) |
| **Stock TVA déductible** | Nouvelle entrée créée avec le restant `tvaImportationDouane` |
| **Apurements TVA intérieure** | Consomment le stock créé par le transfert (ordre chronologique) |
