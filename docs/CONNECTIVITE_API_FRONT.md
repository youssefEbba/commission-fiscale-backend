# Connectivité API — guide front (Failed to fetch)

Ce guide répond à l’erreur **« Impossible de charger les demandes »** (ou toute liste vide avec toast d’erreur) lorsque la console affiche :

```text
TypeError: Failed to fetch
```

Sur **toutes** les requêtes (`/notifications`, `/demandes-correction`, etc.), la cause est presque toujours **réseau / URL API**, pas un bug de rôle (ex. Président).

---

## 1. Symptôme vs cause réelle

| Ce que l’utilisateur voit | Cause fréquente |
|---------------------------|-----------------|
| Président ne voit pas les demandes | `GET /api/demandes-correction` n’a **jamais** répondu (fetch échoué) |
| Notifications vides + erreur | Même origine |
| Erreur après changement de machine / lendemain | Tunnel **ngrok expiré** ou URL obsolète dans `apiConfig.ts` |

**Ce n’est en général pas** un problème de permission `403` : un 403 s’afficherait dans l’onglet Network avec une réponse JSON du backend.

---

## 2. Vérifications (ordre recommandé)

### 2.1 Backend Spring Boot

- Le processus `mvn spring-boot:run` (ou jar) doit tourner.
- Test local : ouvrir `http://localhost:8080/swagger-ui.html` ou :

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/login
```

(attendu : `405` ou `400`, pas timeout)

### 2.2 URL dans `src/lib/apiConfig.ts`

Fichier central :

```typescript
export const API_BASE = "https://XXXX.ngrok-free.app/api";
export const WS_BASE = "https://XXXX.ngrok-free.app/ws";
```

- Les tunnels **ngrok gratuits changent d’URL** à chaque redémarrage.
- Vérifier que **front et backend** pointent vers le **même** tunnel actif.
- Ouvrir `https://XXXX.ngrok-free.app/api` dans le navigateur : la page ne doit pas être « site inaccessible ».

Headers ngrok (déjà requis dans le projet) :

```typescript
"ngrok-skip-browser-warning": "true"
```

### 2.3 CORS

Le backend expose `@CrossOrigin(origins = "*")` sur les controllers. Si `Failed to fetch` persiste avec backend joignable, vérifier extension navigateur / mixed content (HTTP front → HTTPS ngrok).

### 2.4 Token

Si fetch réussit mais liste vide avec **401/403** dans Network → reconnecter l’utilisateur (token expiré ou permissions). Ce cas est **distinct** de `Failed to fetch`.

---

## 3. Cas Président — demandes de correction

### Front (attendu)

`src/pages/Demandes.tsx` :

```typescript
await demandeCorrectionApi.getAll(); // GET /api/demandes-correction
```

### Backend (confirmé)

| Élément | Valeur |
|---------|--------|
| Endpoint | `GET /api/demandes-correction` |
| Permission | `correction.president.queue.view` (entre autres) |
| Rôle `PRESIDENT` | `assignAllPermissions` → accès complet |
| Données | Toutes les demandes sauf statut `BROUILLON` |

Si la requête **atteint** le backend, le Président reçoit la liste. Si `Failed to fetch`, le rôle n’est pas en cause.

---

## 4. Procédure après redémarrage ngrok

1. Lancer ngrok : `ngrok http 8080`
2. Copier l’URL HTTPS affichée
3. Mettre à jour **`apiConfig.ts`** (front) : `API_BASE` et `WS_BASE`
4. Recharger l’app front (hard refresh)
5. Se reconnecter (login)

Recommandation long terme : variables d’environnement Vite :

```typescript
export const API_BASE = import.meta.env.VITE_API_BASE ?? "http://localhost:8080/api";
```

---

## 5. Diagnostic rapide dans le navigateur

1. F12 → **Network**
2. Recharger la page Demandes
3. Filtrer `demandes-correction`

| Statut Network | Interprétation |
|----------------|----------------|
| *(failed)* / rouge | URL ngrok morte, backend arrêté, CORS réseau |
| **401** | Token manquant / expiré → reconnecter |
| **403** | Permission (rare pour PRESIDENT) |
| **200** + `[]` | Backend OK, aucune demande en base (hors brouillon) |
| **200** + JSON | Front OK ; filtrer côté UI si liste semble vide |

---

## 6. Développement local sans ngrok

Front et backend sur la même machine :

```typescript
export const API_BASE = "http://localhost:8080/api";
export const WS_BASE = "http://localhost:8080/ws";
```

Lancer le front avec proxy Vite vers `:8080` si besoin d’éviter CORS en dev.

---

## 7. Fichiers front concernés

| Fichier | Rôle |
|---------|------|
| `src/lib/apiConfig.ts` | **URL API** — premier fichier à vérifier |
| `src/lib/api.ts` | Helpers `apiFetch`, headers ngrok |
| `src/pages/Demandes.tsx` | Chargement liste (`getAll`) |

---

## 8. Checklist « Président ne voit rien »

- [ ] Backend démarré (`8080`)
- [ ] `API_BASE` = tunnel ngrok **actif** (test dans le navigateur)
- [ ] Pas de `Failed to fetch` dans la console
- [ ] Requête `GET /api/demandes-correction` en **200** dans Network
- [ ] Utilisateur reconnecté après changement d’URL
- [ ] Des demandes existent en base (statut ≠ `BROUILLON`)
