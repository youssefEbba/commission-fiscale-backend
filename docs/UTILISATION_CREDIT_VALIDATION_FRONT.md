# Validation pré-soumission — utilisation de crédit (guide front)

Guide des **garde-fous UI** avant `POST /api/utilisations-credit` et `POST .../soumettre`. Complète [UTILISATION_CREDIT_DOUANE_FRONT.md](./UTILISATION_CREDIT_DOUANE_FRONT.md) (workflow douane) et s’applique aussi à la TVA intérieure.

---

## 1. Principe

- Le **backend** est la source de vérité : toute règle ci-dessous est **aussi** appliquée côté serveur.
- Le **front** évite les allers-retours 400/409 en validant **avant** soumission et en désactivant le formulaire si le certificat n’est pas éligible.

---

## 2. Charger l’éligibilité certificat

À la sélection du certificat (ou à l’ouverture du modal « Nouvelle utilisation ») :

```http
GET /api/certificats-credit/{certificatId}/eligibilite-utilisation?type=DOUANIER
GET /api/certificats-credit/{certificatId}/eligibilite-utilisation?type=TVA_INTERIEURE
```

**Permissions** : `utilisation.douane.submit` ou `utilisation.interieur.submit` (ou consultation solde).

### Réponse (`CertificatUtilisationEligibilityDto`)

| Champ | Description |
|-------|-------------|
| `eligible` | `true` si aucun motif bloquant |
| `statutCertificat` | Statut actuel du certificat |
| `motifs` | Liste de messages lisibles (vide si éligible) |
| `soldeCordon` | Solde cordon disponible (e) |
| `tvaImportationDouane` | Quota TVA import restant (d) |
| `soldeTVA` | Solde TVA intérieure (h) |
| `transfertExecute` | `true` → plus d’utilisation **douanière** |
| `clotureEnCours` | Proposition de clôture DGTCP non finalisée |
| `dateValidite` | Fin de validité du certificat |
| `expire` | `true` si `dateValidite` dépassée |

Exemple certificat non éligible :

```json
{
  "eligible": false,
  "statutCertificat": "CLOTURE",
  "motifs": ["Certificat clôturé ou annulé"],
  "soldeCordon": 0,
  "tvaImportationDouane": 0,
  "soldeTVA": 0,
  "transfertExecute": false,
  "clotureEnCours": false,
  "expire": false
}
```

---

## 3. Statuts certificat autorisés

| Statut | Nouvelle utilisation |
|--------|---------------------|
| `OUVERT`, `MODIFIE` | Oui |
| `CLOTURE`, `ANNULE` | Non |
| Autres (`EN_CONTROLE`, `BROUILLON` certificat, …) | Non |

> **`MODIFIE`** : certificat encore actif (modification / avenant en cours). Le flux avenant automatique n’est pas encore branché ; le statut peut être posé manuellement via l’API certificat.

---

## 4. Contrôles douane (entreprise / sous-traitant)

| Check | Action front | Référence backend |
|-------|--------------|-------------------|
| `eligible === true` | Désactiver le formulaire sinon | `GET eligibilite-utilisation` |
| `transfertExecute === false` | Bloquer type DOUANIER | Motif API |
| ≥ 1 ligne bulletin | Bloquer soumission | `lignes` obligatoires |
| Chaque ligne > 0 → `AU_CI` ou `A_PAYER` | Bloquer soumission | `affectation` |
| Σ AU_CI (hors code `TVA`) ≤ `soldeCordon` | Bloquer + message | Soldes à la soumission |
| Σ AU_CI (code `TVA`) ≤ `tvaImportationDouane` | Bloquer + message | Quota import |

**Formules** (lignes proposées entreprise) :

- `montantCordonRequis` = somme des `valeurTaxe` avec `affectation === "AU_CI"` et `codeTaxe !== "TVA"`
- `tvaImportRequis` = somme des `valeurTaxe` avec `affectation === "AU_CI"` et `codeTaxe === "TVA"`

La liquidation DGTCP refait un contrôle après visa DGD (filet de sécurité).

---

## 5. Contrôles TVA intérieure

| Check | Blocage / avertissement |
|-------|-------------------------|
| Certificat actif (`eligible`) | **Blocage** |
| `montantTVA` > 0 | **Blocage** |
| `montantTVA` > `soldeTVA` | **Avertissement** (paiement complément possible à l’apurement DGTCP) |
| Documents selon `typeAchat` | **Blocage** si champs / pièces obligatoires manquants |

---

## 6. Handler TypeScript modèle

```typescript
interface CertificatUtilisationEligibilityDto {
  eligible: boolean;
  statutCertificat?: string;
  motifs: string[];
  soldeCordon?: number;
  tvaImportationDouane?: number;
  soldeTVA?: number;
  transfertExecute?: boolean;
  clotureEnCours?: boolean;
  expire?: boolean;
}

interface LigneBulletinInput {
  codeTaxe: string;
  valeurTaxe: number;
  affectation?: "AU_CI" | "A_PAYER";
}

function sumAuCi(lignes: LigneBulletinInput[]): { cordon: number; tva: number } {
  let cordon = 0;
  let tva = 0;
  for (const l of lignes) {
    if (l.affectation !== "AU_CI" || l.valeurTaxe <= 0) continue;
    if (l.codeTaxe?.toUpperCase() === "TVA") tva += l.valeurTaxe;
    else cordon += l.valeurTaxe;
  }
  return { cordon, tva };
}

function canSubmitUtilisationDouane(
  elig: CertificatUtilisationEligibilityDto,
  lignes: LigneBulletinInput[]
): { ok: boolean; errors: string[] } {
  const errors: string[] = [];
  if (!elig.eligible) errors.push(...(elig.motifs ?? []));
  if (elig.transfertExecute) {
    errors.push("Transfert exécuté — utilisations douanières interdites");
  }
  if (lignes.length === 0) errors.push("Au moins une ligne de bulletin requise");
  for (const l of lignes) {
    if (l.valeurTaxe > 0 && !l.affectation) {
      errors.push(`Affectation AU_CI ou A_PAYER requise pour ${l.codeTaxe}`);
    }
  }
  const { cordon, tva } = sumAuCi(lignes);
  const soldeCordon = elig.soldeCordon ?? 0;
  const quotaTva = elig.tvaImportationDouane ?? 0;
  if (cordon > soldeCordon) {
    errors.push(`Solde cordon insuffisant (disponible=${soldeCordon}, requis=${cordon})`);
  }
  if (quotaTva > 0 && tva > quotaTva) {
    errors.push(`Quota TVA import insuffisant (disponible=${quotaTva}, requis=${tva})`);
  }
  return { ok: errors.length === 0, errors };
}
```

---

## 7. UX recommandée

1. Au choix du certificat : appeler `GET eligibilite-utilisation` et afficher un bandeau si `!eligible`.
2. Pendant la saisie des lignes : recalculer localement les totaux AU_CI vs soldes.
3. Bouton **Soumettre** : `disabled` tant que `!ok`.
4. En cas d’erreur API 400/409 : afficher le message backend (souvent identique aux `motifs`).

---

## 8. Messages d’erreur API (référence)

| Situation | HTTP | Exemple message |
|-----------|------|-----------------|
| Certificat non OUVERT/MODIFIE | 400 | Le crédit doit être OUVERT ou MODIFIE… |
| Certificat clôturé / annulé | 400 | Certificat clôturé ou annulé |
| Validité dépassée | 400 | Date de validité du certificat dépassée |
| Clôture en cours | 400 | Une proposition de clôture est en cours… |
| Transfert exécuté (douane) | 409 | Transfert exécuté — utilisations douanières interdites |
| Solde cordon | 400 | Solde cordon insuffisant (disponible=X, requis=Y) |
| Quota TVA import | 400 | Quota TVA import insuffisant… |

---

## 9. Liens

- [UTILISATION_CREDIT_DOUANE_FRONT.md](./UTILISATION_CREDIT_DOUANE_FRONT.md) — workflow douane complet
- [UTILISATION_SOUS_TRAITANCE.md](./UTILISATION_SOUS_TRAITANCE.md) — sous-traitant autorisé sur certificat
- [NOTIFICATIONS_NAVIGATION_FRONT.md](./NOTIFICATIONS_NAVIGATION_FRONT.md) — clic notification → fiche utilisation
