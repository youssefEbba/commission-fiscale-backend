# Notifications in-app + e-mail (workflows SGCI)

Ce document décrit la couche de notifications **double canal** (base + WebSocket + SMTP) pour les six processus métier cibles.

## Architecture

```
Services métier → WorkflowNotificationHelper → WorkflowNotificationDispatcher
                                              ├→ NotificationService (in-app + STOMP)
                                              └→ EmailService (@Async, Thymeleaf)
```

- **Point d'entrée unique** : `WorkflowNotificationDispatcher.dispatch(WorkflowEventCode, WorkflowNotificationContext)`
- **Catalogue d'événements** : `WorkflowEventCode` (correction, certificat, utilisation douane/TVA, transfert, clôture)
- **Résolution destinataires** : `WorkflowRecipientResolver` (entreprise, AC, rôles commission, exclusion acteur)

## Configuration SMTP (production)

Variables d'environnement (ne jamais committer le mot de passe Gmail) :

| Variable | Description |
|----------|-------------|
| `SPRING_MAIL_PASSWORD` | Mot de passe d'application Gmail |
| `APP_MAIL_ENABLED` | `true` pour activer l'envoi (défaut prod : true) |
| `APP_MAIL_FROM` | Expéditeur (ex. `emine.youbah@gmail.com`) |
| `APP_MAIL_OVERRIDE_RECIPIENT` | Phase test : tous les mails y sont redirigés (ex. `emine.youbah@esen.tn`) |

Extrait `application-prod.properties` :

```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=emine.youbah@gmail.com
spring.mail.password=${SPRING_MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

app.mail.enabled=true
app.mail.from=emine.youbah@gmail.com
app.mail.override-recipient=emine.youbah@esen.tn
```

En **tests d'intégration** (`application-test.properties`) : `app.mail.enabled=false`.

## Phase test — override destinataire

Tant que `app.mail.override-recipient` est renseigné, **tous** les e-mails partent vers cette adresse, quels que soient les destinataires métier résolus. La logique in-app (rôles, entreprise, AC) reste inchangée.

Retirer l'override en production lorsque les adresses `Utilisateur.email` sont fiables.

## Types de notification in-app

Nouveaux types ajoutés :

- `CLOTURE_CERTIFICAT`
- `REJET_TEMP_DECISION`
- `REJET_TEMP_RESOLU`
- `REJET_TEMP_REPONSE`

Chaque notification in-app inclut dans le payload JSON le champ **`eventCode`** (valeur de `WorkflowEventCode`).

WebSocket inchangé : `/topic/notifications/user/{userId}`.

## Matrice des processus couverts

| Processus | Services branchés | Événements clés |
|-----------|-------------------|-----------------|
| Demande de correction | `DemandeCorrectionService`, `DecisionCorrectionService`, `ReclamationDemandeCorrectionService`, `RejetTempResponseService` | soumission, statut, visa, rejet temp/résolu/réponse, réclamation |
| Mise en place certificat | `CertificatCreditService`, `DecisionCertificatCreditService`, `RejetTempResponseService` | soumission, visa, rejet temp, président, ouverture |
| Utilisation douane | `UtilisationCreditService`, `DecisionUtilisationCreditService`, `RejetTempResponseService` | soumission, visa DGD, chèque, trésor, quittances, liquidation, clôture, rejets |
| Utilisation TVA | idem (branche TVA) | soumission, apurement, rejets |
| Transfert de crédit | `TransfertCreditService`, `DocumentTransfertCreditService`, `DecisionTransfertCreditService`, `RejetTempResponseService` | demande, EN_COURS, rejet temp, validation, rejet, annulation |
| Clôture certificat | `ClotureCreditService` | proposition, approbation/rejet président, finalisation |

## Templates e-mail

Un modèle HTML FR par processus sous `src/main/resources/templates/mail/process/` :

- `correction.html`
- `certificat.html`
- `utilisation-douane.html`
- `utilisation-tva.html`
- `transfert.html`
- `cloture.html`

Fallback : `mail/workflow-notification.html`.

Le sujet est préfixé `[SGCI]` ; le corps reprend le message in-app, le numéro de dossier, le statut et le motif le cas échéant.

## Checklist manuelle (extrait)

1. Configurer `SPRING_MAIL_PASSWORD` et `app.mail.override-recipient=emine.youbah@esen.tn`.
2. Soumettre une demande de correction → notification in-app + 1 e-mail (override).
3. Émettre un rejet temporaire → entreprise notifiée in-app + mail.
4. Déposer une réponse (message + pièce) → acteur émetteur notifié.
5. Résoudre le rejet temp → entreprise + commission selon processus.
6. Répéter pour certificat, utilisation douane/TVA, transfert, clôture.
7. Vérifier WebSocket temps réel sur le front.
8. Avant prod : retirer `override-recipient`, vérifier les e-mails utilisateurs en base.

## Tests automatisés

`WorkflowNotificationMailIT` : mock `JavaMailSender`, mail activé avec override, vérifie l'envoi SMTP et la création de notification in-app via le dispatcher.

## Sécurité

- Ne jamais committer le mot de passe d'application Gmail.
- Rotation du mot de passe avant mise en production.
- Échec d'envoi mail logué en WARN ; n'interrompt pas la transaction métier (`@Async`).
