# Upload de documents — guide front

Documentation pour corriger l'erreur **`Paramètre obligatoire manquant: codeDocument`** lors des téléversements (correction, certificat, utilisation, transfert, etc.).

## Cause

Le backend attend le **code du type de document** sous le nom **`codeDocument`**.  
Les anciennes versions du front envoyaient **`type`** (query string ou FormData), ce qui provoquait une erreur HTTP **400**.

Depuis la correction backend, les **trois noms** sont acceptés (priorité dans cet ordre) :

1. `codeDocument` *(recommandé)*
2. `typeDocument` *(réponses rejet temporaire)*
3. `type` *(legacy — compatibilité)*

## Règle générale

Tout upload `multipart/form-data` sur un endpoint `POST .../documents` doit inclure :

| Champ | Obligatoire | Description |
|-------|-------------|-------------|
| `file` | Oui | Fichier binaire |
| `codeDocument` | Oui* | Code du type (ex. `OFFRE_FINANCIERE`, `OFFRE_FISCALE_CORRIGEE`) |
| `message` | Non | Commentaire (obligatoire si rejet temporaire ouvert sur ce type) |

\* Ou alias `type` / `typeDocument` (legacy).

**Ne pas** envoyer uniquement le fichier sans code de type.

## Stockage MinIO (fail-fast)

Le backend enregistre le fichier dans MinIO (ou stockage local en dev) **avant** de persister le document en base. En cas de panne stockage :

- HTTP **503**, code `OBJECT_STORAGE_UNAVAILABLE` ;
- **aucun** document actif créé ;
- pour les actions combinées (visa DGD, saisie chèque, quittances), **aucun** changement de statut.

Guide UI complet : **[UPLOAD_STORAGE_FAIL_FAST_FRONT.md](./UPLOAD_STORAGE_FAIL_FAST_FRONT.md)**.

## Exemple TypeScript (`api.ts`)

### Correction — demande de correction

```typescript
uploadDocument: (id: number, codeDocument: string, file: File, message?: string) => {
  const formData = new FormData();
  formData.append("file", file);
  if (message) formData.append("message", message);
  return apiFetch<DocumentDto>(
    `/demandes-correction/${id}/documents?codeDocument=${encodeURIComponent(codeDocument)}`,
    { method: "POST", rawBody: formData }
  );
},
```

### Certificat — FormData (query ou form acceptés)

```typescript
uploadDocument: (id: number, codeDocument: string, file: File) => {
  const formData = new FormData();
  formData.append("codeDocument", codeDocument);
  formData.append("file", file);
  return apiFetch<DocumentDto>(`/certificats-credit/${id}/documents`, {
    method: "POST",
    rawBody: formData,
  });
},
```

### Utilisation / Transfert / Clôture / Sous-traitance / Avenant

Même principe : **`?codeDocument=...`** en query **ou** `formData.append("codeDocument", ...)`.

```typescript
// Exemple utilisation
`/utilisations-credit/${id}/documents?codeDocument=${encodeURIComponent(codeDocument)}`
```

## Endpoints concernés

| Processus | Méthode | URL |
|-----------|---------|-----|
| Demande correction | POST | `/api/demandes-correction/{id}/documents` |
| Certificat | POST | `/api/certificats-credit/{id}/documents` |
| Utilisation | POST | `/api/utilisations-credit/{id}/documents` |
| Transfert | POST | `/api/transferts-credit/{id}/documents` |
| Clôture | POST | `/api/clotures-credit/{id}/documents` |
| Sous-traitance | POST | `/api/sous-traitances/{id}/documents` |
| Avenant | POST | `/api/avenants/{id}/documents` |

> **Convention / Marché** : ces endpoints utilisent encore le paramètre enum `type` (`TypeDocumentConvention`, `TypeDocumentMarche`) — pas de changement.

## Cas métier : visas DGD / DGI sur demande de correction

Guide complet (map `UPLOAD_BEFORE_VISA`, modale, checklist) : **[CORRECTION_VISA_DOCUMENTS_FRONT.md](./CORRECTION_VISA_DOCUMENTS_FRONT.md)**.

### Visa DGD — offre fiscale corrigée

Lors du clic **Visa** (DGD), si **`OFFRE_FISCALE_CORRIGEE`** est absent, modale upload puis visa.

5. `uploadDocument(demandeId, "OFFRE_FISCALE_CORRIGEE", file)` puis `postDecision(..., "VISA")`

Le backend refuse le visa DGD sans document actif `OFFRE_FISCALE_CORRIGEE` (`400`).

### Visa DGI — crédit intérieur

Lors du clic **Visa** (DGI), si **`CREDIT_INTERIEUR`** est absent (après visa DGD), modale upload puis visa.

5. `uploadDocument(demandeId, "CREDIT_INTERIEUR", file)` puis `postDecision(..., "VISA")`

Codes utiles (correction — visa commission) :

| Code | Usage |
|------|--------|
| `OFFRE_FISCALE_CORRIGEE` | **Obligatoire avant visa DGD** |
| `CREDIT_INTERIEUR` | **Obligatoire avant visa DGI** |
| `OFFRE_FINANCIERE` | Offre fiscale (assistant création AC) |
| `LETTRE_SAISINE` | Lettre de saisine |
| `TABLEAU_MODELE` | Tableau modèle |
| `LETTRE_ADOPTION` | Lettre signée (adoption président) |
| `CREDIT_EXTERIEUR` | Pièce GED (hors flux visa DGD) |

Les codes exacts par processus sont configurables dans **GED → Configuration** (`GET /api/document-requirements?processus=...`).

### Détail DGI

Guide complémentaire : [CORRECTION_DGI_CREDIT_INTERIEUR_FRONT.md](./CORRECTION_DGI_CREDIT_INTERIEUR_FRONT.md).

Le backend DGI :

- exige `correction.dgi.document.upload` ;
- n'autorise que `CREDIT_INTERIEUR` (`403` sinon) ;
- refuse le visa sans document actif (`400`).

## Réponse rejet temporaire (multipart)

Pour `POST .../decisions/{decisionId}/rejet-temp/reponses` :

```typescript
const formData = new FormData();
formData.append("message", message);
formData.append("file", file);
formData.append("codeDocument", codeDocument); // ou typeDocument (legacy)
```

## Checklist intégration front

- [ ] Remplacer `?type=` par `?codeDocument=` dans toutes les URLs d'upload
- [ ] Remplacer `formData.append("type", ...)` par `formData.append("codeDocument", ...)`
- [ ] Ne pas définir `Content-Type` manuellement sur FormData (le navigateur ajoute le boundary)
- [ ] Conserver `Authorization: Bearer <token>`
- [ ] Vérifier que le code envoyé existe dans le référentiel GED du processus
- [ ] Redémarrer le backend après déploiement de la correction alias (si front encore en `type`, ça fonctionne aussi)

## Erreurs fréquentes

| Message | Cause | Action |
|---------|-------|--------|
| `Paramètre obligatoire manquant: codeDocument` | Aucun code type envoyé | Ajouter `codeDocument` (ou `type`) |
| `VALIDATION_FAILED` sur le code | Code inconnu / non autorisé pour le processus | Vérifier GED / liste des types |
| Upload OK mais visa bloqué | Mauvais code (ex. `CREDIT_EXTERIEUR` au lieu de `OFFRE_FISCALE_CORRIGEE` pour DGD) | Voir [CORRECTION_VISA_DOCUMENTS_FRONT.md](./CORRECTION_VISA_DOCUMENTS_FRONT.md) |
| `Document actif requis avant visa: OFFRE_FISCALE_CORRIGEE` | Visa DGD sans upload | Téléverser `OFFRE_FISCALE_CORRIGEE` |
| `Document actif requis avant visa: CREDIT_INTERIEUR` | Visa DGI sans upload préalable | Téléverser `CREDIT_INTERIEUR` avant le visa |
| `Le DGI ne peut téléverser que le document CREDIT_INTERIEUR` | DGI tente un autre type | Utiliser uniquement `CREDIT_INTERIEUR` |
| `OBJECT_STORAGE_UNAVAILABLE` (503) | MinIO / stockage indisponible | Réessayer ; ne pas avancer l’étape (voir [UPLOAD_STORAGE_FAIL_FAST_FRONT.md](./UPLOAD_STORAGE_FAIL_FAST_FRONT.md)) |

## Fichier de référence (snapshot front)

Les helpers `uploadDocument` du fichier `src/lib/api.ts` du projet front ont été alignés sur `codeDocument`.
