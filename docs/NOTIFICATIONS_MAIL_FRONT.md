# Notifications front — complément e-mail

Les notifications in-app et WebSocket restent le canal principal temps réel. Depuis l'implémentation mail :

- Chaque payload notification contient **`eventCode`** (`WorkflowEventCode`) en plus du `NotificationType` existant.
- Les types `REJET_TEMP_*` et `CLOTURE_CERTIFICAT` peuvent apparaître côté client.
- Endpoint WebSocket inchangé : `/topic/notifications/user/{userId}`.
- Les utilisateurs recevront aussi un e-mail (adresse `Utilisateur.email`) lorsque `app.mail.override-recipient` sera désactivé en production.

Voir `docs/NOTIFICATIONS_MAIL.md` pour la matrice complète par processus.
