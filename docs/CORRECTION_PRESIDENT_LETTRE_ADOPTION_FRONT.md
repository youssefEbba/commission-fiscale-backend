# Lettre d'adoption — validation Président (guide front)

Le **Président** doit téléverser une **lettre d'adoption** (`LETTRE_ADOPTION`) **avant** de valider une demande de correction (statut `ADOPTEE`).

Voir aussi : [CORRECTION_VISA_DOCUMENTS_FRONT.md](./CORRECTION_VISA_DOCUMENTS_FRONT.md) (visas commission).

---

## 1. Règle métier

| Étape | Condition |
|-------|-----------|
| Statut dossier | `EN_VALIDATION` (4 visas commission posés) |
| Upload | `LETTRE_ADOPTION` actif sur la demande |
| Validation | `PATCH .../statut?statut=ADOPTEE&decisionFinale=true` |

Sans lettre active, le backend répond **`400`** :

`Document actif requis avant validation: LETTRE_ADOPTION`

---

## 2. Flux UI recommandé

Au clic **Valider** (Président) :

1. `GET /api/demandes-correction/{id}/documents`
2. Si `LETTRE_ADOPTION` absent (`actif !== false`) → modale upload
3. `POST /api/demandes-correction/{id}/documents?codeDocument=LETTRE_ADOPTION`
4. `PATCH /api/demandes-correction/{id}/statut?statut=ADOPTEE&decisionFinale=true`

### Détection document

```typescript
function normalizeDocs(docs: DocumentDto[]): DocumentDto[] {
  return docs.map((d) => ({ ...d, type: d.codeDocument ?? d.type }));
}

const DOC_PRESIDENT = "LETTRE_ADOPTION";

const hasLettre = normalizeDocs(documents).some((d) => {
  const code = d.codeDocument ?? d.type;
  return code === DOC_PRESIDENT && d.actif !== false;
});
```

### Upload

```typescript
await demandeCorrectionApi.uploadDocument(demandeId, "LETTRE_ADOPTION", file);
await demandeCorrectionApi.updateStatut(demandeId, "ADOPTEE", { decisionFinale: true });
```

Libellé modale : `tTypeDocument("LETTRE_ADOPTION")` → « Lettre d'adoption ».

---

## 3. Constante front (exemple)

Dans `DemandeDetail.tsx` / `Demandes.tsx`, à côté de `UPLOAD_BEFORE_VISA` :

```typescript
const UPLOAD_BEFORE_PRESIDENT_VALIDATE = {
  PRESIDENT: { docType: "LETTRE_ADOPTION" },
};
```

Réutiliser la même logique que `checkAndHandleVisa` (liste documents → modale → upload → action).

---

## 4. Permissions & API

| Action | Permission | Endpoint |
|--------|------------|----------|
| Lister documents | `correction.president.queue.view` ou `correction.president.signature.upload` | `GET .../documents` |
| Upload lettre | `correction.president.signature.upload` | `POST .../documents?codeDocument=LETTRE_ADOPTION` |
| Valider | `correction.president.validate` | `PATCH .../statut?statut=ADOPTEE&decisionFinale=true` |

Upload autorisé **uniquement** en statut `EN_VALIDATION`. Remplacement d'une lettre déjà déposée : autorisé pour le Président sur ce statut.

---

## 5. Séquence globale correction

```text
DGD  : OFFRE_FISCALE_CORRIGEE → visa
DGTCP, DGI (CREDIT_INTERIEUR), DGB : visas
Statut → EN_VALIDATION
Président : LETTRE_ADOPTION → validation (ADOPTEE)
```

Après **réclamation acceptée**, la lettre est archivée : le Président doit la **retéléverser** avant une nouvelle validation.

---

## 6. Erreurs fréquentes

| Message | Cause | Action |
|---------|-------|--------|
| `Document actif requis avant validation: LETTRE_ADOPTION` | Validation sans upload | Modale + upload puis valider |
| `Le Président ne peut téléverser que... LETTRE_ADOPTION` | Mauvais `codeDocument` | Envoyer `LETTRE_ADOPTION` |
| `... uniquement en statut EN_VALIDATION` | Upload trop tôt | Attendre les 4 visas commission |
| `403` sur upload | Permission manquante | Compte avec `correction.president.signature.upload` |

---

## 7. Checklist intégration

- [ ] `checkAndHandlePresidentValidate` (ou extension de `checkAndHandleVisa`) avec `LETTRE_ADOPTION`
- [ ] `uploadDocument(..., "LETTRE_ADOPTION", file)` avec `codeDocument` en query
- [ ] `normalizeDocs` sur `getDocuments`
- [ ] Bouton **Valider** désactivé ou guidé si statut ≠ `EN_VALIDATION`
- [ ] i18n : `type_document.LETTRE_ADOPTION` dans `enums.json`
