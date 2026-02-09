# SGCI Backend – Spring Boot

Backend du **Système de Gestion des Crédits d'Impôt (SGCI)** – Commission Fiscale, Ministère des Finances.

## Prérequis

- **Java 17**
- **Maven 3.8+**

## Lancement

```bash
cd backend
mvn spring-boot:run
```

L’API est disponible sur **http://localhost:8080**.

**Documentation Swagger (OpenAPI)** : [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)  
- Tous les endpoints sont listés ; vous pouvez tester les appels depuis l’interface.
- Pour les routes protégées : **Authorize** → saisir le token (sans le préfixe "Bearer"), puis valider.

## Authentification (JWT)

Toutes les routes `/api/*` (sauf `/api/auth/**` et `/api/health`) nécessitent un **token JWT** dans l’en-tête :

```
Authorization: Bearer <votre_token>
```

- **POST** `/api/auth/login` – Connexion (body : `{"username":"admin","password":"admin"}`). Réponse : `token`, `userId`, `username`, `role`, `nomComplet`.
- **POST** `/api/auth/register` – Création de compte (body : `username`, `password`, `role`, `nomComplet`, `email`). Rôles : `PRESIDENT`, `DGD`, `DGTCP`, `DGI`, `DGB`, `AUTORITE_CONTRACTANTE`, `ENTREPRISE`.
- **GET** `/api/auth/me` – Utilisateur connecté (avec token).

**Compte par défaut au démarrage :** `admin` / `admin` (rôle PRESIDENT). À changer en production.

## Endpoints principaux

| Méthode | URL | Description |
|--------|-----|-------------|
| GET | `/api/health` | Santé de l’application (public) |
| POST | `/api/auth/login` | Connexion (public) |
| GET | `/api/auth/me` | Utilisateur connecté (authentifié) |
| GET/POST/PUT/DELETE | `/api/autorites-contractantes` | Autorités contractantes (authentifié) |
| GET/POST/PUT/DELETE | `/api/entreprises` | Entreprises |
| GET/POST | `/api/demandes-correction` | Demandes de correction d’offre fiscale |
| PATCH | `/api/demandes-correction/{id}/statut?statut=...` | Changer le statut d’une demande |
| GET | `/api/certificats-credit` | Certificats de crédit d’impôt |

## Base de données

- **Développement** : H2 en mémoire. Console H2 : http://localhost:8080/h2-console  
  - JDBC URL : `jdbc:h2:mem:sgcidb`  
  - User : `sa`, Password : (vide)

Pour utiliser **PostgreSQL** en production, ajouter la dépendance et configurer dans `application-prod.properties` :

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/sgci
spring.datasource.username=...
spring.datasource.password=...
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

## Structure des packages

```
ma.gov.finances.sgci
├── domain
│   ├── entity      # Entités JPA (DemandeCorrection, CertificatCredit, etc.)
│   └── enums       # StatutDemande, StatutCertificat, TypeDocument, etc.
├── repository      # JpaRepository
├── service        # Logique métier
├── web
│   ├── controller # REST API
│   ├── dto        # DTOs requête / réponse
│   └── exception  # Gestion globale des erreurs
└── SgciApplication.java
```

## Exemples d’appels (curl)

Créer une autorité contractante :

```bash
curl -X POST http://localhost:8080/api/autorites-contractantes \
  -H "Content-Type: application/json" \
  -d "{\"nom\":\"Ministère des Travaux Publics\",\"code\":\"MTP\",\"contact\":\"contact@mtp.gov.mr\"}"
```

Créer une entreprise :

```bash
curl -X POST http://localhost:8080/api/entreprises \
  -H "Content-Type: application/json" \
  -d "{\"raisonSociale\":\"Entreprise ABC\",\"nif\":\"NIF123\",\"adresse\":\"Nouakchott\"}"
```

Créer une demande de correction (avec token, id autorité 1) :

```bash
# 1. Se connecter et récupérer le token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.token')

# 2. Appel authentifié
curl -X POST http://localhost:8080/api/demandes-correction \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"autoriteContractanteId\":1}"
```
