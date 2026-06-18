# Documents avant visa — correction d'offre fiscale (guide front)

Documentation pour les téléversements **obligatoires avant visa** sur une demande de correction :

| Rôle | Code document | Libellé i18n |
|------|---------------|--------------|
| **DGD** | `OFFRE_FISCALE_CORRIGEE` | Offre fiscale corrigée |
| **DGI** | `CREDIT_INTERIEUR` | Crédit intérieur |
| **Président** | `LETTRE_ADOPTION` | Lettre d'adoption (avant validation, pas visa) |

> **Ne pas confondre** avec `CREDIT_EXTERIEUR` (pièce GED distincte, **non** exigée au visa DGD) ni avec le montant calculé `FiscaliteInterieure.creditInterieur` du wizard.

Guide Président : [CORRECTION_PRESIDENT_LETTRE_ADOPTION_FRONT.md](./CORRECTION_PRESIDENT_LETTRE_ADOPTION_FRONT.md).

Voir aussi : [UPLOAD_DOCUMENT_FRONT.md](./UPLOAD_DOCUMENT_FRONT.md) (règles générales `codeDocument`).

---

## 1. Principe commun (front)

Les pages **`DemandeDetail.tsx`** et **`Demandes.tsx`** utilisent la map `UPLOAD_BEFORE_VISA` et le flux générique `checkAndHandleVisa` :

```typescript
const UPLOAD_BEFORE_VISA: Record<string, { docType: string }> = {
  DGD: { docType: "OFFRE_FISCALE_CORRIGEE" },
  DGI: { docType: "CREDIT_INTERIEUR" },
};
```

Au clic **Apposer visa** :

1. `GET /api/demandes-correction/{id}/documents`
2. Si le document actif du rôle est absent → modale upload
3. `POST /api/demandes-correction/{id}/documents?codeDocument=...`
4. `POST /api/demandes-correction/{id}/decisions` avec `{ "decision": "VISA" }`

### Détection document (compat `codeDocument` / `type`)

```typescript
function normalizeDocs(docs: DocumentDto[]): DocumentDto[] {
  return docs.map((d) => ({ ...d, type: d.codeDocument ?? d.type }));
}

const hasDoc = normalizeDocs(documents).some((d) => {
  const code = d.codeDocument ?? d.type;
  return code === uploadBeforeVisa.docType && d.actif !== false;
});
```

### Upload API

```typescript
await demandeCorrectionApi.uploadDocument(demandeId, uploadBeforeVisa.docType, file);
await demandeCorrectionApi.postDecision(demandeId, "VISA");
```

Libellé modale : `tTypeDocument(uploadBeforeVisa.docType)`.

---

## 2. Visa DGD — offre fiscale corrigée

### Règle métier

Le **DGD** doit téléverser **`OFFRE_FISCALE_CORRIGEE`** avant d'apposer son visa. Le backend refuse le visa sans document actif :

`400` — `Document actif requis avant visa: OFFRE_FISCALE_CORRIGEE`

### Parcours UI

1. Connexion **DGD**
2. **Demandes → Demandes de correction** → ouvrir la demande
3. **Apposer visa**
4. Modale si `OFFRE_FISCALE_CORRIGEE` absent → PDF → **Valider**
5. Visa enregistré

### Permissions

- Upload : `correction.offer.upload` (DGD)
- Visa : transitions existantes + `postDecision("VISA")`

### Code à envoyer

```typescript
uploadDocument(demandeId, "OFFRE_FISCALE_CORRIGEE", file);
```

**Ne pas** utiliser `CREDIT_EXTERIEUR` pour le visa DGD (ancienne doc obsolète pour ce flux).

---

## 3. Visa DGI — crédit intérieur

### Règle métier

Le **DGI** doit téléverser **`CREDIT_INTERIEUR`** avant son visa, **après** le visa DGD.

Backend :

- Upload DGI limité à `CREDIT_INTERIEUR` (`403` sinon)
- Visa refusé sans document actif (`400`)

### Parcours UI

1. Connexion **DGI**
2. Ouvrir une demande avec **visa DGD déjà posé**
3. **Apposer visa** → modale si `CREDIT_INTERIEUR` absent
4. Upload puis visa

### Permissions

- Upload : `correction.dgi.document.upload`
- Visa : `correction.dgi.visa`

### Code à envoyer

```typescript
uploadDocument(demandeId, "CREDIT_INTERIEUR", file);
```

Détail complémentaire : [CORRECTION_DGI_CREDIT_INTERIEUR_FRONT.md](./CORRECTION_DGI_CREDIT_INTERIEUR_FRONT.md).

---

## 4. Séquence globale (commission)

```text
DGD  : upload OFFRE_FISCALE_CORRIGEE → visa DGD
DGTCP: visa (sans upload préalable spécifique)
DGI  : upload CREDIT_INTERIEUR       → visa DGI  (après visa DGD)
DGB  : visa (sans upload préalable spécifique)
Président (EN_VALIDATION) : upload LETTRE_ADOPTION → validation ADOPTEE
```

---

## 5. Tableau des codes correction (visa)

| Code | Rôle visa | Obligatoire avant visa |
|------|-----------|-------------------------|
| `OFFRE_FISCALE_CORRIGEE` | DGD | Oui |
| `CREDIT_INTERIEUR` | DGI | Oui |
| `CREDIT_EXTERIEUR` | — | Non (GED legacy / autres usages) |
| `LETTRE_ADOPTION` | Président (validation) | Oui — avant `ADOPTEE` |

---

## 6. Erreurs fréquentes

| Message | Cause | Action |
|---------|-------|--------|
| `Document actif requis avant visa: OFFRE_FISCALE_CORRIGEE` | Visa DGD sans upload | Modale / upload `OFFRE_FISCALE_CORRIGEE` |
| `Document actif requis avant visa: CREDIT_INTERIEUR` | Visa DGI sans upload | Modale / upload `CREDIT_INTERIEUR` |
| `Type de document non paramétré...` | GED non seedé | Redémarrer backend |
| `Le visa DGD est requis en premier` | DGI avant DGD | Attendre visa DGD |
| `Le DGI ne peut téléverser que... CREDIT_INTERIEUR` | Mauvais code côté DGI | Utiliser `CREDIT_INTERIEUR` |
| Upload OK mais mauvaise pièce | `CREDIT_EXTERIEUR` au lieu de `OFFRE_FISCALE_CORRIGEE` | Corriger `UPLOAD_BEFORE_VISA.DGD` |

---

## 7. Checklist intégration front

- [ ] `UPLOAD_BEFORE_VISA.DGD` = `OFFRE_FISCALE_CORRIGEE` (plus `CREDIT_EXTERIEUR`)
- [ ] `UPLOAD_BEFORE_VISA.DGI` = `CREDIT_INTERIEUR`
- [ ] `normalizeDocs` + `codeDocument ?? type` dans `checkAndHandleVisa`
- [ ] Même map dans `DemandeDetail.tsx` et `Demandes.tsx`
- [ ] Libellés via `tTypeDocument` (`enums:type_document.*`)
- [ ] Backend redémarré + reconnecter DGI après déploiement permissions

---

## 8. Fichiers front à modifier

| Fichier | Modification |
|---------|--------------|
| `src/pages/DemandeDetail.tsx` | `UPLOAD_BEFORE_VISA`, `checkAndHandleVisa` |
| `src/pages/Demandes.tsx` | Idem (actions liste) |
| `src/lib/api.ts` | `uploadDocument` avec `codeDocument` ; `normalizeDocs` sur `getDocuments` |
| `src/i18n/locales/fr/enums.json` | `type_document.OFFRE_FISCALE_CORRIGEE`, `CREDIT_INTERIEUR` |

**Ne pas** étendre `CorrectionDouaniere.tsx` pour ces flux — parcours canonique : `DemandeDetail`.
