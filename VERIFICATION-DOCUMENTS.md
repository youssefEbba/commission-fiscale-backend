# Vérification Backend SGCI vs documents du projet

Références : *Rapport de Spécification des Besoins*, *Cahier des Charges*, *Diagramme de Classes*, *11 Processus métier*.

---

## ✅ Déjà en place

| Élément | Statut | Détail |
|--------|--------|--------|
| **Entités JPA** | ✅ | Toutes les entités du diagramme de classes : DemandeCorrection, AutoriteContractante, Entreprise, Document, FeuilleEvaluation, LettreCorrection, CertificatCredit, Item, UtilisationCredit (Douaniere, TVAInterieure), Avenant, TransfertCredit, SousTraitance, ClotureCredit, Utilisateur |
| **Enums** | ✅ | StatutDemande, StatutCertificat, StatutUtilisation, TypeDocument, TypeAvenant, MotifCloture, Role, etc. |
| **Authentification** | ✅ | JWT, rôles (PRESIDENT, DGD, DGTCP, DGI, DGB, etc.) |
| **Workflow** | ✅ | Transitions contrôlées pour DemandeCorrection, CertificatCredit, UtilisationCredit |
| **Audit** | ✅ | Journal en base : qui, quand, action (CREATE/UPDATE/DELETE), type d’objet, id, snapshot JSON |
| **CRUD Autorité contractante** | ✅ | GET/POST/PUT/DELETE `/api/autorites-contractantes` |
| **CRUD Entreprise** | ✅ | GET/POST/PUT/DELETE `/api/entreprises` |
| **Demande de correction** | ✅ | Création, liste, par autorité/statut, changement de statut (workflow) |
| **Certificat de crédit** | ✅ | Liste, par entreprise/statut, changement de statut (workflow) |
| **Utilisation de crédit** | ✅ | Liste, par certificat, changement de statut (workflow) |
| **Consultation audit** | ✅ | GET `/api/audit-logs` avec filtres (user, entityType, action, dates) |
| **Swagger** | ✅ | `/swagger-ui.html` |
| **Santé** | ✅ | GET `/api/health` |

---

## ⚠️ À ajouter / à compléter (par rapport aux documents)

### 1. **Upload de documents (P1, P4)**  
- **Spec** : P1 demande 7 pièces (lettre saisine, PV ouverture, attestation fiscale, offre financière, tableau modèle, DAO+DQE, liste items). P4 demande 5 pièces.
- **Implémenté** : `POST /api/demandes-correction/{id}/documents` (multipart, param `type` = TypeDocument), `GET /api/demandes-correction/{id}/documents`. Fichiers stockés dans `app.upload.dir` (défaut: `uploads`).

### 2. **Création d’un certificat de crédit (P4–P5)**  
- **Spec** : Après demande de mise en place (P4), émission du certificat (P5) : ouverture crédit, montants, entreprise.
- **Implémenté** : `POST /api/certificats-credit` (body: entrepriseId, montantCordon, montantTVAInterieure, dateValidite, etc.), numéro auto (CERT-...).

### 3. **Feuille d’évaluation et items (P2)**  
- **Spec** : DGD évalue, calcule crédit cordon + TVA, produit une feuille avec montants et détail des items.
- **Manque** : Pas d’API pour créer/mettre à jour une `FeuilleEvaluation` et ses `Item` rattachés à une `DemandeCorrection`.
- **À faire** : Création/mise à jour de la feuille (montantCordon, montantTVA, montantTotal, signee) + CRUD des items (nomenclature, quantités, valeurs) liés à une demande.

### 4. **Lettre de correction (P3)**  
- **Spec** : Génération et signature de la lettre (crédit cordon + TVA), notification.
- **Manque** : Pas d’API pour créer une `LettreCorrection` à partir d’une feuille / demande, ni pour gérer signee/notifiee.
- **À faire** : Création `LettreCorrection` liée à une `FeuilleEvaluation` / DemandeCorrection, champs signee, notifiee, dates.

### 5. **Création d’une utilisation de crédit (P6–P7)**  
- **Spec** : L’entreprise soumet une demande d’utilisation (douanière avec 7 pièces, ou TVA intérieure).
- **Implémenté** : `POST /api/utilisations-credit` (body: type DOUANIER ou TVA_INTERIEURE, certificatCreditId, entrepriseId, montant + champs spécifiques douane/TVA).

### 6. **Avenant, transfert, sous-traitance, clôture (P8–P11)**  
- **Spec** : Modification du crédit (avenant), transfert, sous-traitance, clôture.
- **Manque** : Pas d’API dédiée pour créer/lister `Avenant`, `TransfertCredit`, `SousTraitance`, `ClotureCredit` par certificat.
- **À faire** : Endpoints du type :  
  - `POST/GET /api/certificats-credit/{id}/avenants`  
  - `POST/GET /api/certificats-credit/{id}/transfert`  
  - `POST/GET /api/certificats-credit/{id}/sous-traitance`  
  - `POST/GET /api/certificats-credit/{id}/cloture`

### 7. **Contrôles métier**  
- **Spec** : Délai 48h pour compléter un dossier (P2), délai 3 jours pour réponse après notification (P3).
- **Manque** : Pas de règles métier (dates, automatismes) dans le backend.
- **À faire** : Selon besoin : champs date limite, jobs ou règles en service pour rejet auto / validation auto.

### 8. **Droits par rôle**  
- **Spec** : Matrice acteurs/processus (qui fait quoi).
- **Manque** : Les endpoints ne restreignent pas encore par rôle (DGD, DGTCP, etc.).
- **À faire** : Restreindre certaines actions par rôle (ex. seul DGTCP peut ouvrir le crédit, seul DGD peut passer en évaluation, etc.) avec `@PreAuthorize` ou équivalent.

---

## Synthèse

- **Backend déjà aligné** avec le modèle de données et les principaux flux (demande → statuts, certificat → statuts, utilisation → statuts, audit, auth).
- **À ajouter pour coller aux documents** :  
  - Upload de documents et lien aux demandes.  
  - Création certificat, feuille d’évaluation + items, lettre de correction.  
  - Création des utilisations de crédit (douane / TVA).  
  - APIs pour avenant, transfert, sous-traitance, clôture.  
  - Optionnel : règles de délais (48h, 3 jours) et droits par rôle.

Ce fichier sert de **checklist** : chaque section “À faire” peut être traitée puis cochée au fur et à mesure des développements.
