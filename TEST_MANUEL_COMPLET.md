# Plan de tests manuels — SGCI Backend (couverture fonctionnelle)

**Objectif.** Servir de grille pour un testeur manuel sur l’API (`/api`) et, en parallèle, sur le front branché sur la même base.

**Limite méthodologique.** On ne peut pas exécuter « toutes les possibilités » au sens combinatoire (nombre infini de données et de séquences). Ce document liste **toutes les zones fonctionnelles**, **les statuts et transitions prévus par le code**, et des **cas obligatoires** + **négatifs**. Pour les workflows à graphe d’états, valider au minimum : **chemin nominal**, **chaque type de sortie de rejet / annulation**, et **un refus de transition illégale**.

**Automatisation déjà en place** (profil `test`, H2) :

- `WorkflowSmokeIT` — accès listes entreprise / DGD  
- `AllWorkflowsIT` — chaîne mise en place + demande NOTIFIEE + utilisation + rejet  
- `RejetWorkflowsIT` — REJET_TEMP certificat / utilisation + REJETEE douane  

Commande : `mvn test`

---

## 1. Prérequis

| Élément | Détail |
|--------|--------|
| API | Base URL (ex. `http://localhost:8080`) |
| Auth | `POST /api/auth/login` → JWT Bearer |
| Comptes seed (mot de passe `123456`, sauf `admin` / `admin`) | `entreprise`, `ac`, `dgd`, `dgtcp`, `dgi`, `dgb`, `president`, `admin`, `test` |
| Outils | Postman / Insomnia / curl + onglet Réseau du navigateur |
| Traçabilité | Pour chaque cas : ID entités, rôle, statut avant/après, code HTTP, extrait message erreur |

---

## 2. Cartographie API (à cocher par endpoint / rôle)

### 2.1 Authentification & profil

| ID | Cas | Méthode | Chemin | Rôles / notes |
|----|-----|---------|--------|----------------|
| A1 | Login OK | POST | `/api/auth/login` | Body `username`, `password` |
| A2 | Login refusé | POST | idem | Mauvais mot de passe → 401 |
| A3 | Register | POST | `/api/auth/register` | Selon règles métier actuelles |
| A4 | Moi | GET | `/api/auth/me` | Avec Bearer |

### 2.2 Santé

| ID | Cas | GET | `/api/health` |

### 2.3 Référentiel administratif

| Zone | Endpoints principaux | Tester |
|------|----------------------|--------|
| Autorités contractantes | `GET/POST/PUT/DELETE /api/autorites-contractantes` | CRUD + 403 sans droit |
| Entreprises | `/api/entreprises` | idem |
| Bailleurs | `/api/bailleurs` | liste + création |
| Devises | `/api/devises` | liste + création |
| Taux de change | `/api/taux-change` | lecture |
| Forex | `/api/forex/convert`, `/api/forex/rate` | paramètres valides / invalides |
| Exigences documents | `/api/document-requirements` | CRUD admin |
| Délégues | `/api/delegues` | liste, création, `PATCH .../actif` |

### 2.4 Convention & référentiel projet & marché

| ID | Ressource | Actions |
|----|-----------|---------|
| C1 | `/api/conventions` | liste, détail, `by-statut`, création, `PATCH statut` (EN_ATTENTE → VALIDE / REJETE), documents GET + POST multipart |
| R1 | `/api/referentiels-projet` | idem pattern statuts EN_ATTENTE / VALIDE / REJETE + documents |
| M1 | `/api/marches` | CRUD, `by-correction/{id}`, `PATCH assign`, délégués POST/DELETE, documents |

### 2.5 Dossiers GED

| GET | `/api/dossiers`, `/api/dossiers/{id}` | visibilité par rôle |

### 2.6 Demande de correction

| Méthode | Chemin | Cas |
|---------|--------|-----|
| GET | `/api/demandes-correction` (+ filtres by-entreprise, by-autorite, by-delegue, by-statut) | filtrage correct par rôle |
| GET/POST | `/{id}/documents` | liste + upload types `TypeDocument` processus correction |
| POST | `/api/demandes-correction` | création (champs obligatoires) |
| PATCH | `/{id}/statut` | **chaque transition autorisée** (voir §3.1) + `motifRejet` si REJETEE + `decisionFinale` si applicable |
| GET/POST | `/api/demandes-correction/{id}/decisions` | VISA, REJET_TEMP (+ motif + `documentsDemandes`) |
| POST | `.../decisions/{id}/rejet-temp/reponses` | message entreprise / complément |
| PUT | `.../decisions/{id}/resolve` | même rôle que l’émetteur du REJET_TEMP |

### 2.7 Certificat de crédit (mise en place)

| Méthode | Chemin | Cas |
|---------|--------|-----|
| GET | liste, `/{id}`, `by-entreprise`, `by-statut` | périmètre entreprise vs services |
| POST | `/api/certificats-credit` | création si demande ADOPTEE/NOTIFIEE + marché |
| PATCH | `/{id}/montants` | DGTCP, montants > 0 |
| PATCH | `/{id}/statut` | transitions §3.2 (dont OUVERT, ANNULE, EN_CONTROLE, etc.) |
| GET/POST | `/{id}/documents` | types processus mise en place |
| GET | `/{id}/tva-stock` | si utilisation douane a alimenté le stock |
| GET/POST | `/{id}/decisions`, resolve, rejet-temp réponses | comme § rejets certificat |

### 2.8 Utilisation de crédit

| Méthode | Chemin | Cas |
|---------|--------|-----|
| GET | liste, `/{id}`, `by-certificat/{id}` | |
| POST | `/api/utilisations-credit` | type DOUANIER / TVA_INTERIEURE, certificat **OUVERT** |
| PATCH | `/{id}/statut` | transitions §3.3 (dont REJETEE court-circuit douane) |
| POST | `/{id}/liquidation-douane` | DGTCP → LIQUIDEE |
| POST | `/{id}/apurement-tva` | DGTCP → APUREE |
| GET/POST | `/{id}/documents` | processus douane / TVA intérieure |
| Décisions | `/{id}/decisions`, resolve, réponses rejet temp | DGD/DGTCP/DGI selon type |

### 2.9 Transfert, sous-traitance, clôture, avenant

| Ressource | Points à tester |
|-----------|-----------------|
| `/api/transferts-credit` | liste, détail, création, `valider` / `rejeter`, documents |
| `/api/sous-traitances` | liste, détail, création, `onboarding`, `autoriser` / `refuser`, documents |
| `/api/clotures-credit` | `eligible`, création, `propositions`, `valider` / `rejeter` / `finaliser`, documents |
| `/api/avenants/{id}/documents` | liste + upload |

### 2.10 Notifications & audit & admin

| Ressource | Cas |
|-----------|-----|
| `/api/notifications` | liste, unread-count, read, read-all |
| `/api/audit-logs` | lecture filtrée (admin / audit) |
| `/api/admin/permissions` | rôles, GET/POST/DELETE assignations |
| `/api/utilisateurs` | liste, sous-traitants, pending, `PATCH actif` |

---

## 3. Graphes d’états (transitions à valider manuellement)

### 3.1 Demande de correction (`StatutDemande`)

Valeurs : `RECUE`, `INCOMPLETE`, `RECEVABLE`, `EN_EVALUATION`, `EN_VALIDATION`, `ADOPTEE`, `REJETEE`, `NOTIFIEE`, `ANNULEE`.

**À tester.**

- Nominal : chemin vers `ADOPTEE` puis `NOTIFIEE` (Président).
- Rejet définitif : vers `REJETEE` avec **motif** (depuis états autorisés, ex. `EN_VALIDATION`).
- `REJET_TEMP` sur décision → demande `INCOMPLETE` → resolve → retour métier (ex. `RECEVABLE`).
- `ANNULEE` par acteurs autorisés.
- **Négatif** : transition non listée dans le workflow → erreur claire.
- **Documents** : passages exigeant pièces obligatoires (`RECEVABLE`, `EN_EVALUATION`) → blocage si manquantes.

### 3.2 Certificat (`StatutCertificat`)

Valeurs : `EN_CONTROLE`, `INCOMPLETE`, `A_RECONTROLER`, `EN_VALIDATION_PRESIDENT`, `VALIDE_PRESIDENT`, `EN_OUVERTURE_DGTCP`, `OUVERT`, `MODIFIE`, `CLOTURE`, `ANNULE`.

**À tester.**

- Nominal : montants DGTCP → 3 × `VISA` (DGI, DGD, DGTCP) → auto `EN_VALIDATION_PRESIDENT` → `OUVERT` (Président ou chemin DGTCP).
- `REJET_TEMP` → `INCOMPLETE` → resolve → `A_RECONTROLER` → retour `EN_CONTROLE` (DGI/DGD/DGTCP).
- **Négatif** : `EN_VALIDATION_PRESIDENT` ne doit pas être posé manuellement si l’API l’interdit ; `VISA` DGTCP sans montants.
- `ANNULE` (rôles autorisés) puis nouvelle mise en place sur même demande (si métier + BD).

### 3.3 Utilisation (`StatutUtilisation`)

Valeurs : `DEMANDEE`, `INCOMPLETE`, `A_RECONTROLER`, `EN_VERIFICATION`, `VISE`, `VALIDEE`, `LIQUIDEE`, `APUREE`, `REJETEE`.

**À tester (douane).**

- `DEMANDEE` → `EN_VERIFICATION` (DGD) **avec documents requis**.
- `EN_VERIFICATION` → `VISE` / `REJETEE`.
- Chemin jusqu’à `LIQUIDEE` via **liquidation** (pas seulement PATCH statut).
- `REJET_TEMP` → `INCOMPLETE` → resolve → `A_RECONTROLER`.
- `REJETEE` depuis `DEMANDEE` (court-circuit).

**À tester (TVA intérieure).**

- Flux DGTCP/DGI : vérification, validation, `apurement-tva` → `APUREE`.
- Rejets / REJET_TEMP symétriques aux permissions `utilisation.interieur.*`.

---

## 4. Matrice transversale (à appliquer à chaque module)

| Type | Exemples |
|------|----------|
| **401** | Sans token, token expiré |
| **403** | Bon token mais rôle sans permission |
| **400** | Body invalide, enum inconnu, violation validation |
| **404** | ID inexistant |
| **Fichiers** | multipart type MIME, taille max, type document hors référentiel |
| **Cohérence** | solde certificat après liquidation / apurement ; pas double action idempotente interdite |
| **Notifications** | réception après changement de statut (si WebSocket / polling activé) |

---

## 5. Frontend (si `commission-fiscale-bd18374b` ou équivalent)

Pour chaque page : chargement, erreur API affichée, navigation, formulaires obligatoires.

| Zone | Pages / flux |
|------|----------------|
| Auth | Login, Register, déconnexion |
| Correction | Liste demandes, détail, upload pièces, statuts |
| Mise en place | Certificats, détail, documents |
| Utilisation | Création douane / TVA, détail, statuts |
| Notifications | Liste, marquer lu |
| Permissions | menus masqués selon `permissions` du login |

---

## 6. Priorisation recommandée (sprint recette)

1. Auth + health + login tous rôles seed.  
2. Workflow correction (nominal + REJET_TEMP + REJETEE).  
3. Workflow certificat (nominal + REJET_TEMP).  
4. Workflow utilisation douane (nominal + REJET_TEMP + REJETEE + liquidation).  
5. Workflow utilisation TVA intérieure (nominal + apurement).  
6. Convention / référentiel / marché (validation rejet).  
7. Transfert, sous-traitance, clôture.  
8. Admin permissions, utilisateurs, audit.  

---

## 7. Références code

- Workflows : `workflow/DemandeCorrectionWorkflow.java`, `CertificatCreditWorkflow.java`, `UtilisationCreditWorkflow.java`  
- Seed : `config/DataInitializer.java`  
- Liste contrôleurs : `web/controller/*.java`  

Document généré pour accompagner la recette ; à adapter (URL, jeux de données métier) selon environnement (dev / recette / prod).
