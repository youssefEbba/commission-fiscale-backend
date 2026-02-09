## Documentation des endpoints SGCI

Toutes les routes d’API sont sous **`/api`**.  
Authentification via **JWT** pour toutes les routes sauf `/api/auth/**` et `/api/health`.

En-tête requis pour les endpoints protégés :

```text
Authorization: Bearer <token>
```

---

## 1. Authentification / Utilisateur courant

- **POST** `/api/auth/login`  
  - Body :
    ```json
    { "username": "admin", "password": "admin" }
    ```
  - Réponse :
    ```json
    {
      "token": "xxx",
      "type": "Bearer",
      "userId": 1,
      "username": "admin",
      "role": "PRESIDENT",
      "nomComplet": "Administrateur SGCI"
    }
    ```

- **POST** `/api/auth/register`  
  - Crée un compte **inactif** (en attente de validation admin).
  - Body :
    ```json
    {
      "username": "user1",
      "password": "secret",
      "role": "ENTREPRISE",
      "nomComplet": "Entreprise X",
      "email": "x@example.com"
    }
    ```
  - Le login sera refusé tant que `actif=false`.

- **GET** `/api/auth/me`  
  - Retourne l’utilisateur courant :
    ```json
    {
      "userId": 1,
      "username": "admin",
      "role": "PRESIDENT"
    }
    ```

---

## 2. Santé & Swagger

- **GET** `/api/health`  
  - Public, retourne :
    ```json
    { "status": "UP", "application": "SGCI Backend" }
    ```

- Swagger UI : `http://localhost:8080/swagger-ui.html`  
- OpenAPI JSON : `http://localhost:8080/v3/api-docs`

---

## 3. Autorités contractantes

Base : `/api/autorites-contractantes`

- **GET** `/api/autorites-contractantes`  
  Liste toutes les autorités.

- **GET** `/api/autorites-contractantes/{id}`  
  Détail d’une autorité.

- **POST** `/api/autorites-contractantes`  
  Body :
  ```json
  { "nom": "Ministère X", "code": "MX", "contact": "contact@mx.gov" }
  ```

- **PUT** `/api/autorites-contractantes/{id}`  
  Body identique au POST.

- **DELETE** `/api/autorites-contractantes/{id}`

---

## 4. Entreprises

Base : `/api/entreprises`

- **GET** `/api/entreprises`  
  Liste des entreprises.

- **GET** `/api/entreprises/{id}`

- **POST** `/api/entreprises`  
  Body :
  ```json
  {
    "raisonSociale": "Entreprise ABC",
    "nif": "NIF123",
    "adresse": "Nouakchott",
    "situationFiscale": "En règle"
  }
  ```

- **PUT** `/api/entreprises/{id}`  
  Mise à jour avec le même format.

- **DELETE** `/api/entreprises/{id}`

---

## 5. Demandes de correction

Base : `/api/demandes-correction`

- **GET** `/api/demandes-correction`  
  Liste toutes les demandes.

- **GET** `/api/demandes-correction/{id}`  
  Détail d’une demande.

- **GET** `/api/demandes-correction/by-autorite/{autoriteId}`  
  Liste par autorité contractante.

- **GET** `/api/demandes-correction/by-statut?statut=RECUE`  
  - `statut` ∈ `RECUE, INCOMPLETE, RECEVABLE, EN_EVALUATION, EN_VALIDATION, ADOPTEE, REJETEE, NOTIFIEE`

- **POST** `/api/demandes-correction`  
  Body :
  ```json
  { "autoriteContractanteId": 1 }
  ```

- **PATCH** `/api/demandes-correction/{id}/statut?statut=EN_EVALUATION`  
  - Change le statut en respectant le **workflow** (transitions contrôlées).

### Documents liés à une demande

- **GET** `/api/demandes-correction/{id}/documents`  
  Liste les documents (7 pièces du P1, etc.).

- **POST** `/api/demandes-correction/{id}/documents`  
  - `Content-Type: multipart/form-data`
  - Paramètres :
    - `type` : `TypeDocument` (`LETTRE_SAISINE`, `PV_OUVERTURE`, `OFFRE_FINANCIERE`, etc.)
    - `file` : fichier uploadé.

Exemple :

```bash
curl -X POST "http://localhost:8080/api/demandes-correction/1/documents?type=LETTRE_SAISINE" \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@/chemin/lettre.pdf"
```

---

## 6. Certificats de crédit

Base : `/api/certificats-credit`

- **GET** `/api/certificats-credit`  
  Liste des certificats.

- **GET** `/api/certificats-credit/{id}`

- **GET** `/api/certificats-credit/by-entreprise/{entrepriseId}`

- **GET** `/api/certificats-credit/by-statut?statut=OUVERT`  
  - `statut` ∈ `DEMANDE, EMIS, OUVERT, MODIFIE, CLOTURE, ANNULE`

- **POST** `/api/certificats-credit`  
  Crée un nouveau certificat (statut initial `DEMANDE`).
  Body :
  ```json
  {
    "entrepriseId": 1,
    "montantCordon": 1000000,
    "montantTVAInterieure": 200000,
    "dateValidite": "2026-12-31T00:00:00Z"
  }
  ```

- **PATCH** `/api/certificats-credit/{id}/statut?statut=OUVERT`  
  Met à jour le statut selon le workflow.

---

## 7. Utilisations de crédit

Base : `/api/utilisations-credit`

- **GET** `/api/utilisations-credit`  
  Liste toutes les utilisations (douane + TVA intérieure).

- **GET** `/api/utilisations-credit/{id}`

- **GET** `/api/utilisations-credit/by-certificat/{certificatCreditId}`

- **POST** `/api/utilisations-credit`  
  Crée une utilisation de crédit.

  **Douanière** (`type = DOUANIER`) :
  ```json
  {
    "type": "DOUANIER",
    "certificatCreditId": 1,
    "entrepriseId": 1,
    "montant": 50000,
    "numeroDeclaration": "DEC123",
    "numeroBulletin": "BUL456",
    "dateDeclaration": "2026-02-10T00:00:00Z",
    "montantDroits": 10000,
    "montantTVA": 40000,
    "enregistreeSYDONIA": true
  }
  ```

  **TVA intérieure** (`type = TVA_INTERIEURE`) :
  ```json
  {
    "type": "TVA_INTERIEURE",
    "certificatCreditId": 1,
    "entrepriseId": 1,
    "montant": 30000,
    "typeAchat": "ACHAT_LOCAL",
    "numeroFacture": "FAC789",
    "dateFacture": "2026-02-15T00:00:00Z",
    "montantTVAInterieure": 30000,
    "numeroDecompte": "DEC-001"
  }
  ```

- **PATCH** `/api/utilisations-credit/{id}/statut?statut=EN_VERIFICATION`  
  - `statut` ∈ `DEMANDEE, EN_VERIFICATION, VISE, VALIDEE, LIQUIDEE, APUREE, REJETEE`

---

## 8. Gestion des utilisateurs (validation admin)

Base : `/api/utilisateurs` – réservé au rôle **PRESIDENT**.

- **GET** `/api/utilisateurs`  
  Liste tous les comptes :
  ```json
  [
    {
      "id": 1,
      "username": "admin",
      "role": "PRESIDENT",
      "nomComplet": "Administrateur SGCI",
      "email": "admin@example.com",
      "actif": true
    }
  ]
  ```

- **GET** `/api/utilisateurs/pending`  
  Liste des comptes en attente de validation (`actif = false`).

- **PATCH** `/api/utilisateurs/{id}/actif?actif=true`  
  - Active (`true`) ou désactive (`false`) un compte.
  - Réponse : `204 No Content`.

> À l’inscription (`/auth/register`), les comptes sont créés avec `actif=false` et ne peuvent pas se connecter tant que l’admin ne les a pas validés.

---

## 9. Journal d’audit

Base : `/api/audit-logs`

- **GET** `/api/audit-logs`  
  Liste paginée des opérations (CREATE/UPDATE/DELETE).

  Paramètres optionnels :
  - `username` (string)
  - `entityType` (string, ex. `Entreprise`, `DemandeCorrection`)
  - `action` (`CREATE`, `UPDATE`, `DELETE`)
  - `dateFrom`, `dateTo` (ISO-8601, ex. `2026-02-09T00:00:00Z`)
  - `page` (int, défaut 0), `size` (int, défaut 20)

  Réponse (type Page) :
  ```json
  {
    "content": [
      {
        "id": 1,
        "timestamp": "2026-02-09T05:00:00Z",
        "userId": 1,
        "username": "admin",
        "action": "CREATE",
        "entityType": "Entreprise",
        "entityId": "1",
        "objectSnapshot": "{...}"
      }
    ],
    "totalElements": 10,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
  ```

---

## 10. Rappel santé

- **GET** `/api/health` – vérifie que l’application backend est en ligne.

