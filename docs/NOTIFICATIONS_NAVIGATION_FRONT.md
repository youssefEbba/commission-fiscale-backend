# Navigation notifications workflow (deep-link front)

Guide pour le clic sur une notification in-app (cloche) ou reçue via WebSocket. Complète [NOTIFICATIONS_MAIL_FRONT.md](./NOTIFICATIONS_MAIL_FRONT.md) (canal e-mail) et [DEMANDE_EXPLICATION_FRONT.md](./DEMANDE_EXPLICATION_FRONT.md) (demandes d'explication, hors workflow standard).

---

## 1. Structure `NotificationDto`

| Champ racine | Description |
|--------------|-------------|
| `id` | Identifiant notification |
| `type` | `NotificationType` (ex. `REJET_TEMP_DECISION`, `STATUT_CHANGE`, `DEMANDE_EXPLICATION`) |
| `entityType` | Entité métier liée (ex. `DemandeCorrection`, `CertificatCredit`) |
| `entityId` | Id de cette entité |
| `message` | Texte affiché dans la liste |
| `payload` | Objet JSON enrichi (voir ci-dessous) |
| `read` | Lu / non lu |
| `dateCreation` | Horodatage |

Le front doit traiter **`payload`** pour la navigation et le contexte métier ; les champs racine servent surtout à l'affichage et au fallback.

---

## 2. Champs payload (workflows)

| Champ | Présence | Description |
|-------|----------|-------------|
| `eventCode` | Toujours | Code catalogue `WorkflowEventCode` (ex. `CORRECTION_REJET_TEMP`, `CERTIFICAT_REJET_TEMP`) |
| `redirectPath` | **Toutes** les notifications workflow (nouvelles) | Chemin React prêt à l'emploi, ex. `/dashboard/demandes/42` |
| `entityType` | Miroir explicite | Même sémantique que la racine |
| `entityId` | Miroir explicite | Même sémantique que la racine |
| `dossierLabel` | Souvent | Numéro lisible (ex. `DC-2025-001`, `CI-DEMO-…`) |
| `decisionId` | Événements `*_REJET_TEMP*` | Id de la décision commission à cibler (bandeau compléments, scroll `#rejet-temp-{decisionId}`) |
| `documentsDemandes` | Émission `REJET_TEMP` | Codes GED demandés (ex. `CONTRAT`, `OFFRE_FISCALE_CORRIGEE`) |
| `statut` / `newStatus` | Changements de statut | Nouveau statut métier |
| `oldStatus` | Changements de statut | Ancien statut (si connu) |
| `motif` / `motifRejet` | Rejets | Motif saisi par la commission |
| `acteurRole` / `acteurUserId` | Souvent | Rôle et utilisateur à l'origine de l'action |

`extraPayload` métier peut ajouter d'autres clés (`codeDocument` sur réponse REJET_TEMP, etc.).

---

## 3. Règle d'or

**Au clic, naviguer via `payload.redirectPath`.**

Ne pas déduire la route uniquement depuis `entityId` racine si `entityType` ne correspond pas à l'écran affiché (ex. notification `DemandeExplication` : `entityId` = id explication, `redirectPath` = fiche dossier parent). Voir [DEMANDE_EXPLICATION_FRONT.md](./DEMANDE_EXPLICATION_FRONT.md).

Pour les **notifications workflow** émises depuis la version actuelle, `redirectPath` est systématique. Les **anciennes** notifications en base peuvent ne pas l'avoir : utiliser le fallback ci-dessous.

### Chemins backend (`NotificationNavigationHelper`)

| `entityType` | `redirectPath` |
|--------------|----------------|
| `DemandeCorrection` | `/dashboard/demandes/{entityId}` |
| `CertificatCredit` | `/dashboard/certificats/{entityId}` |
| `UtilisationCredit` | `/dashboard/utilisations/{entityId}` |
| `TransfertCredit` | `/dashboard/transferts/{entityId}` |
| `ClotureCredit` | `/dashboard/certificats/{certificatCreditId}` *(pas l'id clôture)* |

---

## 4. Handler TypeScript modèle

```typescript
interface NotificationDto {
  id: number;
  type: string;
  entityType?: string;
  entityId?: number;
  message?: string;
  payload?: Record<string, unknown>;
  read?: boolean;
  dateCreation?: string;
}

function resolveNotificationLink(n: NotificationDto): string {
  const p = n.payload ?? {};
  if (typeof p.redirectPath === "string" && p.redirectPath.startsWith("/")) {
    return p.redirectPath;
  }
  switch (n.entityType) {
    case "DemandeCorrection":
      return `/dashboard/demandes/${n.entityId}`;
    case "CertificatCredit":
      return `/dashboard/certificats/${n.entityId}`;
    case "UtilisationCredit":
      return `/dashboard/utilisations/${n.entityId}`;
    case "TransfertCredit":
      return `/dashboard/transferts/${n.entityId}`;
    default:
      return "/dashboard";
  }
}

function onNotificationClick(n: NotificationDto) {
  navigate(resolveNotificationLink(n));
}
```

---

## 5. REJET_TEMP — UX après navigation

Pour les `eventCode` se terminant par `_REJET_TEMP` (émission, pas réponse/résolu) :

1. Naviguer avec `redirectPath`.
2. Si `documentsDemandes` est non vide : afficher un bandeau « Compléments demandés » listant les codes (libellés GED).
3. Optionnel : ancrer ou scroller vers `#rejet-temp-{decisionId}` si la fiche dossier expose cette zone.

Guides processus détaillés :

- Certificat : [MISE_EN_PLACE_REJET_TEMP_FRONT.md](./MISE_EN_PLACE_REJET_TEMP_FRONT.md)
- Vue technique globale : [REJET_TEMPORAIRE.md](./REJET_TEMPORAIRE.md)

---

## 6. Matrice `eventCode` → écran

Tous les chemins ci-dessous sont aussi disponibles dans `payload.redirectPath`.

### Correction (`DemandeCorrection`)

| `eventCode` | Écran |
|-------------|-------|
| `CORRECTION_SOUMISE`, `CORRECTION_STATUT_CHANGE`, `CORRECTION_VISA`, `CORRECTION_REJET_*`, `CORRECTION_ADOPTEE`, `CORRECTION_RECLAMATION_*` | `/dashboard/demandes/{id}` |

### Certificat mise en place (`CertificatCredit`)

| `eventCode` | Écran |
|-------------|-------|
| `CERTIFICAT_*` | `/dashboard/certificats/{id}` |

### Utilisation douane / TVA (`UtilisationCredit`)

| `eventCode` | Écran |
|-------------|-------|
| `UTIL_DOUANE_*`, `UTIL_TVA_*` | `/dashboard/utilisations/{id}` |

### Transfert (`TransfertCredit`)

| `eventCode` | Écran |
|-------------|-------|
| `TRANSFERT_*` | `/dashboard/transferts/{id}` |

### Clôture (`ClotureCredit`)

| `eventCode` | Écran |
|-------------|-------|
| `CLOTURE_*` | `/dashboard/certificats/{certificatCreditId}` |

---

## 7. WebSocket et REST

Même structure `NotificationDto` et même `payload` :

- **WebSocket** : abonnement `/topic/notifications/user/{userId}` (JWT STOMP)
- **REST** : `GET /api/notifications` (liste paginée / filtrée selon implémentation front)

Après clic, marquer lu via l'API existante (`PATCH` / `PUT` selon contrat front).

---

## 8. Hors scope de ce document

| Type | Documentation |
|------|----------------|
| `DEMANDE_EXPLICATION` | [DEMANDE_EXPLICATION_FRONT.md](./DEMANDE_EXPLICATION_FRONT.md) |
| `PASSWORD_RESET_*` | Flux auth / admin |
| E-mail HTML | [NOTIFICATIONS_MAIL.md](./NOTIFICATIONS_MAIL.md) |

---

## 9. Exemple payload REJET_TEMP (correction)

```json
{
  "eventCode": "CORRECTION_REJET_TEMP",
  "entityType": "DemandeCorrection",
  "entityId": 15,
  "redirectPath": "/dashboard/demandes/15",
  "dossierLabel": "DC-TEST-EXPLICATION",
  "newStatus": "INCOMPLETE",
  "statut": "INCOMPLETE",
  "motif": "Compléments correction",
  "motifRejet": "Compléments correction",
  "decisionId": 8,
  "documentsDemandes": ["OFFRE_FISCALE_CORRIGEE"],
  "acteurRole": "DGD",
  "acteurUserId": 3
}
```
