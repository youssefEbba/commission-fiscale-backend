# Execution du plan notifications

Ce document execute le plan "qui, par quoi, condition, declencheur" avec une validation directe dans le code source du projet.

## 1) Cartographie finale des emetteurs

| Type notification | Qui est notifie | Par quoi (service) | Declencheur API |
|---|---|---|---|
| `CORRECTION_STATUT_CHANGE` | Utilisateurs de l'entreprise + autorite contractante de la demande | `DemandeCorrectionService.notifyDemandeCorrection(...)` | `PATCH /api/demandes-correction/{id}/statut` |
| `CORRECTION_DECISION` | Utilisateurs de l'entreprise + autorite contractante de la demande | `DecisionCorrectionService.notifyDecision(...)` | `POST /api/demandes-correction/{id}/decisions` |
| `CONVENTION_STATUT_CHANGE` | Utilisateurs de l'autorite contractante | `ConventionService.notifyConvention(...)` | `PATCH /api/conventions/{id}/statut` |
| `REFERENTIEL_STATUT_CHANGE` | Utilisateurs de l'autorite contractante | `ReferentielProjetService.notifyReferentielProjet(...)` | `PATCH /api/referentiels-projet/{id}/statut` |
| `CERTIFICAT_STATUT_CHANGE` (entreprise) | Utilisateurs de l'entreprise du certificat | `CertificatCreditService.notifyCertificat(...)` | `PATCH /api/certificats-credit/{id}/statut` |
| `CERTIFICAT_STATUT_CHANGE` (president) | Utilisateurs role `PRESIDENT` | `DecisionCertificatCreditService.notifyPresidentForValidation(...)` | `POST /api/certificats-credit/{id}/decisions` (apres 3 visas) |
| `UTILISATION_STATUT_CHANGE` (creation) | `DGD` si douanier, `DGTCP` si TVA interieure | `UtilisationCreditService.notifyActorsOnCreation(...)` | `POST /api/utilisations-credit` |
| `UTILISATION_STATUT_CHANGE` (evolution) | Utilisateurs de l'entreprise liee | `UtilisationCreditService.notifyUtilisation(...)` | `PATCH /api/utilisations-credit/{id}/statut`, `POST /{id}/apurement-tva`, `POST /{id}/liquidation-douane` |
| `TRANSFERT_CREDIT` | Utilisateurs role `DGTCP` | `TransfertCreditService.create(...)` | `POST /api/transferts-credit` |
| `SOUS_TRAITANCE` | Utilisateurs role `DGTCP` | `SousTraitanceService.create(...)` | `POST /api/sous-traitances` |

## 2) Conditions metier verifiees

- Les notifications sont emises uniquement si la liste des destinataires est non vide.
- Les notifications suivent les transitions workflow et les controles de role sur les endpoints de statut/decision.
- Cas certificat president: emission seulement si les 3 visas (`DGI`, `DGD`, `DGTCP`) existent et aucun rejet temporaire n'est ouvert.
- Cas utilisation a la creation: le role notifie depend du type (`DOUANIER` -> `DGD`, sinon `DGTCP`).
- Cas evolution utilisation: notification vers l'entreprise apres transitions valides (`APUREE`, `LIQUIDEE`, etc.).

## 3) Couche delivery (REST + WebSocket)

- Lecture REST: `NotificationController` expose
  - `GET /api/notifications`
  - `GET /api/notifications/unread-count`
  - `PATCH /api/notifications/{id}/read`
  - `PATCH /api/notifications/read-all`
- Isolation utilisateur:
  - `markAsRead` utilise `findByIdAndUtilisateurId(...)`, ce qui empeche de marquer la notification d'un autre utilisateur.
  - `markAllAsRead` opere uniquement sur les notifications de l'utilisateur courant.
- Diffusion temps reel:
  - `NotificationService` publie sur `/topic/notifications/user/{utilisateurId}` via `SimpMessagingTemplate.convertAndSend(...)`.
  - Broker active dans `WebSocketConfig` avec endpoint SockJS `/ws` et broker `/topic`.

## 4) Suite de verification manuelle (pret a executer)

1. Se connecter avec `entreprise`, `ac`, `dgd`, `dgtcp`, `president`.
2. Declencher chaque endpoint declencheur de la matrice.
3. Verifier le destinataire cible via:
   - `GET /api/notifications`
   - `GET /api/notifications/unread-count`
4. Verifier la non-reception sur un profil non cible.
5. Verifier ack:
   - `PATCH /api/notifications/{id}/read`
   - `PATCH /api/notifications/read-all`
6. Verifier push temps reel sur abonnement STOMP:
   - destination `/topic/notifications/user/{userId}`
   - endpoint websocket `/ws`.

## 5) Conclusion d'execution

Le plan est implemente et trace dans ce rapport: toutes les notifications du systeme sont reliees a un emetteur, un declencheur API, des conditions metier et des destinataires explicites.
