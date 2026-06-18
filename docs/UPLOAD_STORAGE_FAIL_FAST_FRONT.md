# Stockage MinIO fail-fast — guide front

## Principe

Aucune **transition de statut** ni effet métier durable tant que l’upload vers le stockage objet (MinIO) n’a pas réussi dans la même requête API.

- Succès upload seul (`POST …/documents` → **201**) : document actif en base.
- Échec stockage (**503**, code `OBJECT_STORAGE_UNAVAILABLE`) : **rollback** — rien en base, statut inchangé.
- **Ne pas** avancer l’UI de façon optimiste avant la réponse HTTP.

Voir aussi : [UPLOAD_DOCUMENT_FRONT.md](./UPLOAD_DOCUMENT_FRONT.md) (paramètres `codeDocument`).

---

## Codes erreur et comportement UI

| HTTP | Code API | Action UI |
|------|----------|-----------|
| **503** | `OBJECT_STORAGE_UNAVAILABLE` | Message « Stockage indisponible », bouton **Réessayer**, **rester sur l’étape courante** |
| **400** | `VALIDATION_FAILED`, `BUSINESS_RULE_VIOLATION` | Corriger le formulaire / pièce manquante |
| **403** | `ROLE_FORBIDDEN` | Masquer l’action ou message droits insuffisants |

Pas de navigation vers l’étape suivante tant que la réponse n’est pas **2xx**.

---

## Upload document seul

Endpoints `POST /api/…/{id}/documents` (correction, certificat, utilisation, transfert, etc.) :

1. Envoyer `multipart/form-data` avec `file` + `codeDocument`.
2. **201** → rafraîchir la liste documents ; la version active est utilisable pour visa / validation.
3. **503** → aucun document créé ; ré-afficher le même écran d’upload.

---

## Actions combinées (fichier + statut)

| Action | Endpoint | Fichier | Règle UI |
|--------|----------|---------|----------|
| Visa DGD douane | `POST /api/utilisations-credit/{id}/visa-dgd` | `file` optionnel (`BULLETIN_ANNOTE`) | Succès API seulement → rafraîchir statut |
| Saisie chèque | `POST /api/utilisations-credit/{id}/cheque` | **obligatoire** (`CHEQUE_CERTIFIE`) | **503** → rester sur étape chèque |
| Quittances Trésor | `POST /api/utilisations-credit/{id}/quittances` | un par index (optionnel) | **503** → ne pas afficher étape suivante |
| Lettre adoption | `POST /api/demandes-correction/{id}/documents` | Président | puis `PATCH statut=ADOPTEE` |
| Offre corrigée / crédit intérieur | upload puis visa séparé | DGD / DGI | visa **uniquement** après **201** upload |

Guides détaillés :

- Correction visa documents : [CORRECTION_VISA_DOCUMENTS_FRONT.md](./CORRECTION_VISA_DOCUMENTS_FRONT.md)
- Lettre Président : [CORRECTION_PRESIDENT_LETTRE_ADOPTION_FRONT.md](./CORRECTION_PRESIDENT_LETTRE_ADOPTION_FRONT.md)
- Utilisation douane : [UTILISATION_CREDIT_DOUANE_FRONT.md](./UTILISATION_CREDIT_DOUANE_FRONT.md)

---

## GED dossier — lecture seule

L’endpoint **`POST /api/dossiers/{id}/documents`** (injection globale Président) a été **supprimé**.

- Écran dossier GED : **consultation** + liens vers fiches entité (demande, certificat, utilisation…).
- Retirer les boutons « Ajouter / remplacer » sur le dossier GED.
- Dépôt Président **uniquement** sur :
  - fiche correction → `LETTRE_ADOPTION` ;
  - fiche certificat → signature / certificat signé.

Ancien guide (obsolète) : [GED_PRESIDENT_INJECTION_FRONT.md](./GED_PRESIDENT_INJECTION_FRONT.md).

---

## Notifications

En cas de **503** (transaction rollback), **aucune** notification in-app ni e-mail n’est émise pour cette action.

Voir [NOTIFICATIONS_MAIL_FRONT.md](./NOTIFICATIONS_MAIL_FRONT.md).

---

## Checklist recette manuelle

1. Arrêter MinIO → upload offre corrigée DGD → **503**, pas de document en `GET …/documents`, visa DGD **400**.
2. MinIO OK → upload OK → visa OK.
3. Président : `LETTRE_ADOPTION` upload OK → `ADOPTEE` OK ; sans lettre → **400**.
4. `POST /api/dossiers/{id}/documents` → **404** ou **405** (endpoint supprimé).
5. GED dossier : affichage inchangé en lecture.
