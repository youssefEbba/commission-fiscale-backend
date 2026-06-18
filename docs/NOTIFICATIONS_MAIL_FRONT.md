# Notifications front — complément e-mail

Les notifications in-app et WebSocket restent le canal principal temps réel. Depuis l'implémentation mail :

- Chaque payload notification contient **`eventCode`** (`WorkflowEventCode`) en plus du `NotificationType` existant.
- Les types `REJET_TEMP_*` et `CLOTURE_CERTIFICAT` peuvent apparaître côté client.
- Endpoint WebSocket inchangé : `/topic/notifications/user/{userId}`.
- Les utilisateurs recevront aussi un e-mail (adresse `Utilisateur.email`) lorsque `app.mail.override-recipient` sera désactivé en production.

Voir `docs/NOTIFICATIONS_MAIL.md` pour la matrice complète par processus.

**Navigation au clic (deep-link)** : toutes les notifications workflow incluent `payload.redirectPath`. Guide front dédié : [NOTIFICATIONS_NAVIGATION_FRONT.md](./NOTIFICATIONS_NAVIGATION_FRONT.md).

**Rollback stockage (503)** : si une action combinée (upload + changement de statut) échoue sur MinIO, la transaction est annulée — **aucune** notification in-app ni e-mail pour cette tentative. Voir [UPLOAD_STORAGE_FAIL_FAST_FRONT.md](./UPLOAD_STORAGE_FAIL_FAST_FRONT.md).
