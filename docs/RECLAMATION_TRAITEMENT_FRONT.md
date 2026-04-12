# Traitement d’une réclamation (acceptation / rejet) — guide front-end

Endpoint unique en **`multipart/form-data`** (plus de JSON sur ce `PATCH`).

## `PATCH /api/demandes-correction/{demandeId}/reclamations/{reclamationId}`

**Permission** : `correction.reclamation.traiter` (DGTCP, Président).

### Champs formulaire

| Champ | Obligatoire | Description |
|--------|-------------|-------------|
| `acceptee` | oui | `"true"` / `"1"` pour accepter, `"false"` / `""` / autre pour rejeter |
| `motifReponse` | si rejet | Motif de rejet (texte, max **2000** caractères côté serveur) |
| `file` | si rejet | **Document de réponse** (pièce jointe obligatoire au rejet) |
| `file` | non si acceptation | Ne **pas** envoyer de fichier pour une acceptation (sinon 400) |

Exemple **acceptation** (DGTCP uniquement) :

- `acceptee` = `true`
- `motifReponse` = optionnel (commentaire)

Exemple **rejet** (DGTCP ou Président) :

- `acceptee` = `false`
- `motifReponse` = texte obligatoire
- `file` = fichier obligatoire (PDF ou autre selon votre politique UI)

### Effets métier

| Cas | Réclamation | Demande de correction | Visas (DGD, DGTCP, DGI, DGB) |
|-----|-------------|------------------------|------------------------------|
| **Rejet** | `REJETEE` + motif + métadonnées document (`reponseRejet*` dans le DTO) | **Inchangée** (ex. `ADOPTEE`, `NOTIFIEE`) | **Inchangés** |
| **Acceptation** | `ACCEPTEE` | repasse en **`RECUE`**, archivage lettre d’adoption / offres corrigées, visas remis à zéro | réinitialisés (voir doc principale) |

### DTO après rejet

Outre `motifReponse`, le back renseigne lorsque `statut === 'REJETEE'` :

- `reponseRejetChemin`, `reponseRejetNomFichier`, `reponseRejetTaille`, `reponseRejetDateUpload`

### Erreurs fréquentes (400)

- Rejet sans **motif** ou sans **fichier**
- Motif trop long (> 2000)
- Acceptation avec un **fichier** fourni

### Front (ex. `fetch` / FormData)

```ts
const fd = new FormData();
fd.append("acceptee", "false");
fd.append("motifReponse", motif);
fd.append("file", fileBlob, fileName);
await fetch(
  `${API}/api/demandes-correction/${demandeId}/reclamations/${reclamationId}`,
  { method: "PATCH", headers: { Authorization: `Bearer ${token}` }, body: fd }
);
```

Ne pas mettre `Content-Type` manuellement : le navigateur doit définir la boundary du multipart.

Voir aussi : [RECLAMATION_CORRECTION_FRONT.md](./RECLAMATION_CORRECTION_FRONT.md) (création, annulation, liste).
