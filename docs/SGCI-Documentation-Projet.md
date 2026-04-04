---
title: SGCI — Documentation complète du projet
subtitle: Système de Gestion des Crédits d'Impôt — Backend Spring Boot
date: Mars 2026
pdf_output: SGCI-Documentation-Projet.pdf
---

# SGCI — Documentation complète du projet

> **Export PDF** : le fichier `SGCI-Documentation-Projet.pdf` (même dossier) est produit par `python scripts/generate_documentation_pdf.py` à la racine du dépôt (après `pip install fpdf2`).

**Système de Gestion des Crédits d'Impôt (SGCI)** — Commission fiscale, Ministère des Finances.

Ce document décrit l’ensemble du projet backend : objectifs métier, installation, architecture, API et workflows. Il est fourni pour impression ou archivage au format PDF.

---

## 1. Objectif du système

Le SGCI permet de :

- Gérer le **référentiel** (autorités contractantes, entreprises, conventions, référentiels de projet, marchés, délégués).
- Traiter les **demandes de correction d’offre fiscale** avec instruction parallèle des organismes (DGD, DGTCP, DGI, DGB), rejets temporaires et adoption / rejet.
- Mettre en place les **certificats de crédit d’impôt** après une demande admise (contrôle, visas, validation Président / DGTCP, ouverture du crédit).
- Gérer les **utilisations de crédit** (douane, TVA intérieure) jusqu’à liquidation ou apurement.
- Couvrir les processus annexes : **transferts**, **sous-traitance**, **clôture**, **avenants**, **GED**, **notifications**, **audit**, **administration des permissions**.

Les pièces jointes sont stockées dans **MinIO** (API compatible S3).

---

## 2. Chaîne métier (vue logique)

```
Référentiel
  Autorité contractante → Convention → Référentiel projet → Marché (+ délégués)
  Entreprise

Demande de correction (liée convention, entreprise, marché optionnel)
  → Décisions par organisme (VISA, REJET_TEMP, …)
  → Statuts : RECUE … → ADOPTEE / REJETEE → NOTIFIEE

Certificat de crédit (mise en place)
  → Création si demande ADOPTEE ou NOTIFIEE (règles métier)
  → Contrôle, visas, Président, DGTCP → statut OUVERT

Utilisation de crédit (certificat OUVERT)
  → Douane ou TVA intérieure → LIQUIDEE ou APUREE (ou REJETEE)
```

---

## 3. Prérequis techniques

| Composant | Version / détail |
|-----------|------------------|
| Java | 17 |
| Maven | 3.8+ |
| Base de données | **MySQL** (configuration par défaut dans `application.properties`) |
| Stockage fichiers | **MinIO** sur `http://127.0.0.1:9000` par défaut (Docker : `docker-compose.minio.yml` à la racine du projet) |

---

## 4. Installation et lancement (pas à pas)

1. Cloner le dépôt et ouvrir le dossier `commission-fiscale-backend`.
2. Installer et démarrer **MySQL** ; adapter `spring.datasource.*` dans `src/main/resources/application.properties`.
3. Démarrer **MinIO** (Docker : `docker compose -f docker-compose.minio.yml up -d`) et vérifier le bucket `documents`.
4. Lancer l’API :

```bash
mvn spring-boot:run
```

5. API : **http://localhost:8080**  
   Swagger : **http://localhost:8080/swagger-ui.html**

6. Au premier démarrage, **`DataInitializer`** charge permissions, utilisateurs de démo, exigences documentaires et jeux de données selon la configuration.

**Comptes** : `admin` / `admin` (ADMIN_SI) ; utilisateurs métier souvent **`123456`** (voir `DataInitializer`).

---

## 5. Configuration importante

Fichier : `src/main/resources/application.properties`

- **MySQL** : URL, utilisateur, mot de passe.  
- **JPA** : `spring.jpa.hibernate.ddl-auto` (souvent `create` en dev — à ne pas utiliser tel quel en production).  
- **JWT** : `app.jwt.secret`, `app.jwt.expiration-ms`.  
- **MinIO** : `minio.url`, `minio.access-key`, `minio.secret-key`, `minio.bucket` — surcharge par variables d’environnement `MINIO_URL`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`.  
- **Taille des fichiers** : `spring.servlet.multipart.max-file-size` (ex. 50 Mo).

---

## 6. Sécurité

- Authentification **JWT** : en-tête `Authorization: Bearer <token>`.
- Routes publiques typiques : `/api/auth/login`, `/api/auth/register`, `/api/health`.
- Le reste de `/api/*` : rôles et **permissions fines** (`@PreAuthorize`, authorities du type `correction.dgd.queue.view`, etc.).
- Le login renvoie les **permissions** utilisées par le frontend pour les menus.

---

## 7. Architecture du code

Package racine : **`mr.gov.finances.sgci`**

```
mr.gov.finances.sgci
├── SgciApplication.java
├── config/          # MinIO, sécurité, données initiales
├── domain/
│   ├── entity/      # Entités JPA
│   └── enums/       # Statuts, types de documents, rôles
├── repository/      # Spring Data JPA
├── service/         # Logique métier
├── workflow/        # Transitions de statut
├── security/        # JWT, filtres
└── web/
    ├── controller/  # REST
    ├── dto/
    └── exception/
```

---

## 8. Workflows — Demande de correction (`StatutDemande`)

Classe : `DemandeCorrectionWorkflow.java`

**Statuts** : RECUE, INCOMPLETE, RECEVABLE, EN_EVALUATION, EN_VALIDATION, ADOPTEE, REJETEE, NOTIFIEE, ANNULEE.

**Transitions autorisées (résumé)** :

| Depuis | Vers possibles |
|--------|----------------|
| RECUE | RECEVABLE, INCOMPLETE, REJETEE, EN_EVALUATION, ANNULEE |
| INCOMPLETE | RECEVABLE, REJETEE, ANNULEE |
| RECEVABLE | EN_EVALUATION |
| EN_EVALUATION | EN_VALIDATION, ADOPTEE |
| EN_VALIDATION | ADOPTEE, REJETEE |
| ADOPTEE | NOTIFIEE |
| REJETEE, NOTIFIEE, ANNULEE | (terminaux dans le graphe workflow) |

**Compléments métier** :

- Décisions **`DecisionCorrection`** par rôle (DGD, DGTCP, DGI, DGB) : notamment **VISA** et **REJET_TEMP**.
- Résolution manuelle d’un rejet temporaire : **`PUT /api/demandes-correction/decisions/{id}/resolve`**.
- Réponses / compléments : **`POST .../rejet-temp/reponses`**, upload de documents selon permissions.
- Contrôle des **pièces obligatoires** du processus **CORRECTION_OFFRE_FISCALE** aux passages clés (ex. RECEVABLE, EN_EVALUATION).

**API principales** : `GET/POST /api/demandes-correction`, `PATCH .../statut`, `GET/POST .../documents`, `GET/POST .../decisions`.

---

## 9. Workflows — Certificat de crédit (`StatutCertificat`)

Classe : `CertificatCreditWorkflow.java`

**Statuts** : EN_CONTROLE, INCOMPLETE, A_RECONTROLER, EN_VALIDATION_PRESIDENT, VALIDE_PRESIDENT, EN_OUVERTURE_DGTCP, OUVERT, MODIFIE, CLOTURE, ANNULE.

**Idée générale** : contrôle initial avec visas (DGI, DGD, DGTCP selon règles service), passage par validation Président et/ou DGTCP, puis **OUVERT** pour autoriser les utilisations. Des **REJET_TEMP** peuvent renvoyer vers INCOMPLETE puis A_RECONTROLER et retour EN_CONTROLE.

**API principales** : `GET/POST /api/certificats-credit`, `PATCH .../statut`, `PATCH .../montants` (DGTCP), documents, décisions certificat.

---

## 10. Workflows — Utilisation de crédit (`StatutUtilisation`)

Classe : `UtilisationCreditWorkflow.java`

**Statuts** : DEMANDEE, INCOMPLETE, A_RECONTROLER, EN_VERIFICATION, VISE, VALIDEE, LIQUIDEE, APUREE, REJETEE.

**Idée générale** : demande sur certificat **OUVERT** ; circuit douane ou TVA intérieure ; étapes de vérification / visa / validation ; **liquidation** (douane) ou **apurement TVA** (TVA intérieure) via endpoints dédiés.

**API** : préfixe `/api/utilisations-credit` (création, statuts, documents, décisions, liquidation, apurement). Détail : voir aussi le fichier **`UTILISATION_CREDIT_API.md`** à la racine du projet.

---

## 11. Autres modules API (aperçu)

| Domaine | Préfixe / ressource |
|---------|---------------------|
| Conventions, référentiels, marchés | `/api/conventions`, `/api/referentiels-projet`, `/api/marches` |
| Transferts, sous-traitance, clôture | `/api/transferts-credit`, `/api/sous-traitances`, `/api/clotures-credit` |
| Avenants | `/api/avenants` |
| GED | `/api/dossiers` |
| Notifications | `/api/notifications` |
| Audit | `/api/audit-logs` |
| Admin permissions / utilisateurs | `/api/admin/permissions`, `/api/utilisateurs` |
| Référentiel (devises, taux, forex) | `/api/devises`, `/api/taux-change`, `/api/forex` |

La liste exhaustive et les corps de requête sont dans **Swagger**.

---

## 12. Frontend

Un client **React / Vite** peut être utilisé avec la même API (variable `VITE_API_BASE`). Exemple de dossier dans le dépôt : `cs-front-for-cursor/commission-fiscale-bd18374b`.

---

## 13. Tests et recette

- **Maven** : `mvn test` (profil test, H2) — tests d’intégration de workflows (`WorkflowSmokeIT`, `AllWorkflowsIT`, `RejetWorkflowsIT`, etc.).
- **Recette manuelle** : fichier **`TEST_MANUEL_COMPLET.md`** à la racine du dépôt (grille par endpoint, graphes d’états, priorités).

---

## 14. Fichiers utiles dans le dépôt

| Fichier | Rôle |
|---------|------|
| `README.md` | Aide-mémoire lancement / auth |
| `TEST_MANUEL_COMPLET.md` | Plan de tests manuels |
| `UTILISATION_CREDIT_API.md` | API utilisations de crédit |
| `docker-compose.minio.yml` | MinIO local |
| `src/main/java/.../workflow/*.java` | Vérité métier des transitions de statut |

---

## 15. Rappels production

- Ne pas utiliser `ddl-auto=create` en production.
- Renforcer `app.jwt.secret`, mots de passe base et MinIO.
- Configurer CORS et HTTPS selon l’infrastructure.

---

*Document généré pour export PDF — SGCI Backend.*
