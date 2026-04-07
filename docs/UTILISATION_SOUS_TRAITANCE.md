# Traçabilité des utilisations de crédit (titulaire vs sous-traitant)

## Modèle de données

Chaque ligne `utilisation_credit` porte :

- **`entreprise_id`** : entreprise **demandeuse** de l’utilisation (celle qui saisit la demande). Pour un sous-traitant autorisé, c’est l’entreprise sous-traitante.
- Le **certificat** (`certificat_credit.entreprise_id`) : entreprise **titulaire** du crédit (bénéficiaire initial).

Une demande est **émise par un sous-traitant** lorsque  
`entreprise_id` (demandeur) ≠ `certificat.entreprise_id` (titulaire), sous réserve que la sous-traitance sur ce certificat soit **AUTORISEE** (contrôle à la création).

## Champs exposés dans `UtilisationCreditDto` (JSON)

| Champ | Description |
|--------|-------------|
| `entrepriseId` | Id de l’entreprise demandeuse. |
| `certificatTitulaireEntrepriseId` | Id de l’entreprise titulaire du certificat. |
| `certificatTitulaireRaisonSociale` | Raison sociale du titulaire (affichage). |
| `demandeurEstSousTraitant` | `true` si la demande est portée par une entreprise autre que le titulaire du certificat (cas sous-traitance). |

Le front peut filtrer ou badger les lignes avec `demandeurEstSousTraitant === true` pour un tableau « Demandes sous-traitants ».

## API `GET /api/utilisations-credit`

- **Titulaire** (`ENTREPRISE`) : reçoit **toutes** les utilisations rattachées à certificats dont son entreprise est titulaire, **y compris** celles saisies par des sous-traitants autorisés.
- **Sous-traitant** (`SOUS_TRAITANT`) : reçoit uniquement les utilisations dont **`entreprise_id`** = son entreprise.

**Query optionnelles** (titulaire uniquement pour ces filtres) :

- `demandeurSousTraitantOnly=true` : ne retourne que les lignes où `demandeurEstSousTraitant` est vrai.
- `sousTraitantEntrepriseId={id}` : ne retourne que les demandes dont l’entreprise demandeuse est cet id **et** qui sont des demandes sous-traitant (`demandeurEstSousTraitant`).

Exemples :

```http
GET /api/utilisations-credit?demandeurSousTraitantOnly=true
Authorization: Bearer …
```

```http
GET /api/utilisations-credit?sousTraitantEntrepriseId=42
Authorization: Bearer …
```

Voir aussi `docs/GESTION_SOUS_TRAITANCE.md` (suspendre / révoquer le lien, statuts, listes).

## API `GET /api/utilisations-credit/by-certificat/{certificatCreditId}`

Liste les utilisations pour un certificat donné, avec le même enrichissement DTO.

- **Titulaire** : accès si le certificat appartient à son entreprise.
- **Sous-traitant** : accès si sous-traitance **AUTORISEE** sur ce certificat pour son entreprise (même règle que la création de demande).

## API `GET /api/utilisations-credit/{id}`

Détail d’une utilisation ; contrôle de périmètre aligné sur les règles ci-dessus pour les rôles `ENTREPRISE` / `SOUS_TRAITANT`.

## Création `POST /api/utilisations-credit`

Comportement inchangé côté métier : le corps contient toujours `entrepriseId` (demandeur) et `certificatCreditId`.  
Les rôles **ENTREPRISE** et **SOUS_TRAITANT** sont soumis aux mêmes contrôles (titulaire ou sous-traitant autorisé sur le certificat).

## Permissions (rôle `SOUS_TRAITANT`)

Le seed assigne notamment : soumission utilisation, pièces, soldes, historiques, sous-traitance, liste des comptes sous-traitants — aligné sur le besoin de consultation et de dépôt côté sous-traitant.

---

*Les services internes (DGD, DGTCP, …) continuent de voir l’ensemble des utilisations via les permissions de file d’attente existantes.*
