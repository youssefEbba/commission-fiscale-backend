# Upload CREDIT_INTERIEUR par le DGI — guide front

Documentation pour intégrer le téléversement du document **crédit intérieur** (`CREDIT_INTERIEUR`) **avant le visa DGI** sur une demande de correction, en symétrie du flux DGD / `OFFRE_FISCALE_CORRIGEE`.

Guide unifié DGD + DGI : [CORRECTION_VISA_DOCUMENTS_FRONT.md](./CORRECTION_VISA_DOCUMENTS_FRONT.md).

Voir aussi : [UPLOAD_DOCUMENT_FRONT.md](./UPLOAD_DOCUMENT_FRONT.md) (règles générales d'upload).

---

## 1. Objectif

Lorsque l'agent **DGI** clique **Apposer visa** sur une demande de correction :

1. Le front vérifie qu'un document actif `CREDIT_INTERIEUR` est présent.
2. S'il est absent → modale d'upload.
3. Après upload réussi → enregistrement du visa (`POST .../decisions` avec `decision=VISA`).

Le backend refuse le visa DGI sans document actif `CREDIT_INTERIEUR` (`400` — `Document actif requis avant visa: CREDIT_INTERIEUR`).

---

## 2. Acteurs et prérequis

| Élément | Valeur |
|---------|--------|
| Rôle | `DGI` |
| Permission upload | `correction.dgi.document.upload` |
| Permission visa | `correction.dgi.visa` |
| Code document | `CREDIT_INTERIEUR` |
| Processus GED | `CORRECTION_OFFRE_FISCALE` |

**Prérequis métier :**

- Visa **DGD** déjà posé (sinon banner UI + `400` backend : « Le visa DGD est requis en premier »).
- Statut demande dans : `RECUE`, `INCOMPLETE`, `RECEVABLE`, `EN_EVALUATION`, `EN_VALIDATION`.
- Aucun visa DGI déjà enregistré pour ce dossier.

---

## 3. Parcours UI (pas à pas)

1. Connexion compte **DGI**.
2. Menu **Demandes → Demandes de correction**.
3. Ouvrir le détail d'une demande (`/dashboard/demandes/:id`).
4. Vérifier que le visa DGD est présent (onglet DGD ou liste décisions).
5. Cliquer **Apposer visa** (bouton DGI).
6. Si `CREDIT_INTERIEUR` actif absent → modale « document requis » :
   - Choisir un fichier (PDF recommandé).
   - **Valider** → upload puis visa automatique.
7. Rafraîchir la liste documents : pièce « Crédit intérieur », `actif: true`.

---

## 4. Séquence API

```text
GET  /api/demandes-correction/{id}/documents
POST /api/demandes-correction/{id}/documents?codeDocument=CREDIT_INTERIEUR   (multipart: file)
POST /api/demandes-correction/{id}/decisions                                 (JSON: { "decision": "VISA" })
```

### Exemple `api.ts`

Réutiliser les helpers existants :

```typescript
// Upload (DGI — codeDocument obligatoire)
await demandeCorrectionApi.uploadDocument(demandeId, "CREDIT_INTERIEUR", file);

// Visa
await demandeCorrectionApi.postDecision(demandeId, "VISA");
```

### Normalisation des documents (`normalizeDocs`)

Le backend renvoie `codeDocument` (plus `type`). Appliquer `normalizeDocs` sur tous les `getDocuments` :

```typescript
function normalizeDocs(docs: DocumentDto[]): DocumentDto[] {
  return docs.map((d) => ({
    ...d,
    type: d.codeDocument ?? d.type,
  }));
}
```

Utiliser `codeDocument ?? type` pour la détection avant visa :

```typescript
const hasDoc = documents.some((d) => {
  const code = d.codeDocument ?? d.type;
  return code === uploadBeforeVisa.docType && d.actif !== false;
});
```

---

## 5. Modifications front à appliquer

### 5.1 `DemandeDetail.tsx` et `Demandes.tsx`

Étendre la map **upload avant visa** (aujourd'hui DGD seul) :

```typescript
const UPLOAD_BEFORE_VISA: Record<string, { docType: string }> = {
  DGD: { docType: "OFFRE_FISCALE_CORRIGEE" },
  DGI: { docType: "CREDIT_INTERIEUR" },
};
```

Le flux existant `checkAndHandleVisa` → modale → `handleOffreCorrigeeUploadAndVisa` est **déjà générique** : aucune nouvelle modale nécessaire si `uploadBeforeVisaLabel` utilise `tTypeDocument(uploadBeforeVisa.docType)`.

**Corriger la détection** dans `checkAndHandleVisa` :

```typescript
const checkAndHandleVisa = async (demandeId: number) => {
  if (uploadBeforeVisa) {
    try {
      const documents = normalizeDocs(await demandeCorrectionApi.getDocuments(demandeId));
      const hasDoc = documents.some((d) => {
        const code = d.codeDocument ?? d.type;
        return code === uploadBeforeVisa.docType && d.actif !== false;
      });
      if (!hasDoc) {
        setOffreCorrigeePendingId(demandeId);
        setOffreCorrigeeOpen(true);
        return;
      }
    } catch {
      setOffreCorrigeePendingId(demandeId);
      setOffreCorrigeeOpen(true);
      return;
    }
  }
  await handleTempVisa(demandeId);
};
```

Appliquer la **même modification** dans `Demandes.tsx` si les actions visa y sont dupliquées.

### 5.2 i18n

Libellé déjà disponible :

- `enums:type_document.CREDIT_INTERIEUR` → « Crédit intérieur » (FR)

Optionnel : renommer les clés modale `demandes:dialogs.offre_corrigee.*` en `demandes:dialogs.pre_visa_document.*` pour DGD et DGI.

### 5.3 Écran à ne pas modifier

**`CorrectionDouaniere.tsx`** : logique DGD spécifique, route séparée. Le parcours canonique reste **`DemandeDetail`**.

---

## 6. Affichage des documents

| Champ API | Usage UI |
|-----------|----------|
| `codeDocument` | Code canonique (`CREDIT_INTERIEUR`) |
| `type` | Alias rempli par `normalizeDocs` pour compat i18n |
| `actif` | `true` = version courante ; `false` = historique |
| `version` | Numéro de version (n, n+1…) |

Libellé : `tTypeDocument("CREDIT_INTERIEUR")`.

---

## 7. Crédit intérieur : document GED vs champ fiscal

| | Document GED `CREDIT_INTERIEUR` | Champ `FiscaliteInterieure.creditInterieur` |
|--|--------------------------------|-----------------------------------------------|
| Nature | Fichier PDF téléversé par le DGI | Montant calculé dans le wizard |
| Moment | Avant visa DGI | À la création de la demande (AC) |
| API | `POST .../documents?codeDocument=CREDIT_INTERIEUR` | Payload `POST /api/demandes-correction` |
| Affichage | Liste pièces du dossier | Récapitulatif fiscal |

Ce sont **deux concepts distincts** : le montant calculé ne remplace pas la pièce justificative du visa DGI.

---

## 8. Erreurs fréquentes

| Message | Cause | Action |
|---------|-------|--------|
| `Type de document non paramétré... CREDIT_INTERIEUR` | Exigence GED absente | Redémarrer le backend (seed idempotent) |
| `403` sur upload | Permission `correction.dgi.document.upload` manquante | Déployer backend + reconnecter DGI |
| `Le DGI ne peut téléverser que le document CREDIT_INTERIEUR` | Autre code envoyé en DGI | Utiliser uniquement `CREDIT_INTERIEUR` |
| `Document actif requis avant visa: CREDIT_INTERIEUR` | Visa sans upload préalable | Passer par la modale ou uploader d'abord |
| `Le visa DGD est requis en premier` | DGD n'a pas visé | Attendre le visa DGD |
| `type_document.undefined` | `type` non normalisé | `normalizeDocs` sur `getDocuments` |

---

## 9. Checklist intégration

- [ ] `UPLOAD_BEFORE_VISA` inclut `DGI: { docType: "CREDIT_INTERIEUR" }` dans `DemandeDetail.tsx` et `Demandes.tsx`
- [ ] `checkAndHandleVisa` utilise `codeDocument ?? type` + `normalizeDocs`
- [ ] `uploadDocument` envoie `codeDocument=CREDIT_INTERIEUR`
- [ ] Libellé modale via `tTypeDocument("CREDIT_INTERIEUR")`
- [ ] Backend redémarré (GED + permission DGI)
- [ ] Test manuel : visa DGI sans document → modale ; avec document → visa OK

---

## 10. Tests de recette

Voir [PLAN_DE_TEST.md](./PLAN_DE_TEST.md) — section **4.3** (cas 4.3.2, 4.3.5, 4.3.6).
