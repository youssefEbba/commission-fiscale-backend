# GED — Types de documents paramétrables par workflow

## Objectif

Permettre à l’administrateur SI d’ajouter de nouveaux types de pièces (ex. « Certificat d’utilisation douane ») **sans redéployer** le backend, et de les associer à un processus métier (workflow GED).

**Règle d’upload** : un dépôt n’est accepté que si le `codeDocument` est déclaré dans `document_requirement` pour le processus concerné.

## Modèle

| Table / entité | Rôle |
|----------------|------|
| `referentiel_type_document` | Catalogue global (`code`, `libelle`, `libelleAr`, `actif`, `systeme`) |
| `document_requirement` | Lien processus ↔ type (`processus`, `codeDocument`, `obligatoire`, `typesAutorises`, …) |
| Tables `document_*` | Pièces déposées (`code_document` VARCHAR) |

Les codes suivent le motif `[A-Z0-9_]+` (ex. `FACTURE`, `BULLETIN_LIQUIDATION`).

## API admin

### Référentiel des types

| Méthode | URL | Permission |
|---------|-----|------------|
| GET | `/api/referentiel/types-document?actif=true` | `document.types.view` |
| GET | `/api/referentiel/types-document/{code}` | `document.types.view` |
| POST | `/api/referentiel/types-document` | `document.types.manage` |
| PUT | `/api/referentiel/types-document/{code}` | `document.types.manage` |
| DELETE | `/api/referentiel/types-document/{code}` | `document.types.manage` (interdit si `systeme=true`) |

Corps création / mise à jour :

```json
{
  "code": "CERTIFICAT_UTILISATION_DOUANE",
  "libelle": "Certificat d'utilisation douane",
  "libelleAr": null,
  "actif": true
}
```

### Exigences par processus

| Méthode | URL | Permission |
|---------|-----|------------|
| GET | `/api/document-requirements?processus=UTILISATION_CI_DOUANE` | `document.requirements.view` |
| POST | `/api/document-requirements` | `document.types.manage` |
| PUT | `/api/document-requirements/{id}` | `document.types.manage` |
| DELETE | `/api/document-requirements/{id}` | `document.types.manage` |

Corps exemple :

```json
{
  "processus": "UTILISATION_CI_DOUANE",
  "codeDocument": "CERTIFICAT_UTILISATION_DOUANE",
  "libelle": "Certificat d'utilisation douane",
  "obligatoire": false,
  "typesAutorises": ["PDF"],
  "description": "Scan du certificat après liquidation",
  "ordreAffichage": 10
}
```

Si `codeDocument` n’existe pas encore dans le référentiel et `libelle` est fourni, le type est **créé automatiquement** (création inline).

## Flux admin typique

1. Créer le type dans le référentiel (ou le laisser se créer via l’étape 2).
2. `POST /api/document-requirements` pour le processus cible (`UTILISATION_CI_DOUANE`, `TRANSFERT_CREDIT`, etc.).
3. Le front GED appelle `GET /api/document-requirements?processus=…` pour construire la checklist d’upload.

## Upload (front)

Remplacer le paramètre enum `type` par **`codeDocument`** (même valeur métier, ex. `FACTURE`).

Compatibilité temporaire sur certains endpoints rejet temporaire : `type`, `typeDocument` ou `codeDocument` (priorité à `codeDocument`).

Exemple :

```
POST /api/utilisations-credit/{id}/documents?codeDocument=FACTURE
```

Sans ligne `document_requirement` correspondante → **400** « Type non paramétré pour ce processus ».

## Permissions

| Code | Rôle typique |
|------|----------------|
| `document.types.view` | Lecture référentiel + exigences |
| `document.types.manage` | CRUD référentiel + exigences |
| `document.requirements.view` | Lecture seule des exigences (déjà utilisée par les acteurs) |

`ADMIN_SI` reçoit `document.types.view` et `document.types.manage` au démarrage (`DataInitializer`).

## Migration MySQL

Script : `scripts/migrate-type-document-referentiel-mysql.sql`  
À exécuter sur les bases existantes avant ou après déploiement selon `ddl-auto`.

## Seed

Au démarrage : `ReferentielTypeDocumentService.seedFromEnumIfEmpty()` puis `DataInitializer.seedDocumentRequirements()` (codes identiques aux anciennes valeurs d’enum).

## Phase 2 (hors scope actuel)

Règles conditionnelles TVA intérieure (FACTURE + DECLARATION_TVA pour achat local) restent codées dans `UtilisationCreditService` ; une évolution possible : champ `condition` sur `document_requirement`.
