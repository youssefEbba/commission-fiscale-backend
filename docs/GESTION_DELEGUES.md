# Gestion des délégués (autorité contractante)

Les délégués sont des utilisateurs de rôle **AUTORITE_UPM** ou **AUTORITE_UEP**, rattachés à la même **autorité contractante** que le compte **AUTORITE_CONTRACTANTE**. Ils peuvent être affectés à **un ou plusieurs marchés** de cette autorité (relation many-to-many via la table `marche_delegue`).

## Fonctionnalités déjà couvertes par le modèle

| Besoin | Comportement | API / mécanisme |
|--------|----------------|-----------------|
| Suspendre l’activité (empêcher connexion et appels API) | Champ `actif` à `false` sur l’utilisateur délégué | `PATCH /api/delegues/{id}/actif?actif=false` (permission `delegue.disable`) |
| Réactiver | `actif=true` | Même endpoint avec `actif=true` |
| Détacher d’un marché | Suppression du lien `marche_delegue` | `DELETE /api/marches/{marcheId}/delegues/{delegueId}` (`marche.manage`) |
| Rattacher à plusieurs marchés | Plusieurs liens possibles pour le même délégué | `POST /api/marches/{marcheId}/delegues` avec `{"delegueId": …}` pour chaque marché, ou **`PUT /api/delegues/{id}/marches`** (remplacement de la liste complète) |

**Important — suspension effective :** dès qu’un compte est désactivé (`actif = false`), le filtre JWT refuse les requêtes authentifiées avec un message **403** (`Compte désactivé ou introuvable`), même si un ancien jeton est encore valide.

## Permissions (rôle AUTORITE_CONTRACTANTE)

| Code | Usage |
|------|--------|
| `delegue.list` | Liste des délégués, détail, marchés d’un délégué |
| `delegue.create` | Création d’un délégué |
| `delegue.update` | Mise à jour profil / e-mail / mot de passe |
| `delegue.disable` | Activer ou désactiver (suspendre) un délégué |
| `marche.manage` | Affectation / retrait sur un marché, synchronisation globale des marchés d’un délégué |

Les permissions sont injectées en base par `DataInitializer` pour le rôle **AUTORITE_CONTRACTANTE**. En production, si le rôle existait déjà, attribuer manuellement **`delegue.update`** à ce rôle si nécessaire.

## API délégués (`/api/delegues`)

Toutes les routes ci-dessous exigent un en-tête `Authorization: Bearer <token>` et le rôle **autorité contractante** (sauf mention contraire côté sécurité métier).

### Liste et détail

- **`GET /api/delegues`** — Liste des délégués de votre autorité (`delegue.list`).
- **`GET /api/delegues/{id}`** — Détail d’un délégué de votre autorité (`delegue.list`).

### Création et mise à jour

- **`POST /api/delegues`** — Corps JSON `CreateDelegueRequest` : `username`, `password`, `role` (`AUTORITE_UPM` | `AUTORITE_UEP`), `nomComplet`, `email` (`delegue.create`).
- **`PATCH /api/delegues/{id}`** — Corps JSON `UpdateDelegueRequest` : au moins un des champs `nomComplet`, `email`, `newPassword` (min. 8 caractères) (`delegue.update`). Le nom d’utilisateur n’est pas modifiable par cette route.
- **`PATCH /api/delegues/{id}/actif?actif=true|false`** — Suspension / réactivation (`delegue.disable`).

### Marchés d’un délégué

- **`GET /api/delegues/{id}/marches`** — Marchés de votre autorité auxquels ce délégué est rattaché (`delegue.list`).
- **`PUT /api/delegues/{id}/marches`** — Remplace **toute** l’affectation marchés ↔ ce délégué par la liste envoyée (`marche.manage`).  
  Corps JSON : `{ "marcheIds": [1, 2, 3] }`.  
  - Liste vide : détache le délégué de **tous** les marchés (dans le périmètre de l’AC).  
  - Chaque `marcheId` doit être un marché dont la convention appartient à la même autorité contractante.

## API marchés (rappel)

Gestion fine **par marché** (même logique métier que ci-dessus) :

- **`PATCH /api/marches/{id}/assign`** — Si `delegueId` est **absent ou null** : retire **tous** les délégués du marché. Si `delegueId` est renseigné : **ajoute** ce délégué au marché **sans** retirer les autres déjà liés (comportement historique).
- **`POST /api/marches/{id}/delegues`** — Ajoute un délégué au marché (sans retirer les autres).
- **`DELETE /api/marches/{id}/delegues/{delegueId}`** — Retire ce délégué de ce marché uniquement.

## Règles métier

- Un délégué ne peut être affecté qu’à des marchés dont la **convention** est liée à l’**autorité contractante** du délégué et du compte qui effectue l’opération.
- Les rôles délégués acceptés pour ces affectations sont **AUTORITE_UPM** et **AUTORITE_UEP** uniquement.
- La visibilité des marchés pour un délégué connecté est filtrée sur les marchés où il figure dans `marche_delegue` (voir `MarcheService.findAll`).

## Exemples rapides

**Suspendre un délégué**

```http
PATCH /api/delegues/5/actif?actif=false
Authorization: Bearer …
```

**Synchroniser les marchés du délégué 5 sur trois marchés**

```http
PUT /api/delegues/5/marches
Authorization: Bearer …
Content-Type: application/json

{ "marcheIds": [10, 11, 12] }
```

**Retirer un délégué d’un seul marché**

```http
DELETE /api/marches/10/delegues/5
Authorization: Bearer …
```
