# Gestion de la sous-traitance (titulaire, certificats, utilisations)

## Modèle métier

- **Titulaire** : entreprise bénéficiaire du **certificat** (`certificat_credit.entreprise_id`).
- **Sous-traitant** : autre **entreprise** (`sous_traitant_entreprise_id`) autorisée par DGTCP à déposer des **demandes d’utilisation** sur ce certificat.
- Relation **certificat ↔ sous-traitance** : **au plus une ligne** `sous_traitance` par certificat (`OneToOne`). En revanche, la **même entreprise sous-traitante** peut apparaître sur **plusieurs certificats** du même titulaire (une ligne par certificat).

### Statuts (`StatutSousTraitance`)

| Valeur | Signification |
|--------|----------------|
| `DEMANDE` | En attente de décision DGTCP. |
| `EN_COURS` | Valeur réservée / legacy (traitée comme demande en attente côté DGTCP). |
| `AUTORISEE` | Sous-traitant autorisé : peut créer des utilisations sur ce certificat. |
| `REFUSEE` | Refus DGTCP. |
| `SUSPENDUE` | **Titulaire** a suspendu l’autorisation (pause). Plus de nouvelles utilisations sous-traitant ; réactivation possible **sans** repasser par DGTCP. |
| `REVOQUEE` | **Titulaire** a retiré le lien (fin / détachement). Nouvelles utilisations interdites ; pour rétablir un lien il faut une **nouvelle demande** (`POST` sous-traitance) puis validation DGTCP. |

Seul le statut **`AUTORISEE`** permet au sous-traitant de créer des utilisations (`assertSousTraitantEntrepriseAuthorizedOnCertificat`).

---

## Ce que l’entreprise titulaire peut faire

| Action | API | Permission |
|--------|-----|------------|
| Déclarer / mettre à jour une demande de sous-traitance sur un certificat | `POST /api/sous-traitances` ou `POST /api/sous-traitances/onboarding` | `sous_traitance.submit` |
| **Suspendre** l’activité sur un certificat (pause) | `POST /api/sous-traitances/{id}/suspendre-titulaire` | `sous_traitance.submit` |
| **Réactiver** après suspension | `POST /api/sous-traitances/{id}/reactiver-titulaire` | `sous_traitance.submit` |
| **Révoquer / détacher** (fin du lien sur ce certificat) | `POST /api/sous-traitances/{id}/revoquer-titulaire` | `sous_traitance.submit` |

**Plusieurs certificats** : répéter le flux par certificat (une sous-traitance par certificat). Pour lister les sous-traitances du titulaire : `GET /api/sous-traitances` ; filtrer par sous-traitant : `GET /api/sous-traitances?sousTraitantEntrepriseId={id}`.

**Liste des entreprises sous-traitantes déjà rencontrées** sur vos certificats (pour listes déroulantes) :

```http
GET /api/sous-traitances/entreprises-sous-traitantes
Authorization: Bearer …
```

**Sous-traitance liée à un certificat** :

```http
GET /api/sous-traitances/by-certificat/{certificatCreditId}
```

Réponse **404** si aucune ligne n’existe encore pour ce certificat.

### Suspension du **compte utilisateur** sous-traitant

La désactivation globale d’un utilisateur (`actif = false`) relève des comptes avec la permission `user.disable` (administration), pas du titulaire. La **suspension métier** côté certificat se fait via **`SUSPENDUE`** / **`REVOQUEE`**.

---

## DGTCP

- `POST /api/sous-traitances/{id}/autoriser` — uniquement si statut `DEMANDE` ou `EN_COURS`.
- `POST /api/sous-traitances/{id}/refuser` — idem (pas si déjà `AUTORISEE` ; `REFUSEE` est idempotent).

---

## Traçabilité des demandes d’utilisation

Chaque `utilisation_credit` porte l’**entreprise demandeuse** (`entrepriseId` dans le DTO).  
Voir aussi `docs/UTILISATION_SOUS_TRAITANCE.md` pour `demandeurEstSousTraitant`, `certificatTitulaireEntrepriseId`, etc.

### Titulaire : toutes les utilisations sur ses certificats

`GET /api/utilisations-credit` — inclut les demandes saisies par les sous-traitants autorisés.

### Titulaire : uniquement les demandes **émises par des sous-traitants**

```http
GET /api/utilisations-credit?demandeurSousTraitantOnly=true
```

### Titulaire : utilisations **d’un sous-traitant donné** (id entreprise)

```http
GET /api/utilisations-credit?sousTraitantEntrepriseId=42
```

Combinable avec `demandeurSousTraitantOnly=true` (filtre redondant mais cohérent).

### Par certificat

`GET /api/utilisations-credit/by-certificat/{certificatCreditId}` — toutes les utilisations de ce certificat (titulaire ou sous-traitant autorisé selon règles existantes).

---

## Sécurité / périmètre

- Lecture `GET /api/sous-traitances/{id}` : **titulaire** du certificat, **sous-traitant** dont l’entreprise correspond à la ligne, ou rôles « métier » (non `ENTREPRISE` / non `SOUS_TRAITANT`) sans filtre supplémentaire (aligné sur l’existant).
- Les actions **titulaire** (`suspendre`, `reactiver`, `revoquer`) vérifient que l’utilisateur connecté est **ENTREPRISE** et titulaire du certificat lié.

---

## Implémentation front (résumé)

1. **Écran certificat** : appeler `GET .../by-certificat/{certId}` pour afficher statut / sous-traitant ; badges selon `statut`.
2. **Actions titulaire** : boutons Suspendre / Réactiver / Révoquer selon `AUTORISEE` → suspendre ; `SUSPENDUE` → réactiver ; `AUTORISEE` | `SUSPENDUE` | `DEMANDE` → révoquer si besoin (voir règles métier).
3. **Tableau utilisations par sous-traitant** : `GET /api/utilisations-credit?sousTraitantEntrepriseId=…` + colonnes `demandeurEstSousTraitant`, `entrepriseId`, `certificatTitulaireRaisonSociale`.
4. **Choix entreprise sous-traitante** : préférer `GET /api/sous-traitances/entreprises-sous-traitantes` plutôt que `GET /api/utilisateurs/sous-traitants` (cette dernière liste historiquement **toutes** les entreprises du référentiel).

---

## Fichiers associés

- Service : `SousTraitanceService`, `UtilisationCreditService`
- Contrôleurs : `SousTraitanceController`, `UtilisationCreditController`
- Doc complémentaire : `docs/UTILISATION_SOUS_TRAITANCE.md`
