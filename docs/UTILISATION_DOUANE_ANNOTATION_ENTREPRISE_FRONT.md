# Utilisation crédit extérieur (douane) — Annotation entreprise puis visa DGD

## Nouveau principe

1. **Entreprise** (création / édition brouillon ou demande `DEMANDEE`) : pour chaque ligne du bulletin, saisit le montant **et** choisit **AU_CI** (pris en charge par le crédit / cordon) ou **A_PAYER** (à payer comptant).
2. **DGD** (`POST /api/utilisations-credit/{id}/visa-dgd`) : consulte les propositions, **valide** (même choix) ou **modifie** (autre affectation et/ou montant), puis enregistre le visa → statut `EN_CONTROLE_DGD`.

## Modèle API — ligne de bulletin

Chaque élément de `lignes[]` dans la réponse :

| Champ | Qui le remplit | Description |
|--------|----------------|-------------|
| `valeurTaxe` | Entreprise (modifiable par DGD) | Montant MRU |
| `affectationEntreprise` | Entreprise | `AU_CI` ou `A_PAYER` — proposition |
| `affectation` | DGD (après visa) | Décision finale ; `null` tant que pas de visa |
| `affectationModifieeParDgd` | Calculé | `true` si DGD a changé l'affectation ; `false` si validation ; `null` avant visa |

## Création / mise à jour (entreprise)

`POST /api/utilisations-credit` ou `PUT /api/utilisations-credit/{id}`

```json
{
  "type": "DOUANIER",
  "certificatCreditId": 1,
  "entrepriseId": 1,
  "numeroDeclaration": "DEC-001",
  "lignes": [
    {
      "codeTaxe": "DD",
      "denominationTaxe": "Droit de douane",
      "typeLigne": "GLOBALE",
      "valeurTaxe": 1000,
      "affectation": "AU_CI"
    },
    {
      "codeTaxe": "TVA",
      "denominationTaxe": "TVA",
      "typeLigne": "GLOBALE",
      "valeurTaxe": 200,
      "affectation": "A_PAYER"
    }
  ],
  "brouillon": false
}
```

**Règles :**
- `affectation` sur chaque ligne = proposition entreprise (`AU_CI` / `A_PAYER`).
- **Obligatoire** pour toute ligne avec `valeurTaxe > 0` à la soumission (pas en brouillon si vous laissez vide — recommandé de demander quand même en UI).
- Lignes à `0` : `affectation` optionnelle.

`PATCH` soumission brouillon : même contrôle sur les lignes existantes.

## Visa DGD

`POST /api/utilisations-credit/{id}/visa-dgd` (multipart)

- `decisions` : JSON array
```json
[
  { "ligneId": 1, "affectation": "AU_CI", "valeurTaxe": 1000 },
  { "ligneId": 2, "affectation": "A_PAYER" }
]
```
- Si **`affectation` est omis** sur une ligne avec montant > 0 **et** que l'entreprise a proposé `affectationEntreprise` → le backend **valide** (reprend la proposition).
- Si l'entreprise n'a rien proposé → erreur « Décision manquante ».
- `file` : scan bulletin annoté (optionnel, `BULLETIN_ANNOTE`).

Après visa : `affectation` = décision finale, totaux `totalPrisEnCharge` / `totalAPayer` recalculés.

## UI — entreprise (création / édition)

Sur le tableau du bulletin (dialog création + détail si `BROUILLON` / `DEMANDEE` éditable) :

| Colonne | Contrôle |
|---------|----------|
| Code / libellé taxe | Lecture ou référentiel |
| Valeur | Input nombre |
| **Pris en charge (cordon)** | Radio / toggle → envoyer `AU_CI` |
| **À payer** | Radio / toggle → envoyer `A_PAYER` |

- Une seule affectation par ligne (exclusif AU_CI / A_PAYER).
- Bloquer soumission si une ligne > 0 sans choix.
- Libellés i18n : `tAffectationTaxe('AU_CI')` / `tAffectationTaxe('A_PAYER')`.

## UI — DGD (visa)

Écran / modal **Visa DGD** (`statut` = `DEMANDEE` ou `EN_CONTROLE_DGD`) :

1. Afficher pour chaque ligne :
   - Montant entreprise
   - **Proposition** : badge `affectationEntreprise` (ex. « Proposé : AU_CI »)
2. Colonnes éditables DGD :
   - Montant (optionnel correction `valeurTaxe`)
   - **Décision finale** : select AU_CI / A_PAYER, **pré-rempli** avec `affectationEntreprise`
3. Indication visuelle après visa (lecture seule) :
   - Si `affectationModifieeParDgd === false` → « Validé » (icône check)
   - Si `true` → « Modifié par DGD » (couleur warning)
4. Bouton « Valider le visa » → `visa-dgd` avec `decisions` (envoyer toutes les lignes ; peut reprendre les valeurs pré-remplies = validation sans changement).
5. Upload bulletin annoté (comme avant).

**Workflow inchangé après visa :** chèque entreprise → Trésor → quittances → liquidation DGTCP.

## Migration base

Exécuter : `scripts/add-ligne-bulletin-affectation-entreprise-mysql.sql`

## Enums affichage

- `AU_CI` → via `tAffectationTaxe('AU_CI')` (ex. « Pris en charge par le CI » / AR équivalent)
- `A_PAYER` → `tAffectationTaxe('A_PAYER')`
