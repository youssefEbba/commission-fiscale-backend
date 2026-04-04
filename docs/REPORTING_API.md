# API Reporting (backend SGCI)

Documentation pour brancher le front sur les agrégats « dashboard ». Fond fonctionnel : [REPORTING_PROPOSITION.md](REPORTING_PROPOSITION.md).

**Authentification** : en-tête `Authorization: Bearer <JWT>` (même mécanisme que le reste de `/api/**`).

**Autorisation** : autorité Spring `reporting.view` (permission en base, assignée par défaut au **Président**, **ADMIN_SI**, **DGD**, **DGTCP**, **DGI**, **DGB** via `DataInitializer`). Sans cette permission, les appels renvoient **403**.

**Périmètre métier** : les **totaux et répartitions sont filtrés côté serveur** selon le rôle. Les paramètres `autoriteContractanteId` / `entrepriseId` ne sont pris en compte que pour les rôles « nationaux ».

---

## Rôles nationaux (filtres optionnels)

Rôles : `PRESIDENT`, `ADMIN_SI`, `DGD`, `DGTCP`, `DGI`, `DGB`.

- Sans paramètre de filtre : vue **nationale** (toutes les entités dans la fenêtre de dates).
- Avec `autoriteContractanteId` et/ou `entrepriseId` : restriction supplémentaire.
- Champ de réponse `filtersApplied` : `true` si au moins un de ces deux paramètres a été passé (et appliqué).

## Autres rôles (périmètre fixe)

| Rôle | Périmètre |
|------|-----------|
| `ENTREPRISE`, `SOUS_TRAITANT` | Données liées à `entreprise_id` de l’utilisateur (demandes, certificats, utilisations, conventions/référentiels atteints via les demandes). |
| `AUTORITE_CONTRACTANTE` | `autorite_contractante_id` de l’utilisateur ; pour les délégués voir ci‑dessous. |
| `AUTORITE_UPM`, `AUTORITE_UEP` | Même AC + uniquement les dossiers dont le marché associe l’utilisateur comme délégué (`marche_delegue`). |

Les filtres `autoriteContractanteId` / `entrepriseId` passés en query sont **ignorés** pour ces profils (éviter l’élévation de privilèges).

---

## Fenêtre temporelle

Query params optionnels :

- `from`, `to` : `Instant` ISO‑8601 (ex. `2025-01-01T00:00:00Z`).
- Si **les deux sont absents** : défaut `to = maintenant`, `from = now - 365 jours`.
- Si un seul est fourni : l’autre reste à sa valeur par défaut côté serveur (`from` seul → `to = maintenant` ; `to` seul → `from = to - 365 j`).

**Champs de date utilisés par agrégat** :

| Agrégat | Champ |
|---------|--------|
| Demandes, séries temporelles | `coalesce(dateDepot, dateCreation)` |
| Certificats (soldes / volumes) | `dateEmission` |
| Utilisations | `dateDemande` |
| Conventions | `dateCreation` |
| Référentiels | `dateDepot` |
| Audit (bloc national uniquement) | `timestamp` |

---

## `GET /api/reporting/summary`

### Query params

| Param | Type | Description |
|-------|------|-------------|
| `from` | `Instant` | Début (optionnel) |
| `to` | `Instant` | Fin (optionnel) |
| `autoriteContractanteId` | `long` | Rôles nationaux uniquement |
| `entrepriseId` | `long` | Rôles nationaux uniquement |

### Réponse (`application/json`)

Structure `ReportingSummaryDto` :

- `demandes` (`ReportingDemandeStatsDto`)
  - `byStatut` : liste `{ key, count }` — `key` = nom d’énum `StatutDemande`
  - `total`
  - `tauxAdoptionPct` : **(ADOPTEE + NOTIFIEE) / (total − ANNULEE)** en % ; `tauxRejetPct` : **REJETEE / (total − ANNULEE)** (null si dénominateur 0)
- `certificatsByStatut`, `certificatsTotal`, `certificatsEnValidationPresident`
- `utilisationsByStatut`, `utilisationsByType` — `key` = `StatutUtilisation` / `TypeUtilisation`
- `utilisationsTotal`
- `conventionsByStatut` — `StatutConvention`
- `referentielsByStatut` — `StatutReferentielProjet`
- `marchesByStatut` — `StatutMarche` (marchés liés à une demande dans le périmètre + fenêtre sur la demande)
- `transfertsTotal`, `sousTraitancesTotal` : certificats ayant respectivement un `TransfertCredit` / `SousTraitance` non nul (mêmes filtres certificat)
- `audit` (`ReportingAuditStatsDto`) : **rempli uniquement pour les rôles nationaux** ; sinon `totalActions = 0` et listes vides
  - `byAction` : `AuditAction`
  - `topEntityTypes` : jusqu’à 10 types d’entité les plus journalisés
  - `totalActions`
- `certificatFinancials` (`CertificatFinancialTotalsDto`) : sommes `montantCordon`, `montantTVAInterieure`, `soldeCordon`, `soldeTVA` et `certificatCount` sur le périmètre temporel + métier des certificats
- `filtersApplied` : voir ci‑dessus

Les montants sont des `number` (décimaux JSON) ; les enums sont sérialisés en **chaînes** (noms Java).

---

## `GET /api/reporting/timeseries/demandes`

Même query params que `summary`.

### Réponse

Tableau de `TimeSeriesPointDto` :

- `period` : chaîne `YYYY-MM` (mois civil UTC / calendrier selon la base)
- `count` : nombre de demandes dont `coalesce(dateDepot, dateCreation)` tombe dans ce mois **et** dans `[from, to]` **et** le périmètre métier

Tri par `period` croissant.

---

## Exemple (Présidence, mois courants)

```http
GET /api/reporting/summary?from=2025-01-01T00:00:00Z&to=2026-01-01T00:00:00Z
Authorization: Bearer …
```

```http
GET /api/reporting/timeseries/demandes?from=2025-01-01T00:00:00Z&to=2025-12-31T23:59:59Z&autoriteContractanteId=3
Authorization: Bearer …
```

---

## Écarts vs proposition produit

- Pas encore d’agrégats **délais moyens** ni de **sommes d’utilisations** (autres que via certificats) : à ajouter si besoin.
- Les certificats **sans** `dateEmission` dans l’intervalle n’entrent pas dans les volumes / financiers certificat.
- Permission dédiée `reporting.view` : non requise pour l’instant (contrôle = JWT + filtrage métier). Vous pouvez l’ajouter en base et annoter le contrôleur si la politique RBAC le demande.
