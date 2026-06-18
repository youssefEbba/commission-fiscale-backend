# Injection GED par le Président — guide front

> **DEPRECATED — ne plus implémenter.**  
> L’endpoint `POST /api/dossiers/{id}/documents` et la permission `ged.president.inject` ont été **supprimés**.  
> Utiliser les flux métier par entité et la règle fail-fast MinIO : **[UPLOAD_STORAGE_FAIL_FAST_FRONT.md](./UPLOAD_STORAGE_FAIL_FAST_FRONT.md)**.  
> Lettre d’adoption Président : **[CORRECTION_PRESIDENT_LETTRE_ADOPTION_FRONT.md](./CORRECTION_PRESIDENT_LETTRE_ADOPTION_FRONT.md)**.

---

~~Le **Président** peut compléter ou remplacer un document manquant / corrompu dans un **dossier GED**, à n'importe quelle étape du workflow.~~

**Contenu ci-dessous conservé à titre d’archive uniquement.**

---

## 1. API

```http
POST /api/dossiers/{dossierId}/documents
Content-Type: multipart/form-data
Authorization: Bearer …
```

| Champ | Obligatoire | Description |
|-------|-------------|-------------|
| `etape` | Oui | Code étape GED (voir §2) |
| `codeDocument` | Oui | Code type document (`LETTRE_ADOPTION`, `BULLETIN_LIQUIDATION`, …) |
| `file` | Oui | Fichier |
| `targetId` | Non | Id utilisation / transfert / avenant si plusieurs entités sur le certificat |

Alias acceptés pour le code : `typeDocument`, `type` (legacy).

**Permission :** `ged.president.inject` (rôle `PRESIDENT`).

---

## 2. Codes d'étape (`etape`)

| Code | Exemples de documents |
|------|------------------------|
| `DEMANDE_CORRECTION` | `LETTRE_SAISINE`, `OFFRE_FISCALE`, `DAO_DQE`, … |
| `TRAITEMENT_CORRECTION` | `OFFRE_FISCALE_CORRIGEE`, `CREDIT_INTERIEUR`, `CREDIT_EXTERIEUR` |
| `RETOUR_CORRECTION` | `LETTRE_ADOPTION` |
| `EMISSION_CERTIFICAT` | `CERTIFICAT_CREDIT_IMPOTS`, `LETTRE_CORRECTION`, … |
| `UTILISATION_DOUANE` | `BULLETIN_LIQUIDATION`, `DECLARATION_DOUANE`, … |
| `UTILISATION_TVA` | `FACTURE`, `DECLARATION_TVA`, `DECOMPTE` |
| `TRANSFERT_CREDIT` | `DEMANDE_MOTIVEE_TRANSFERT`, … |
| `CLOTURE_CREDIT` | pièces clôture |
| `MODIFICATION_AVENANT` | pièces avenant |
| `SOUS_TRAITANCE` | pièces sous-traitance |

Liste paramétrable : `GET /api/document-requirements?processus=…`

---

## 3. Versionnement

- Si **aucune** version du type n'existe → **v1**, `actif: true`.
- Si une version **active** existe → ancienne `actif: false`, nouvelle **v(n+1)**, `actif: true`.
- L'historique reste visible dans `GET /api/dossiers/{id}` (toutes les versions, tri date desc).

Champs utiles côté front :

| Champ | Signification |
|-------|----------------|
| `version` | Numéro de version (1, 2, 3…) |
| `actif` | `true` = version courante utilisée par le métier |
| `versionCourante` | Alias de `actif` pour l'affichage |
| `injectionPresident` | `true` sur la réponse du POST injection |

**Affichage recommandé :**

```text
LETTRE_ADOPTION — v3 (actif) — 17/06/2026
LETTRE_ADOPTION — v2 (archivé) — 10/06/2026
```

Badge optionnel si `injectionPresident === true` : « Déposé par le Président ».

---

## 4. Exemple TypeScript

```typescript
async function injectDocumentGed(
  dossierId: number,
  etape: string,
  codeDocument: string,
  file: File,
  targetId?: number
) {
  const form = new FormData();
  form.append("etape", etape);
  form.append("codeDocument", codeDocument);
  form.append("file", file);
  if (targetId != null) form.append("targetId", String(targetId));

  return apiFetch<DocumentDto>(`/dossiers/${dossierId}/documents`, {
    method: "POST",
    rawBody: form,
  });
}

// Lettre d'adoption manquante sur DC-DEMO-PRESIDENT
await injectDocumentGed(dossierId, "RETOUR_CORRECTION", "LETTRE_ADOPTION", pdfFile);

// Bulletin liquidation sur dernière utilisation douane du certificat
await injectDocumentGed(dossierId, "UTILISATION_DOUANE", "BULLETIN_LIQUIDATION", pdfFile);
```

---

## 5. UI GED (écran dossier)

Pour chaque étape accordion, si l'utilisateur est **Président** :

1. Bouton **Ajouter / remplacer un document**
2. Sélecteur type (`codeDocument` filtré par processus de l'étape)
3. Upload fichier
4. Si plusieurs utilisations / transferts : sélecteur `targetId`
5. Rafraîchir `GET /api/dossiers/{id}` et afficher la nouvelle version en tête avec badge **vN · Actif**

---

## 6. Erreurs fréquentes

| Message | Cause |
|---------|--------|
| `403` injection GED réservée au Président | Mauvais rôle ou permission |
| `Certificat absent du dossier` | Étape certificat/utilisation sans mise en place |
| `Aucune utilisation DOUANIER… précisez targetId` | Plusieurs ou zéro utilisation — passer `targetId` |
| `Type de document non paramétré` | Code inconnu dans GED — vérifier paramétrage |

---

## 7. Distinction avec upload métier classique

| Flux | Endpoint | Rôle |
|------|----------|------|
| Visa DGD / DGI, lettre avant validation | `/api/demandes-correction/{id}/documents` | DGD, DGI, Président (lettre) |
| **Complément / réparation GED** | `/api/dossiers/{id}/documents` | **Président uniquement** |

Le flux GED Président **contourne** les restrictions de statut (ex. lettre hors `EN_VALIDATION`) pour débloquer un dossier incomplet.
