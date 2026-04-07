# Transfert de crédit (P9) — guide front-end

Transfert **interne** sur le **même certificat** : passage d’un montant du **solde douanier** (`soldeCordon`) vers le **solde TVA intérieure** (`soldeTVA`), après validation DGTCP.  
Une seule **ligne métier** `transfert_credit` par certificat (réutilisée après un rejet).

---

## Statuts (`StatutTransfert`)

| Valeur | Usage front |
|--------|-------------|
| `DEMANDE` | Demande déposée ; l’entreprise peut **uploader** les pièces ; DGTCP peut valider/rejeter. |
| `EN_COURS` | Réservé / rare ; même traitement que `DEMANDE` pour l’upload. |
| `TRANSFERE` | Transfert exécuté (soldes mis à jour). **Plus** d’upload, **plus** de nouvelle demande sur ce certificat. |
| `REJETE` | DGTCP a refusé. L’entreprise peut **renvoyer** une demande via `POST` (même endpoint création) : la ligne est **réouverte** en `DEMANDE` et les **anciennes pièces actives sont désactivées**. |
| `VALIDE` | Non utilisé par le backend actuellement (ignorer ou masquer). |

---

## Types de documents (`TypeDocument` — query `type` sur multipart)

Obligatoires **avant** validation DGTCP (config base + vérification à la validation) :

1. `DEMANDE_MOTIVEE_TRANSFERT`
2. `DECLARATION_CLOTURE_DOUANE`
3. `JUSTIFICATIFS_CLOTURE_DOUANE`

---

## Modèle JSON (aligné sur `TransfertCreditDto`)

```typescript
type StatutTransfert =
  | "DEMANDE"
  | "EN_COURS"
  | "VALIDE"
  | "TRANSFERE"
  | "REJETE";

interface TransfertCreditDto {
  id: number;
  dateDemande: string; // ISO-8601 instant
  certificatCreditId: number;
  certificatNumero: string | null;
  entrepriseSourceId: number | null;
  montant: string; // décimal (BigDecimal sérialisé en string JSON souvent)
  operationsDouaneCloturees: boolean | null;
  statut: StatutTransfert;
}

interface CreateTransfertCreditRequest {
  certificatCreditId: number;
  montant: number; // ou string selon votre client HTTP
  operationsDouaneCloturees?: boolean; // doit être true pour accepter la validation DGTCP
}
```

```typescript
interface DocumentTransfertCreditDto {
  id: number;
  type: TypeDocumentTransfert; // voir enums ci-dessus
  nomFichier: string;
  chemin: string; // URL MinIO / fichier
  dateUpload: string;
  taille: number;
  version: number;
  actif: boolean;
}

type TypeDocumentTransfert =
  | "DEMANDE_MOTIVEE_TRANSFERT"
  | "DECLARATION_CLOTURE_DOUANE"
  | "JUSTIFICATIFS_CLOTURE_DOUANE";
```

---

## API (base `/api/transferts-credit`)

| Méthode | Chemin | Rôle / permission | Description |
|---------|--------|-------------------|-------------|
| `GET` | `/` | `transfert.solde.view` ou `transfert.dgtcp.queue.view` ou `archivage.view` | Liste (entreprise : ses certificats uniquement). |
| `GET` | `/{id}` | idem | Détail. |
| `GET` | `/by-certificat/{certificatCreditId}` | idem | Dernière demande pour ce certificat ; **404** si aucune ligne. Titulaire : vérifie que le certificat est à son entreprise. |
| `POST` | `/` | `transfert.submit` | Crée ou **réouvre** après `REJETE` (même corps). |
| `POST` | `/{id}/valider` | `transfert.dgtcp.update` | Exécute le transfert (soldes + `TRANSFERE`). |
| `POST` | `/{id}/rejeter` | `transfert.dgtcp.update` | `REJETE` (idempotent si déjà rejeté). |
| `GET` | `/{id}/documents` | lecture transfert | Liste des pièces. |
| `POST` | `/{id}/documents` | `transfert.submit` | `multipart/form-data` : `type` (enum), `file`. **Interdit** si statut ≠ `DEMANDE` / `EN_COURS`. |

**Headers** : `Authorization: Bearer <token>`.

---

## Parcours UI recommandé

1. **Fiche certificat OUVERT** : `GET .../by-certificat/{certId}`  
   - 404 → bouton « Initier un transfert » → formulaire + `POST /`.  
   - 200 → afficher statut, montant, `operationsDouaneCloturees`, liens documents.

2. **Demande `DEMANDE`** :  
   - Cases : confirmation clôture douane (`operationsDouaneCloturees` à la création).  
   - Upload des 3 types (ordre libre).  
   - Message si backend : `Dépôt de pièces interdit pour le statut` → rafraîchir le statut.

3. **DGTCP** : file d’attente via `GET /` (comptes DGTCP) ; valider / rejeter.

4. **Après `TRANSFERE`** : afficher solde mis à jour côté certificat (recharger le certificat) ; désactiver toute action transfert.

5. **Après `REJETE`** : message + `POST /` avec nouveau montant / clôture → repasse en `DEMANDE` ; **re-déposer** les 3 pièces.

6. **Erreurs métier fréquentes** (message utilisateur) :  
   - Transfert déjà exécuté pour ce certificat.  
   - Demande déjà en cours.  
   - Documents obligatoires manquants (à la validation).  
   - Opérations douane non clôturées.  
   - Solde douane insuffisant.

---

## Permissions seed (entreprise)

- `transfert.submit`, `transfert.amount.set`, `transfert.solde.view`

DGTCP : `transfert.dgtcp.queue.view`, `transfert.dgtcp.update`, etc.

---

## Fichiers backend de référence

- `TransfertCreditService`, `TransfertCreditController`
- `DocumentTransfertCreditService`
- `DataInitializer` (exigences documents `TRANSFERT_CREDIT`)
