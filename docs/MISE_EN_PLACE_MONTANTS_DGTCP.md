# Mise en place — renseignement des montants par le DGTCP

Après la **prise en charge** (`ENVOYEE` → `EN_CONTROLE`), le **DGTCP** saisit les montants agrégés du certificat de crédit et, le cas échéant, les lignes du récapitulatif fiscal. Côté service, seul un utilisateur au **rôle `DGTCP`** peut appeler cette opération.

## Endpoint

| Élément | Valeur |
|--------|--------|
| Méthode | `PATCH` |
| URL | `/api/certificats-credit/{id}/montants` |
| Auth | Bearer JWT |
| Permissions | `mise_en_place.dgtcp.open_credit` **ou** `mise_en_place.dgtcp.validate` |

Réponse : `CertificatCreditDto` (corps JSON identique aux autres lectures du certificat).

## Corps de la requête (`UpdateCertificatCreditMontantsRequest`)

### Obligatoires

| Champ | Type | Description |
|-------|------|-------------|
| `montantCordon` | `number` (décimal) | Montant « cordon » (crédit d’impôt extérieur agrégé). |
| `montantTVAInterieure` | `number` (décimal) | Montant TVA intérieure (agrégé). |

Les deux champs sont **requis** (`@NotNull`) : omission → erreur de validation **400**.

### Optionnels — récapitulatif fiscal (lignes du tableau)

S’ils sont fournis, le backend vérifie la **cohérence** avec les montants agrégés (voir ci‑dessous).

| Champ | Rôle (notation doc métier) |
|--------|----------------------------|
| `valeurDouaneFournitures` | (a) |
| `droitsEtTaxesDouaneHorsTva` | (b) |
| `tvaImportationDouane` (saisie) | (d) — à la sauvegarde, sert à initialiser **accord** et **restant** |
| `montantMarcheHt` | (c) |
| `tvaCollecteeTravaux` | (g) |

### Exemple minimal

```json
{
  "montantCordon": 4000000,
  "montantTVAInterieure": 2000000
}
```

### Exemple avec récapitulatif

```json
{
  "montantCordon": 4000000,
  "montantTVAInterieure": 2000000,
  "droitsEtTaxesDouaneHorsTva": 2500000,
  "tvaImportationDouane": 1500000,
  "tvaCollecteeTravaux": 3500000
}
```

## Règles de cohérence (récapitulatif)

Si les lignes du récapitulatif sont renseignées de façon à permettre le contrôle :

1. **Crédit extérieur** : si **(b)** et **(d)** sont présents avec `montantCordon`, alors  
   `montantCordon` doit être **approximativement égal** à **b + d** (tolérance **1 MRU**).
2. **TVA intérieure nette** : si **(g)** et **(d)** sont présents avec `montantTVAInterieure`, alors  
   `montantTVAInterieure` doit être **approximativement égal** à **g − d** (même tolérance).

En cas d’écart : **400** `BUSINESS_RULE_VIOLATION` avec message explicite côté API.

## Soldes

Tant que le certificat **n’est pas** au statut `OUVERT`, si les soldes cordon / TVA sont absents ou à zéro, ils sont **alignés** sur les montants saisis pour éviter une incohérence avec la suite du circuit. Une fois le crédit **ouvert**, les soldes ne sont plus recalculés automatiquement par ce patch (gestion par les utilisations).

## Erreurs courantes

| Code / cas | Cause |
|------------|--------|
| **403** | Utilisateur authentifié mais pas **DGTCP**, ou permissions manquantes. |
| **404** | Certificat `id` inexistant. |
| **400** | Validation bean (montants null) ou récapitulatif incohérent avec les montants. |

## Fichiers utiles (backend)

- `CertificatCreditController#updateMontants`
- `CertificatCreditService#updateMontants`
- DTO : `UpdateCertificatCreditMontantsRequest`

## Lien avec le flux global

Voir aussi `docs/MISE_EN_PLACE_BROUILLON_ANNULATION.md` (chaîne `ENVOYEE` → prise en charge → `EN_CONTROLE`, puis visas et validation président).
