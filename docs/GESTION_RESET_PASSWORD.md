# Gestion de la réinitialisation de mot de passe

Flux **mot de passe oublié** avec validation administrateur et envoi automatique du nouveau mot de passe par e-mail.

## Prérequis

- L'utilisateur doit avoir une adresse **`email`** renseignée sur son compte `Utilisateur`.
- Le compte doit être **actif** (`actif=true`).
- La configuration SMTP doit être active (`app.mail.enabled=true`). Voir [NOTIFICATIONS_MAIL.md](NOTIFICATIONS_MAIL.md).

## Permissions

| Code | Rôles (seed) | Usage |
|------|--------------|-------|
| `user.reset` | `ADMIN_SI`, `DGI`, `PRESIDENT` | Lister, approuver et refuser les demandes |

## Flux utilisateur (public, sans JWT)

1. **Vérifier l'e-mail** — `POST /api/auth/password-reset/check-email`
   ```json
   { "email": "user@example.com" }
   ```
   Réponse : `{ "exists": true }` si au moins un compte actif possède cet e-mail.

2. **Soumettre la demande** — `POST /api/auth/password-reset/request`
   ```json
   { "email": "user@example.com" }
   ```
   - Réponse `202` avec message générique (même si l'e-mail n'existe pas, pour limiter la fuite d'information à cette étape).
   - `409` si une demande `EN_ATTENTE` existe déjà pour ce compte.
   - `409` si plusieurs comptes actifs partagent le même e-mail.

3. Les administrateurs (`user.reset`) reçoivent une **notification in-app** (`PASSWORD_RESET_REQUEST`).

## Flux administrateur

Base : `/api/utilisateurs/password-reset-requests`

| Action | Endpoint | Effet |
|--------|----------|-------|
| Lister | `GET ?statut=EN_ATTENTE` | File d'attente |
| Détail | `GET /{id}` | Informations demande + utilisateur |
| Approuver | `PATCH /{id}/approve` | Génère mot de passe temporaire (12 car.), hash BCrypt, e-mail à l'utilisateur |
| Refuser | `PATCH /{id}/reject` | Body `{ "motif": "..." }` optionnel, e-mail de refus |

Après approbation ou refus, l'utilisateur reçoit une notification in-app `PASSWORD_RESET_TRAITEE`.

## Modèle de données

Table `demande_reset_password` :

- `utilisateur_id`, `email`, `statut` (`EN_ATTENTE`, `APPROUVEE`, `REFUSEE`)
- `date_creation`, `date_traitement`, `traite_par_id`, `motif_refus`

Script MySQL : [`scripts/add-demande-reset-password-mysql.sql`](../scripts/add-demande-reset-password-mysql.sql)

## Sécurité

- L'endpoint `check-email` permet de tester si un e-mail est enregistré (choix UX).
- Le mot de passe temporaire est envoyé **en clair** par e-mail (exigence métier) ; prévoir un changement de mot de passe self-service en évolution future.
- Pas de rate limiting dans cette version.

## Tests

- `PasswordResetIT` — flux API complet
- `PasswordResetMailIT` — envoi SMTP mocké
