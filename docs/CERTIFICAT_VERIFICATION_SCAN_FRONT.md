# Vérification certificat par scan (code-barres) — guide front

Permet de **scanner le code-barres** imprimé sur le certificat de crédit et d’afficher immédiatement si le document est **valide**, **expiré**, **clôturé**, **en cours de mise en place**, etc.

---

## 1. Endpoint

```http
GET /api/certificats-credit/verification?numero={codeBarres}
Authorization: Bearer {token}
```

| Paramètre | Description |
|-----------|-------------|
| `numero` | Valeur lue par le scanner — correspond au champ **`certificat.numero`** (ex. `CI-DEMO-SCEN-E`, `CERT-…`). Trim + majuscules appliqués côté serveur. |

**Permission** : `certificat.verification.scan` (attribuée à tous les rôles métier).

### Comportement HTTP

| Cas | Code |
|-----|------|
| Numéro valide (trouvé ou non) | **200** — toujours un corps JSON complet |
| Paramètre `numero` absent ou vide | **400** |
| Non authentifié | **401** |
| Token sans permission | **403** |

> **UX scanner** : un certificat inconnu renvoie **200** avec `trouve: false` (pas de 404), pour afficher directement « Certificat introuvable » sans gérer une erreur réseau.

---

## 2. Modèle TypeScript

```typescript
export type EtatVerificationCertificat =
  | 'INCONNU'
  | 'VALIDE'
  | 'EXPIRE'
  | 'CLOTURE'
  | 'ANNULE'
  | 'EN_COURS'
  | 'NON_VALIDE';

export type SeveriteUi = 'success' | 'warning' | 'destructive' | 'muted';

export type StatutCertificat =
  | 'BROUILLON'
  | 'ENVOYEE'
  | 'EN_CONTROLE'
  | 'INCOMPLETE'
  | 'A_RECONTROLER'
  | 'EN_VALIDATION_PRESIDENT'
  | 'VALIDE_PRESIDENT'
  | 'EN_OUVERTURE_DGTCP'
  | 'OUVERT'
  | 'MODIFIE'
  | 'CLOTURE'
  | 'ANNULE';

export interface CertificatVerificationDto {
  trouve: boolean;
  numero: string;
  certificatId?: number;
  statutCertificat?: StatutCertificat;
  etatVerification: EtatVerificationCertificat;
  libelleEtat: string;
  severiteUi: SeveriteUi;
  dateEmission?: string;
  dateValidite?: string;
  expire: boolean;
  entrepriseRaisonSociale?: string;
  marcheId?: number;
  soldeCordon?: number;
  soldeTVA?: number;
  utilisableDouane: boolean;
  utilisableTVA: boolean;
  motifs: string[];
}
```

---

## 3. Appel API (exemple)

```typescript
async function verifyCertificatByNumero(numero: string): Promise<CertificatVerificationDto> {
  const params = new URLSearchParams({ numero: numero.trim() });
  const res = await fetch(`${API_BASE}/api/certificats-credit/verification?${params}`, {
    headers: { Authorization: `Bearer ${getToken()}` },
  });
  if (res.status === 400) {
    throw new Error('Numéro de certificat obligatoire');
  }
  if (!res.ok) {
    throw new Error(`Vérification impossible (${res.status})`);
  }
  return res.json();
}
```

---

## 4. États affichés (badge principal)

Utiliser **`libelleEtat`** + **`severiteUi`** pour le badge principal ; **`etatVerification`** pour la logique conditionnelle.

| `etatVerification` | `libelleEtat` (exemple) | `severiteUi` | Signification |
|--------------------|-------------------------|--------------|---------------|
| `VALIDE` | Certificat valide | `success` | Statut `OUVERT` ou `MODIFIE`, non expiré |
| `EXPIRE` | Certificat expiré | `warning` | Date de validité dépassée |
| `CLOTURE` | Certificat clôturé | `muted` | Crédit définitivement clos |
| `ANNULE` | Certificat annulé | `destructive` | Dossier annulé |
| `EN_COURS` | Certificat en cours de mise en place | `warning` | Pas encore ouvert (workflow DGD/DGI/DGTCP/Président) |
| `INCONNU` | Certificat introuvable | `destructive` | Aucun enregistrement pour ce numéro |
| `NON_VALIDE` | Certificat non valide | `destructive` | Autre statut non utilisable |

Mapping couleur suggéré (shadcn / Tailwind) :

| `severiteUi` | Variante badge |
|--------------|----------------|
| `success` | `default` / vert |
| `warning` | `secondary` / ambre |
| `destructive` | `destructive` / rouge |
| `muted` | `outline` / gris |

---

## 5. Informations complémentaires à l’écran

Afficher si `trouve === true` :

| Champ | Usage UI |
|-------|----------|
| `numero` | Numéro normalisé (relecture humaine) |
| `entrepriseRaisonSociale` | Bénéficiaire |
| `dateEmission` / `dateValidite` | Validité |
| `soldeCordon` / `soldeTVA` | Soldes restants |
| `utilisableDouane` / `utilisableTVA` | Puces « Douane » / « TVA intérieure » (voir éligibilité détaillée) |
| `motifs` | Liste à puces si blocages (transfert exécuté, clôture en cours, etc.) |

Lien optionnel vers la fiche certificat : `GET /api/certificats-credit/{certificatId}` si l’utilisateur a les droits de consultation.

---

## 6. Exemples de réponses

### Certificat valide

```json
{
  "trouve": true,
  "numero": "CI-DEMO-SCEN-E",
  "certificatId": 42,
  "statutCertificat": "OUVERT",
  "etatVerification": "VALIDE",
  "libelleEtat": "Certificat valide",
  "severiteUi": "success",
  "expire": false,
  "entrepriseRaisonSociale": "Entreprise démo",
  "soldeCordon": 1500.00,
  "soldeTVA": 800.00,
  "utilisableDouane": true,
  "utilisableTVA": true,
  "motifs": ["Certificat actif — crédit ouvert"]
}
```

### Numéro inconnu

```json
{
  "trouve": false,
  "numero": "CERT-INEXISTANT",
  "etatVerification": "INCONNU",
  "libelleEtat": "Certificat introuvable",
  "severiteUi": "destructive",
  "expire": false,
  "utilisableDouane": false,
  "utilisableTVA": false,
  "motifs": ["Aucun certificat enregistré pour ce numéro"]
}
```

### Certificat clôturé

```json
{
  "trouve": true,
  "numero": "CI-DEMO-SCEN-F",
  "statutCertificat": "CLOTURE",
  "etatVerification": "CLOTURE",
  "libelleEtat": "Certificat clôturé",
  "severiteUi": "muted",
  "utilisableDouane": false,
  "utilisableTVA": false,
  "motifs": ["Certificat clôturé"]
}
```

---

## 7. Parcours UI recommandé

1. **Écran « Vérifier un certificat »** : champ texte + bouton « Scanner » (caméra / douchette).
2. À chaque lecture barcode → appeler `GET …/verification?numero=…`.
3. Afficher le **badge** (`libelleEtat`, `severiteUi`) en grand.
4. Si `trouve`, montrer entreprise, dates, soldes et indicateurs douane/TVA.
5. Si `motifs.length > 0`, liste explicative sous le badge.
6. Debounce 300 ms si saisie manuelle ; pas de debounce si event scanner (souvent `\n` final).

---

## 8. Différence avec l’éligibilité utilisation

| API | Usage |
|-----|--------|
| **`GET …/verification?numero=`** | Contrôle **document** (authenticité / statut global) — idéal scan douane ou contrôle rapide |
| **`GET …/{id}/eligibilite-utilisation?type=`** | Contrôle **métier** avant création d’une utilisation (soldes, transfert, clôture en cours) |

Un certificat peut être `etatVerification: VALIDE` mais `utilisableDouane: false` si un transfert a été exécuté — les `motifs` détaillent la raison.

---

## 9. Données de test (profil `dev` / `test`)

| Numéro | État attendu |
|--------|----------------|
| `CI-DEMO-SCEN-E` | `VALIDE` |
| `CI-DEMO-PRESIDENT` | `EN_COURS` |
| `CI-DEMO-SCEN-F` (après clôture manuelle) | `CLOTURE` |
| `CERT-INEXISTANT` | `INCONNU` |

Comptes : `dgd` / `entreprise` / `president` — mot de passe seed `123456`.
