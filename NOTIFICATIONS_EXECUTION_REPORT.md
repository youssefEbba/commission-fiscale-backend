# Execution du plan notifications

Ce document execute le plan "qui, par quoi, condition, declencheur" avec une validation directe dans le code source du projet.

> **Mise à jour (mail + couverture complète)** : les six workflows cibles passent par `WorkflowNotificationDispatcher` (in-app + e-mail). Voir `docs/NOTIFICATIONS_MAIL.md`.

## 1) Cartographie finale des emetteurs (workflows cibles)

| Type notification | Qui est notifie | Par quoi (service) | Declencheur API |
|---|---|---|---|
| `CORRECTION_*` | Entreprise, AC, commission selon événement | `WorkflowNotificationHelper` via `DemandeCorrectionService`, `DecisionCorrectionService`, `ReclamationDemandeCorrectionService`, `RejetTempResponseService` | soumission, statut, décisions, réclamations, réponses rejet temp |
| `CERTIFICAT_*` / rejets temp | Entreprise, DGI/DGD/DGTCP, Président | `CertificatCreditService`, `DecisionCertificatCreditService`, `RejetTempResponseService` | statut, décisions, réponses |
| `UTILISATION_*` / rejets temp | Entreprise, DGD/DGTCP selon type | `UtilisationCreditService`, `DecisionUtilisationCreditService`, `RejetTempResponseService` | statut, décisions, réponses |
| `TRANSFERT_CREDIT` / rejets temp | DGTCP, entreprise, Président selon étape | `TransfertCreditService`, `DocumentTransfertCreditService`, `DecisionTransfertCreditService`, `RejetTempResponseService` | création, pièces, validation, rejet, annulation |
| `CLOTURE_CERTIFICAT` | Président, DGTCP, entreprise | `ClotureCreditService` | proposer, valider/rejeter, finaliser |

Autres processus (hors scope mail) :

| Type notification | Qui est notifie | Par quoi (service) | Declencheur API |
|---|---|---|---|
| `CONVENTION_STATUT_CHANGE` | Utilisateurs de l'autorite contractante | `ConventionService.notifyConvention(...)` | `PATCH /api/conventions/{id}/statut` |
| `REFERENTIEL_STATUT_CHANGE` | Utilisateurs de l'autorite contractante | `ReferentielProjetService.notifyReferentielProjet(...)` | `PATCH /api/referentiels-projet/{id}/statut` |
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

## 5) E-mail (SMTP)

- Configuration : `docs/NOTIFICATIONS_MAIL.md`
- Phase test : `app.mail.override-recipient=emine.youbah@esen.tn`
- Test automatisé : `WorkflowNotificationMailIT`

## 6) Conclusion d'execution

Le plan est implemente et trace dans ce rapport: les six workflows cibles emettent notification in-app **et** e-mail via `WorkflowNotificationDispatcher`, avec payload `eventCode` pour le front.
