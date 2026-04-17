# API Convention, marché et correction — guide front (2026)

Ce document décrit les champs JSON, paramètres de requête, codes d’erreur et comportements récents pour l’intégration front.

## Conventions (`/api/conventions`)

### Liste avec recherche

- **GET** `/api/conventions?q=...` (paramètre optionnel)
- **q** : recherche insensible à la casse sur **référence**, **intitulé** et **projectReference** (sous-chaîne).
- Sans `q`, comportement inchangé (liste selon le rôle).

### Création / lecture

| Champ JSON | Description |
|------------|-------------|
| `projectReference` | Référence projet (optionnel), distincte de `reference`. |
| `creeParAutoriteContractanteId` / `creeParAutoriteContractanteNom` | Autorité à l’origine de la création (souvent l’AC de l’utilisateur créateur). |
| Autres champs | Inchangés (`reference`, `intitule`, montants, `autoriteContractanteId`, etc.). |

### Notifications

- À la **création** d’une convention : notification aux utilisateurs de l’AC concernée **et** aux rôles commission **PRESIDENT**, **DGD**, **DGTCP**, **DGI**, **DGB** (type `CONVENTION_STATUT_CHANGE`, payload avec `event: CONVENTION_CREATED`).
- Changement de **statut** : mêmes destinataires étendus (plus seulement l’AC).

---

## Marchés (`/api/marches`)

### Montant

- Le montant contractuel est exposé en **HT** sous le nom JSON **`montantContratHt`**.
- **`montantContratTtc`** est encore accepté en **entrée** (alias Jackson) pour compatibilité ; privilégier `montantContratHt` en affichage et envoi.

### Intitulé

- **`intitule`** : libellé du marché (liste, détail, création, mise à jour).

### Date de signature

- **`dateSignature`** : **optionnelle** (peut être `null`).

### Liste avec recherche

- **GET** `/api/marches?q=...`
- **q** : recherche sur **numéro de marché** et **intitulé** (sous-chaîne, insensible à la casse).
- Pour les utilisateurs rattachés à une AC (AC principale ou délégués), la recherche est d’abord limitée aux marchés de cette AC, puis filtrée par les règles d’accès habituelles.

### Erreurs structurées (attribution délégué, etc.)

Les réponses d’erreur suivent le corps uniforme `ErrorResponse` :

```json
{
  "timestamp": "...",
  "status": 403,
  "code": "ACCESS_DENIED",
  "message": "...",
  "error": "...",
  "details": { "marcheId": 1, "code": "MARCHE_HORS_PERIMETRE_AC" }
}
```

- **Attribution** (`PATCH /api/marches/{id}/assign`, etc.) : en cas de marché hors périmètre AC, `details` contient `marcheId` et `code: MARCHE_HORS_PERIMETRE_AC`.
- **Création de marché lié à une correction** : conflit si un marché existe déjà pour la même demande — `details` peut contenir `demandeCorrectionId`, `code: MARCHE_DEJA_LIE_CORRECTION`.

Afficher côté front : lire `message` + optionnellement `details` pour toasts / i18n.

---

## Demandes de correction (`/api/demandes-correction`)

### Création

- **POST** `/api/demandes-correction` — le back reçoit l’utilisateur connecté pour les notifications.
- Notification **RECUE** aux mêmes familles que pour les changements de statut (entreprise + AC + PRESIDENT + DGD/DGTCP/DGI/DGB).

### Marché lié / annulation

- Un **marché** ne peut être lié qu’à **une seule demande active** à la fois.
- Si le marché était lié à une demande **annulée** (`ANNULEE`), le lien est **rompu** automatiquement lors d’une **nouvelle** demande : l’ancienne demande conserve **`marcheIdTrace`** (id du marché au moment du détachement).
- Lors du passage d’une demande au statut **ANNULEE**, le marché est **détaché** et **`marcheIdTrace`** est renseigné sur la demande.

### Objet `marche` dans la réponse

- Inclut notamment : `conventionId`, `numeroMarche`, **`intitule`**, `dateSignature`, **`montantContratHt`** (plus `montantContratTtc` en entrée seulement via DTO marché générique).

### Conflit « marché déjà utilisé »

- HTTP **409**, `code`: **`MARCHE_DEMANDE_ACTIVE`** (également répété dans `details.code`).
- `details` : `marcheId`, `demandeCorrectionId` de la demande bloquante.

### Permissions agents — offre corrigée

- Le rôle **DGB** dispose notamment de **`correction.offer.view`** (aligné sur les autres agents pour la consultation de l’offre corrigée lorsque les règles métier l’exigent).

---

## Utilisateurs (`/api/utilisateurs` ou routes admin concernées)

- Listes **`findAll`** et comptes **en attente d’activation** : ordre **du plus récent au plus ancien** (par **id** décroissant).

---

## Récap erreurs HTTP + JSON

| HTTP | Champ `code` API | Usage front |
|------|------------------|-------------|
| 400 | `BUSINESS_RULE_VIOLATION`, etc. | Message + validation |
| 403 | `ACCESS_DENIED`, `ROLE_FORBIDDEN` | `details` si présent |
| 409 | `CONFLICT`, `MARCHE_DEMANDE_ACTIVE` | Marché / correction / doublons |

Toujours parser **`message`** et optionnellement **`details`** (objet libre) pour les toasts et le diagnostic.
