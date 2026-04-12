# Rejet temporaire (REJET_TEMP) — documentation technique

Vue d’ensemble du mécanisme **tel qu’implémenté** dans le backend SGCI (Spring Boot). Un rejet temporaire permet à une direction de **bloquer provisoirement** le dossier en demandant des **compléments** (motif + liste de types de documents), sans équivaloir à un refus définitif ni à un visa.

---

## 1. Concepts

| Élément | Description |
|--------|-------------|
| **Type de décision** | `DecisionCorrectionType.REJET_TEMP` (réutilisé pour correction, utilisation, certificat). |
| **Statut du rejet** | `RejetTempStatus.OUVERT` → en cours ; `RejetTempStatus.RESOLU` → l’émetteur a clos la phase rejet. |
| **Plusieurs rejets isolés par rôle** | Chaque `POST .../decisions` avec `REJET_TEMP` crée une **nouvelle ligne** (`DecisionCorrection` / `DecisionUtilisationCredit` / `DecisionCertificatCredit`) avec son **id** propre : un même rôle (ex. DGI) peut enchaîner rejet #1, #2, #3 avec motifs et documents distincts. L’AC / l’entreprise répond via **`decisionId`** ciblé (`.../decisions/{decisionId}/rejet-temp/reponses` ou dépôt GED selon le domaine). |
| **Un seul VISA par rôle** | Après au moins un `VISA` enregistré pour ce rôle sur le dossier, plus de nouveau `REJET_TEMP` ni second `VISA` pour ce rôle. |
| **Réponses** | Entités `RejetTempResponse` liées à **une** décision : message (obligatoire), éventuellement pièce jointe selon le flux. |

**Important** : **`PUT .../resolve` ne pose pas de VISA**. Il marque uniquement **le** `REJET_TEMP` ciblé par `decisionId` comme `RESOLU`. Un **VISA** exige un **`POST .../{id}/decisions`** avec `decision: VISA` **lorsqu’il n’existe plus aucun `REJET_TEMP` ouvert pour ce même rôle** sur le dossier.

---

## 2. Où ça s’applique (trois domaines)

| Domaine | Base URL API | Service principal |
|--------|----------------|---------------------|
| Demande de **correction** | `/api/demandes-correction` | `DecisionCorrectionService` |
| **Utilisation** de crédit | `/api/utilisations-credit` | `DecisionUtilisationCreditService` |
| **Certificat** (mise en place) | `/api/certificats-credit` | `DecisionCertificatCreditService` |

Les règles communes (plusieurs `REJET_TEMP` par rôle possibles ; blocage du `VISA` tant qu’il reste au moins un `REJET_TEMP` **ouvert** pour ce rôle ; résolution par le **même** `Role` que celui enregistré sur la décision ciblée) sont dans chaque service.

---

## 3. Endpoints utiles

### 3.1 Correction (`DecisionCorrectionController`)

| Méthode | Chemin | Rôle |
|--------|--------|------|
| `POST` | `/api/demandes-correction/{id}/decisions` | Enregistrer une décision (`VISA` ou `REJET_TEMP` + motif + `documentsDemandes` si rejet temporaire). |
| `POST` | `/api/demandes-correction/decisions/{decisionId}/rejet-temp/reponses` | Ajouter une **réponse texte** (AC / compléments : `correction.offer.upload`, `correction.complement.add`). |
| `PUT` | `/api/demandes-correction/decisions/{decisionId}/resolve` | **Résoudre** le rejet : uniquement le **rôle émetteur** de la décision ; permissions élargies aux directions + AC (voir code). |

Effet métier après résolution : si plus aucun `REJET_TEMP` **ouvert** sur la demande et statut `INCOMPLETE` → repasse en **`RECEVABLE`**.

### 3.2 Utilisation (`DecisionUtilisationCreditController`)

| Méthode | Chemin | Rôle |
|--------|--------|------|
| `POST` | `/api/utilisations-credit/{id}/decisions` | Décision contrôleur (visa / rejet temporaire, etc.). |
| `POST` | `/api/utilisations-credit/decisions/{decisionId}/rejet-temp/reponses` | Réponse (JSON ou multipart avec fichier). |
| `PUT` | `/api/utilisations-credit/decisions/{decisionId}/resolve` | Résolution par le rôle émetteur (`utilisation.*.resolve` selon le profil). |

Effet : si plus de rejet ouvert et utilisation `INCOMPLETE` → **`A_RECONTROLER`**.

### 3.3 Certificat / mise en place (`DecisionCertificatCreditController`)

| Méthode | Chemin | Rôle |
|--------|--------|------|
| `POST` | `/api/certificats-credit/{id}/decisions` | Visa ou rejet temporaire (DGI, DGD, DGTCP). |
| `POST` | `/api/certificats-credit/decisions/{decisionId}/rejet-temp/reponses` | Message de réponse (permissions mise en place / consultation file). |
| `PUT` | `/api/certificats-credit/decisions/{decisionId}/resolve` | Résolution (`mise_en_place.*.resolve`). |

Effet : si plus de rejet ouvert et certificat `INCOMPLETE` → **`A_RECONTROLER`**. Les **trois visas** et absence de rejet ouvert peuvent ensuite déclencher la transition vers **`EN_VALIDATION_PRESIDENT`** (logique `tryAutoTransitionToPresident` **uniquement** lors d’un enregistrement de **VISA**, pas lors du `resolve`).

---

## 4. Règles métier résumées

1. **Création d’un `REJET_TEMP`** : motif obligatoire ; liste `documentsDemandes` (types de documents) obligatoire ; le dossier passe en statut **incomplet** (demande / utilisation / certificat selon le cas).
2. **Tant qu’il existe au moins un `REJET_TEMP` `OUVERT` pour un rôle** sur le dossier, ce rôle **ne peut pas** enregistrer un **VISA** (il doit résoudre chaque rejet concerné via `PUT .../decisions/{decisionId}/resolve`).
3. **`resolve`** : le JWT doit être du **même `Role`** que `decision.role` sur la ligne visée ; cette ligne reste **`REJET_TEMP`** avec `rejetTempStatus = RESOLU` (pas de conversion automatique en `VISA`).
4. **Dépôts de pièces** : `DocumentService` / services documents peuvent exiger un **message** lorsqu’un `REJET_TEMP` ouvert a demandé des compléments (voir implémentations liées aux décisions ouvertes).

---

## 5. Fichiers de référence (code)

- `DecisionCorrectionService`, `DecisionUtilisationCreditService`, `DecisionCertificatCreditService` — `saveDecision`, `resolveRejetTemp`
- `RejetTempResponseService` — enregistrement des réponses
- `DecisionCorrectionType.java`, `RejetTempStatus.java`
- Contrôleurs listés en section 3 — `@PreAuthorize` pour l’intégration front / JWT

Pour toute évolution du workflow, **le code Java fait foi** ; mettre à jour ce document en parallèle.
