# Documentation API — Utilisation du Crédit d'Impôt

> Base URL : `http://localhost:8080/api`  
> Auth : `Authorization: Bearer <jwt>`

---

## Vue d'ensemble

Après l'ouverture du certificat (`statut = OUVERT`), deux soldes sont actifs :

| Solde | Champ certificat | Usage |
|-------|-----------------|-------|
| **Solde Cordon** | `soldeCordon` | Couvre droits de douane + TVA import |
| **Solde TVA Intérieure** | `soldeTVA` | Couvre TVA sur achats locaux et décomptes |

Deux types d'opérations peuvent se produire en parallèle, dans n'importe quel ordre :

```
CERTIFICAT OUVERT
   │
   ├─── Importation (DOUANIER) ──────────────────► débite soldeCordon
   │         └── génère stock TVA déductible FIFO ─►┐
   │                                                  │
   └─── Achat local / Décompte (TVA_INTERIEURE) ◄────┘
              └── apurement : TVA collectée - stock FIFO
                  │
                  ├─ TVA nette > 0  → débite soldeTVA (ou cash si insuffisant)
                  ├─ TVA nette = 0  → rien ne bouge
                  └─ TVA nette < 0  → report à nouveau → crédite soldeTVA
```

---

## 1. Soldes courants du certificat

```
GET /api/certificats-credit/{id}
```

**Réponse** (`CertificatCreditDto`) — champs clés :

| Champ | Description |
|-------|-------------|
| `soldeCordon` | Solde cordon disponible (ne fait que baisser) |
| `soldeTVA` | Solde TVA intérieure (peut monter/descendre) |
| `montantCordon` | Montant initial cordon |
| `montantTVAInterieure` | Montant initial TVA |
| `statut` | Doit être `OUVERT` pour toute utilisation |

---

## 2. Stock TVA Déductible (FIFO)

```
GET /api/certificats-credit/{id}/tva-stock
```

**Rôles autorisés** : DGTCP, archivage  
**Réponse** : tableau de `TvaDeductibleStockDto` trié par date croissante (ordre FIFO)

```json
[
  {
    "id": 1,
    "utilisationDouaneId": 5,
    "numeroDeclaration": "DEC-2024-001",
    "montantInitial": 120000.00,
    "montantRestant": 45000.00,
    "montantConsomme": 75000.00,
    "dateCreation": "2024-03-15T10:00:00Z",
    "epuise": false
  },
  {
    "id": 2,
    "utilisationDouaneId": 8,
    "numeroDeclaration": "DEC-2024-002",
    "montantInitial": 80000.00,
    "montantRestant": 80000.00,
    "montantConsomme": 0.00,
    "dateCreation": "2024-04-01T14:30:00Z",
    "epuise": false
  }
]
```

**Usage UI** : afficher un tableau "Stock TVA déductible disponible" avec total = somme des `montantRestant`.

---

## 3. Opération 1 — Importation Douanière (type = DOUANIER)

### Flux complet

```
Entreprise                  DGD                      DGTCP
    │                        │                          │
    ├─ POST /utilisations     │                          │
    │  (créer demande)        │                          │
    │                        │                          │
    ├─ POST /{id}/documents   │                          │
    │  (upload docs)          │                          │
    │                        │                          │
    │──────────────────────► PATCH /{id}/statut          │
    │                        │ ?statut=EN_VERIFICATION   │
    │                        │                          │
    │                        ├─ (examen conformité)     │
    │                        │                          │
    │──────────────────────► PATCH /{id}/statut          │
    │                        │ ?statut=VISE              │
    │                        │                          │
    │                        │─────────────────────────► POST /{id}/liquidation-douane
    │                        │                          │ (montantDroits + montantTVA)
    │                        │                          │
    │                        │                          ├─ débite soldeCordon
    │                        │                          └─ crée tranche FIFO dans stock TVA
```

### Étape 1 — Créer la demande

```
POST /api/utilisations-credit
```

**Rôle** : `utilisation.douane.submit` (Entreprise)  
**Body** :

```json
{
  "type": "DOUANIER",
  "certificatCreditId": 1,
  "entrepriseId": 2,
  "numeroDeclaration": "DEC-2024-001",
  "numeroBulletin": "BUL-2024-001",
  "dateDeclaration": "2024-03-15",
  "montantDroits": 90000.00,
  "montantTVA": 30000.00,
  "enregistreeSYDONIA": true
}
```

> `montant` est auto-calculé = `montantDroits + montantTVA`

### Étape 2 — Uploader les documents

```
POST /api/utilisations-credit/{id}/documents   (multipart/form-data)
```

**Rôle** : `utilisation.douane.document.upload`  
**Params** : `file`, `type` (enum TypeDocument), `message` (optionnel)

**Documents requis (UTILISATION_CI_DOUANE)** :

| TypeDocument | Description |
|-------------|-------------|
| `DEMANDE_UTILISATION` | Demande d'utilisation |
| `ORDRE_TRANSIT` | Ordre de transit |
| `DECLARATION_DOUANE` | Déclaration en douane |
| `BULLETIN_LIQUIDATION` | Bulletin de liquidation |
| `FACTURE` | Facture commerciale |
| `CONNAISSEMENT` | Connaissement / LTA / LVI |
| `CERTIFICAT_CREDIT_IMPOTS_SYDONIA` | Copie du certificat (SYDONIA) |

### Étape 3 — DGD : Passer en vérification

```
PATCH /api/utilisations-credit/{id}/statut?statut=EN_VERIFICATION
```

**Rôle** : `utilisation.douane.dgd.verify`

### Étape 4 — DGD : Apposer le visa

```
PATCH /api/utilisations-credit/{id}/statut?statut=VISE
```

**Rôle** : `utilisation.douane.dgd.quittance.visa`

### Étape 5 — DGTCP : Liquider (imputation sur le certificat)

```
POST /api/utilisations-credit/{id}/liquidation-douane
```

**Rôle** : `utilisation.douane.dgtcp.impute`  
**Body** :

```json
{
  "montantDroits": 90000.00,
  "montantTVA": 30000.00
}
```

**Réponse** (`UtilisationCreditDto`) — champs clés après liquidation :

```json
{
  "statut": "LIQUIDEE",
  "montant": 120000.00,
  "montantDroits": 90000.00,
  "montantTVADouane": 30000.00,
  "soldeCordonAvant": 500000.00,
  "soldeCordonApres": 380000.00,
  "dateLiquidation": "2024-03-15T10:00:00Z"
}
```

**Effets backend** :
- `soldeCordon` du certificat baisse de `montantTotal`
- Une tranche est ajoutée au stock FIFO : `montantTVA` (30 000 ici) disponible pour apurement futur

---

## 4. Opération 2 — Achat Local / Décompte (type = TVA_INTERIEURE)

### Flux complet

```
Entreprise                                         DGTCP
    │                                                │
    ├─ POST /utilisations (créer demande)             │
    │                                                │
    ├─ POST /{id}/documents (upload facture/décompte) │
    │                                                │
    │────────────────────────────────────────────── PATCH /{id}/statut?statut=EN_VERIFICATION
    │                                                │
    │                                                ├─ (vérification des justificatifs)
    │                                                │
    │────────────────────────────────────────────── PATCH /{id}/statut?statut=VALIDEE
    │                                                │
    │────────────────────────────────────────────── POST /{id}/apurement-tva
    │                                                │ (calcul FIFO automatique)
    │                                                │
    │                                                ├─ consomme stock FIFO
    │                                                ├─ calcule TVA nette
    │                                                └─ ajuste soldeTVA
```

### Étape 1 — Créer la demande

```
POST /api/utilisations-credit
```

**Rôle** : `utilisation.interieur.submit` (Entreprise)  
**Body — Achat local** :

```json
{
  "type": "TVA_INTERIEURE",
  "certificatCreditId": 1,
  "entrepriseId": 2,
  "typeAchat": "ACHAT_LOCAL",
  "numeroFacture": "FAC-2024-042",
  "dateFacture": "2024-04-10",
  "montantTVAInterieure": 55000.00
}
```

**Body — Décompte** :

```json
{
  "type": "TVA_INTERIEURE",
  "certificatCreditId": 1,
  "entrepriseId": 2,
  "typeAchat": "DECOMPTE",
  "numeroDecompte": "DEC-TRAV-003",
  "montantTVAInterieure": 120000.00
}
```

> `typeAchat` : `ACHAT_LOCAL` ou `DECOMPTE`. Si absent, déduit de `numeroDecompte`.

### Étape 2 — Uploader les documents

```
POST /api/utilisations-credit/{id}/documents   (multipart/form-data)
```

**Documents requis (UTILISATION_CI_TVA_INTERIEURE)** :

| TypeDocument | Requis pour |
|-------------|-------------|
| `FACTURE` | Achat local (obligatoire) |
| `DECLARATION_TVA` | Achat local (obligatoire) |
| `DECOMPTE` | Décompte travaux (obligatoire) |

### Étape 3 — DGTCP : Vérifier

```
PATCH /api/utilisations-credit/{id}/statut?statut=EN_VERIFICATION
```

**Rôle** : `utilisation.interieur.dgtcp.verify`

### Étape 4 — DGTCP : Valider

```
PATCH /api/utilisations-credit/{id}/statut?statut=VALIDEE
```

**Rôle** : `utilisation.interieur.dgtcp.validate`

### Étape 5 — DGTCP : Procéder à l'apurement

```
POST /api/utilisations-credit/{id}/apurement-tva
```

**Rôle** : `utilisation.interieur.dgtcp.solde.update`  
**Body** (le champ est optionnel — si absent, tout le stock disponible est consommé) :

```json
{}
```

ou pour spécifier manuellement :

```json
{
  "tvaDeductibleUtilisee": 45000.00
}
```

**Réponse** (`UtilisationCreditDto`) — champs d'apurement :

```json
{
  "statut": "APUREE",
  "montantTVAInterieure": 55000.00,
  "tvaDeductibleUtilisee": 45000.00,
  "tvaNette": 10000.00,
  "soldeTVAAvant": 200000.00,
  "creditInterieurUtilise": 10000.00,
  "paiementEntreprise": 0.00,
  "reportANouveau": 0.00,
  "soldeTVAApres": 190000.00,
  "dateLiquidation": "2024-04-10T14:00:00Z"
}
```

---

## 5. Logique d'apurement — 3 cas (à afficher en UI)

Après calcul : **TVA nette = TVA collectée − TVA déductible utilisée**

### Cas 1 — TVA nette = 0 (équilibre parfait)
```
tvaCollectee = 45 000
tvaDeductibleUtilisee = 45 000
tvaNette = 0
creditInterieurUtilise = 0
paiementEntreprise = 0
reportANouveau = 0
soldeTVA : inchangé
```

### Cas 2 — TVA nette > 0 (entreprise doit de la TVA)

**Sous-cas 2a — Solde TVA suffisant :**
```
tvaCollectee = 55 000
tvaDeductibleUtilisee = 45 000
tvaNette = 10 000

soldeTVAAvant = 200 000
creditInterieurUtilise = 10 000     ← prélevé sur le certificat
paiementEntreprise = 0
soldeTVAApres = 190 000
```

**Sous-cas 2b — Solde TVA insuffisant (paiement cash partiel) :**
```
tvaCollectee = 55 000
tvaDeductibleUtilisee = 45 000
tvaNette = 10 000

soldeTVAAvant = 6 000               ← solde insuffisant
creditInterieurUtilise = 6 000      ← solde épuisé
paiementEntreprise = 4 000          ← entreprise paie 4 000 MRU en cash
soldeTVAApres = 0
```

### Cas 3 — TVA nette < 0 (report à nouveau)
```
tvaCollectee = 20 000
tvaDeductibleUtilisee = 80 000      ← beaucoup de stock accumulé
tvaNette = -60 000

reportANouveau = 60 000             ← excédent → crédite soldeTVA
soldeTVAAvant = 50 000
creditInterieurUtilise = 0
paiementEntreprise = 0
soldeTVAApres = 110 000             ← soldeTVA augmente
```

---

## 6. Gestion des rejets temporaires

Les 3 acteurs (DGD, DGTCP, DGI) peuvent rejeter temporairement pour demander des compléments.

### Émettre un rejet temporaire

```
POST /api/utilisations-credit/{id}/decisions
```

**Body** :

```json
{
  "decision": "REJET_TEMP",
  "motifRejet": "Déclaration douanière manquante",
  "documentsDemandes": ["DECLARATION_DOUANE", "FACTURE"]
}
```

**Effet** : statut → `INCOMPLETE`

### L'entreprise répond

```
POST /api/utilisations-credit/decisions/{decisionId}/rejet-temp/reponses
```

**Body** :

```json
{
  "message": "Documents complémentaires déposés"
}
```

### L'acteur résout le rejet

```
PUT /api/utilisations-credit/decisions/{decisionId}/resolve
```

**Effet** : si plus aucun rejet ouvert → statut → `A_RECONTROLER`

### Consulter les décisions

```
GET /api/utilisations-credit/{id}/decisions
```

**Réponse** : `DecisionCreditDto[]`

---

## 7. Consulter les utilisations

| Endpoint | Usage |
|----------|-------|
| `GET /api/utilisations-credit` | Toutes les utilisations (filtré par rôle côté service) |
| `GET /api/utilisations-credit/{id}` | Détail d'une utilisation |
| `GET /api/utilisations-credit/by-certificat/{certId}` | Toutes les utilisations d'un certificat |
| `GET /api/utilisations-credit/{id}/documents` | Documents d'une utilisation |
| `GET /api/certificats-credit/{id}/tva-stock` | Stock FIFO TVA déductible du certificat |

---

## 8. Statuts et transitions

### Douanière (DOUANIER)

```
DEMANDEE ──► EN_VERIFICATION (DGD) ──► VISE (DGD) ──► LIQUIDEE (DGTCP, via endpoint dédié)
    │                │                    │
    └────────────────┴────────────────────┴──► INCOMPLETE ──► A_RECONTROLER ──► EN_VERIFICATION
                                                    └──────────────────────────► REJETEE (définitif)
```

### TVA Intérieure (TVA_INTERIEURE)

```
DEMANDEE ──► EN_VERIFICATION (DGTCP) ──► VALIDEE (DGTCP) ──► APUREE (DGTCP, via endpoint dédié)
    │                │                       │
    └────────────────┴───────────────────────┴──► INCOMPLETE ──► A_RECONTROLER ──► EN_VERIFICATION
                                                      └──────────────────────────► REJETEE (définitif)
```

---

## 9. Indicateurs à afficher en UI (tableau de bord certificat)

| Indicateur | Source | Notes |
|-----------|--------|-------|
| Solde Cordon disponible | `certificat.soldeCordon` | Ne remonte jamais |
| Solde TVA Intérieure | `certificat.soldeTVA` | Peut monter (report à nouveau) |
| Stock TVA Déductible total | Somme `tvaStock[].montantRestant` | Consommé au fur et à mesure |
| Importations liquidées | `/by-certificat/{id}` filtre `type=DOUANIER, statut=LIQUIDEE` | — |
| Apurements réalisés | `/by-certificat/{id}` filtre `type=TVA_INTERIEURE, statut=APUREE` | — |
| Paiement cash cumulé | Somme `paiementEntreprise` sur APUREE | Ce qui n'a pas pu être couvert |
| Reports à nouveau cumulés | Somme `reportANouveau` sur APUREE | Crédit supplémentaire généré |

---

## 10. Rôles et permissions

| Rôle | Permissions clés |
|------|-----------------|
| **Entreprise** | `utilisation.douane.submit`, `utilisation.interieur.submit`, `*.document.upload`, `*.solde.view`, `*.history.view` |
| **DGD** | `utilisation.douane.dgd.queue.view`, `utilisation.douane.dgd.verify`, `utilisation.douane.dgd.quittance.visa`, `utilisation.douane.dgd.reject` |
| **DGTCP** | `utilisation.douane.dgtcp.queue.view`, `utilisation.douane.dgtcp.impute`, `utilisation.interieur.dgtcp.queue.view`, `utilisation.interieur.dgtcp.verify`, `utilisation.interieur.dgtcp.validate`, `utilisation.interieur.dgtcp.solde.update`, `utilisation.interieur.dgtcp.reject` |
| **DGI** | `utilisation.interieur.dgi.view` |

---

## 11. Exemple de scénario complet

```
[T1] Importation : 100 000 MRU (droits=70k, TVA import=30k)
     → soldeCordon : 500 000 → 400 000
     → stock FIFO : +30 000 (tranche #1)

[T2] Importation : 200 000 MRU (droits=140k, TVA import=60k)
     → soldeCordon : 400 000 → 200 000
     → stock FIFO : +60 000 (tranche #2) | total dispo = 90 000

[T3] Décompte travaux : TVA collectée = 50 000
     → apurement auto : utilise 50 000 du stock (tranche #1 30k + 20k de tranche #2)
     → TVA nette = 50 000 - 50 000 = 0
     → stock FIFO restant : 40 000 (40k de tranche #2)
     → soldeTVA : inchangé

[T4] Achat local : TVA collectée = 10 000
     → apurement auto : utilise 10 000 du stock (10k de tranche #2)
     → TVA nette = 10 000 - 10 000 = 0
     → stock FIFO restant : 30 000

[T5] Décompte travaux : TVA collectée = 80 000
     → apurement auto : utilise 30 000 du stock (épuise tranche #2)
     → TVA nette = 80 000 - 30 000 = 50 000 (positif)
     → soldeTVA avant = 300 000 → soldeTVA après = 250 000
     → creditInterieurUtilise = 50 000, paiementEntreprise = 0
     → stock FIFO : épuisé
```
