# Demande d'explication (discussion commission) — guide front

Fil de discussion **interne à la commission** (DGD, DGTCP, DGI, DGB, Président) sur un dossier **correction**, **certificat** (mise en place) ou **utilisation**. Ce mécanisme **ne modifie pas** le statut métier du dossier et **ne bloque pas** le VISA.

> Comparaison avec le rejet temporaire : voir [REJET_TEMPORAIRE.md](REJET_TEMPORAIRE.md#6-comparaison-avec-la-demande-dexplication).

---

## 1. API REST

**Base** : `/api/demandes-explication`  
**Auth** : JWT Bearer (mêmes comptes commission que le reste de SGCI).

| Méthode | Chemin | Permission | Description |
|--------|--------|------------|-------------|
| `GET` | `?contexte={CORRECTION\|CERTIFICAT\|UTILISATION}&dossierId={id}` | `demande.explication.view` | Liste des fils du dossier (messages inclus) |
| `POST` | `/` | `demande.explication.create` | Ouvrir un fil |
| `POST` | `/{id}/messages` | `demande.explication.reply` | Répondre (fil `OUVERTE` uniquement) |
| `PUT` | `/{id}/fermer` | `demande.explication.close` | Fermer (auteur du fil ou Président) |

**Codes d'erreur** : `403` entreprise / AC / commission relais ; `400` dossier en statut non éligible ou fil fermé.

### 1.1 Ouvrir un fil (`POST /`)

```json
{
  "contexte": "CORRECTION",
  "dossierId": 123,
  "roleDestinataire": "DGI",
  "message": "Merci de préciser le calcul de la ligne (b)."
}
```

- `contexte` : `CORRECTION` | `CERTIFICAT` | `UTILISATION`
- `dossierId` : id de la **demande de correction**, du **certificat** ou de l'**utilisation** selon le contexte
- `roleDestinataire` : `DGD` | `DGTCP` | `DGI` | `DGB` | `PRESIDENT` (rôle visé ; **tous** les membres peuvent lire et répondre)
- `message` : question d'ouverture (max 2000 caractères)

Réponse `201` : `DemandeExplicationDto` (`statut` = `OUVERTE`, `messageInitial`, pas de message dans `messages` tant qu'il n'y a pas de réponse).

### 1.2 Répondre (`POST /{id}/messages`)

```json
{ "message": "Le détail figure en annexe DGD." }
```

### 1.3 Liste (`GET`)

Tableau de `DemandeExplicationDto` :

| Champ | Type | Notes |
|-------|------|--------|
| `id` | number | Id du fil |
| `contexte` | string | |
| `dossierId` | number | Id parent (demande / certificat / utilisation) |
| `roleDestinataire` | string | Badge « Destinataire : DGI » |
| `messageInitial` | string | Question d'ouverture (hors liste `messages`) |
| `statut` | `OUVERTE` \| `FERMEE` | |
| `auteurId`, `auteurNom`, `roleAuteur` | | Ouverture |
| `dateOuverture`, `dateFermeture` | ISO-8601 | |
| `messages` | array | Réponses uniquement (`DemandeExplicationMessageDto`) |

Chaque message : `id`, `message`, `auteurId`, `auteurNom`, `roleAuteur`, `createdAt`.

---

## 2. Règles métier (affichage)

| Règle | Comportement UI |
|-------|-----------------|
| Visibilité | Panneau visible **uniquement** si l'utilisateur a `demande.explication.view` (commission). Masquer pour entreprise / AC. |
| Statuts dossier autorisés | **Correction** : `RECUE`, `INCOMPLETE`, `RECEVABLE`, `EN_EVALUATION`, `EN_VALIDATION`. **Certificat** : `EN_CONTROLE`, `INCOMPLETE`, `A_RECONTROLER`. **Utilisation** : tout sauf `LIQUIDEE`, `APUREE`, `REJETEE`, `CLOTUREE`. |
| VISA | Bouton « Demander une explication » **avant** le VISA en UX ; l'API **n'empêche pas** le VISA. |
| Fil fermé | Désactiver saisie + bouton répondre ; afficher `FERMEE`. |
| Fermeture | Bouton « Fermer le fil » si utilisateur = auteur **ou** rôle `PRESIDENT`. |

---

## 3. Intégration pages détail

| Page front (référence) | `contexte` | `dossierId` |
|------------------------|------------|-------------|
| [CorrectionDouaniere.tsx](https://github.com/youssefEbba/commission-fiscale-bd18374b/blob/main/src/pages/CorrectionDouaniere.tsx) | `CORRECTION` | `demande.id` |
| [CertificatDetail.tsx](https://github.com/youssefEbba/commission-fiscale-bd18374b/blob/main/src/pages/CertificatDetail.tsx) | `CERTIFICAT` | `certificat.id` |
| [UtilisationDetail.tsx](https://github.com/youssefEbba/commission-fiscale-bd18374b/blob/main/src/pages/UtilisationDetail.tsx) | `UTILISATION` | `utilisation.id` |

### 3.1 Composant suggéré : `DiscussionCommissionPanel`

1. Au chargement : `GET /api/demandes-explication?contexte=...&dossierId=...`
2. Affichage type **chat chronologique** :
   - Bloc ouverture : `messageInitial` + auteur + date
   - Puis chaque élément de `messages` trié par `createdAt`
3. Formulaire « Nouvelle demande » (si `demande.explication.create` et statut dossier OK) : sélecteur rôle destinataire + textarea + envoi `POST /`
4. Formulaire réponse en bas de chaque fil `OUVERTE` : `POST /{id}/messages`
5. Rafraîchir la liste après create / reply / fermer (ou WebSocket notifications si branché sur `NotificationType.DEMANDE_EXPLICATION`)

### 3.2 Notifications

Type backend : `DEMANDE_EXPLICATION`. Payload : `explicationId`, `contexte`, `dossierId`, `roleDestinataire`, `statut`.  
À l'ouverture et à chaque réponse, tous les utilisateurs actifs des rôles commission sont notifiés (sauf l'auteur du message). Lien deep-link vers la page détail + panneau discussion.

### 3.3 i18n

Namespace recommandé : `explication` (FR / AR), clés exemple :

- `explication.panel.title` — « Discussion commission »
- `explication.destinataire` — « Destinataire : {{role}} »
- `explication.statut.OUVERTE` / `FERMEE`
- `explication.action.ouvrir`, `repondre`, `fermer`
- `explication.error.fermee`, `explication.error.statutDossier`

---

## 4. Exemple client (fetch)

```typescript
const API = '/api/demandes-explication';

export async function listExplications(
  token: string,
  contexte: 'CORRECTION' | 'CERTIFICAT' | 'UTILISATION',
  dossierId: number
) {
  const res = await fetch(
    `${API}?contexte=${contexte}&dossierId=${dossierId}`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  if (!res.ok) throw await res.json();
  return res.json();
}

export async function openExplication(
  token: string,
  body: {
    contexte: string;
    dossierId: number;
    roleDestinataire: string;
    message: string;
  }
) {
  const res = await fetch(API, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw await res.json();
  return res.json();
}
```

---

## 5. Fichiers backend de référence

- `DemandeExplicationController`, `DemandeExplicationService`
- Entités : `DemandeExplication`, `DemandeExplicationMessage`
- Script MySQL : `scripts/add-demande-explication-mysql.sql`
- Permissions seed : `demande.explication.view|create|reply|close` (rôles DGD, DGTCP, DGI, DGB ; Président : toutes permissions)
