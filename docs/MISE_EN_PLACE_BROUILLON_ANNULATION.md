# Mise en place (certificat de crédit) — brouillon, envoyée, prise en charge, annulation

Les **demandes de mise en place** sont modélisées par l’entité **certificat de crédit** (`CertificatCredit`), statuts `StatutCertificat`, workflow `CertificatCreditWorkflow`.

## Chaîne de statuts (résumé)

`BROUILLON` → (soumission) → **`ENVOYEE`** → (prise en charge DGI / DGD / DGTCP) → **`EN_CONTROLE`** → … (visas, président, ouverture).

- **`ENVOYEE`** : demande transmise, modifiable par le **déposant** (même `PUT` qu’en brouillon) tant qu’aucun acteur n’a pris en charge.
- **`EN_CONTROLE`** : dès qu’un acteur **prend en charge** la demande (`POST .../prendre-en-charge` ou `PATCH .../statut=EN_CONTROLE` avec les bons rôles). Rattachement GED à ce passage.

## Ce qui est prévu côté API

### Brouillon et envoi

| Action | HTTP | Condition | Détail |
|--------|------|-----------|--------|
| Créer en brouillon | `POST /api/certificats-credit` | `brouillon: true` | Statut `BROUILLON`. |
| Créer sans brouillon | `POST /api/certificats-credit` | `brouillon: false` ou absent | Statut **`ENVOYEE`** (contrôles métier complets). |
| Modifier le contenu | `PUT /api/certificats-credit/{id}` | Statut **`BROUILLON` ou `ENVOYEE`**, rôle déposant (AC / délégué / entreprise) | Même corps (`CreateCertificatCreditRequest`). **Interdit** dès `EN_CONTROLE` et suivants. |
| Soumettre le brouillon | `POST /api/certificats-credit/{id}/soumettre` | `BROUILLON` | Passe en **`ENVOYEE`**, contrôles `assertMiseEnPlaceTrigger`. |
| Prise en charge | `POST /api/certificats-credit/{id}/prendre-en-charge` | Statut `ENVOYEE`, rôle **DGI / DGD / DGTCP** | Passe en **`EN_CONTROLE`**, rattache le certificat au dossier GED. |
| Supprimer | `DELETE /api/certificats-credit/{id}` | `BROUILLON` uniquement | Suppression définitive. |

Permission déposant : `mise_en_place.submit`. Prise en charge : permissions de file `mise_en_place.dgi.queue.view`, `mise_en_place.dgd.queue.view`, `mise_en_place.dgtcp.queue.view`.

### Unicité : une mise en place « active » par demande de correction

Tant qu’une demande de correction est liée, il ne peut exister **qu’un seul** certificat **non** `ANNULE` pour cette demande (création, `PUT`, soumission brouillon). Réponse **409** `CONFLICT` si doublon.

### Annulation

| Action | HTTP |
|--------|------|
| `ANNULE` | `PATCH /api/certificats-credit/{id}/statut?statut=ANNULE` |

Voir règles `assertActorCanTransition` dans `CertificatCreditService`.

### Modification après `EN_CONTROLE`

- Plus de `PUT` « wizard » côté déposant ; montants DGTCP : `PATCH /api/certificats-credit/{id}/montants`.

### Non prévu

- Retour en `BROUILLON` après `EN_CONTROLE`.

## Comparaison rapide avec la demande de correction

| Sujet | Demande de correction | Mise en place (certificat) |
|--------|------------------------|----------------------------|
| Brouillon | `brouillon: true` | `brouillon: true` |
| Édition déposant | `PUT` si `BROUILLON` / adapté | `PUT` si **`BROUILLON` ou `ENVOYEE`** |
| Après envoi | `RECUE`… | **`ENVOYEE`** puis prise en charge → **`EN_CONTROLE`** |

## Fichiers utiles (backend)

- `CertificatCreditController`, `CertificatCreditService`, `CertificatCreditWorkflow`, `StatutCertificat`

## Indications front

- Afficher le statut **`ENVOYEE`** et proposer la **prise en charge** côté file (DGI / DGD / DGTCP) : `POST /api/certificats-credit/{id}/prendre-en-charge`.
- Le **`PUT`** de modification n’est possible qu’en **`BROUILLON`** ou **`ENVOYEE`** ; désactiver l’édition dès **`EN_CONTROLE`**.
- Création directe sans brouillon : réponse avec statut **`ENVOYEE`** (plus **`EN_CONTROLE`** immédiat) — enchaîner avec prise en charge pour les écrans acteurs.
- Gérer **409** en cas de doublon sur la même demande de correction.
