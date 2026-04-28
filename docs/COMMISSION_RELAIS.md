# Commission relais (impersonation)

## Rôle et compte de démonstration

- **Rôle applicatif** : `COMMISSION_RELAIS` (enum `Role`).
- **Utilisateur seed** (profil `local`, via `DataInitializer`) :
  - **Username** : `commission_relais`
  - **Mot de passe** : `123456`
  - Aucune entreprise ni autorité contractante n’est liée au compte en base : le périmètre métier vient du **JWT d’impersonation** (voir ci‑dessous).

## Permissions (référentiel / UI)

Ces codes sont assignés au rôle `COMMISSION_RELAIS` en base pour la lisibilité (administration, future UI). **Ils ne sont pas utilisés comme garde sur les endpoints** : pendant l’impersonation le JWT ne contient que les permissions ENTREPRISE ou AUTORITE_CONTRACTANTE ; l’accès aux routes `/api/commission-relais/**` est contrôlé en **chargeant l’utilisateur par `userId` du JWT** et en exigeant `role == COMMISSION_RELAIS` en base.

| Code | Description |
|------|-------------|
| `commission.relais.list.entreprises` | Lister les entreprises (recherche paginée) |
| `commission.relais.list.autorites` | Lister les autorités contractantes |
| `commission.relais.impersonate.entreprise` | Obtenir un JWT « entreprise » |
| `commission.relais.impersonate.autorite` | Obtenir un JWT « autorité contractante » |
| `commission.relais.release` | Revenir au JWT commission relais |

Le rôle reçoit aussi `document.requirements.view` pour la cohérence avec les écrans qui affichent les documents requis.

## API REST

Base : `/api/commission-relais` (JWT authentifié ; compte en base = `COMMISSION_RELAIS`).

| Méthode | Chemin |
|---------|--------|
| `GET` | `/entreprises?page=&size=&q=` |
| `GET` | `/autorites-contractantes?page=&size=&q=` |
| `POST` | `/impersonate/entreprise` |
| `POST` | `/impersonate/autorite-contractante` |
| `POST` | `/release` |

Corps JSON des impersonations :

```json
{ "entrepriseId": 1 }
```

```json
{ "autoriteContractanteId": 1 }
```

Les réponses d’impersonation et de **release** reprennent la forme de `LoginResponse` (`token`, `role`, `permissions`, etc.) avec en plus :

- `impersonating` : `true` pendant une impersonation, `false` après release ou à la connexion standard ;
- `actingEntrepriseId` / `actingAutoriteContractanteId` : identités « vues » par l’API.

**Contrôle métier** : seuls les comptes dont le **rôle en base de données** est `COMMISSION_RELAIS` peuvent appeler ces endpoints (même si le JWT courant porte un rôle effectif `ENTREPRISE` ou `AUTORITE_CONTRACTANTE`).

## JWT

### Connexion normale (`POST /api/auth/login`)

- Claims habituels : `userId`, `role`, `permissions`, `impersonating` à `false`.

### Après impersonation entreprise

- `role` = `ENTREPRISE` (rôle **effectif** pour les contrôles métier et le filtre de sécurité).
- `userId` = identifiant du **compte commission relais** (inchangé).
- `permissions` = jeu de permissions du rôle `ENTREPRISE`.
- `impersonating` = `true`.
- `actingEntrepriseId` = identifiant de l’entreprise choisie.

### Après impersonation autorité contractante

- `role` = `AUTORITE_CONTRACTANTE`.
- `permissions` = jeu du rôle `AUTORITE_CONTRACTANTE`.
- `actingAutoriteContractanteId` renseigné.

### Durée de vie

- JWT « standard » (hors impersonation) : `app.jwt.expiration-ms` (défaut 24 h).
- JWT d’impersonation : `app.jwt.relais-expiration-ms` (défaut **4 h**).

## Comportement côté services

Un service `EffectiveIdentityService` résout l’entreprise ou l’autorité contractante effective à partir du principal `AuthenticatedUser` et de l’enregistrement `Utilisateur` : en impersonation, ce sont les claims `acting*` qui priment sur les liaisons éventuelles en base pour le compte relais.

Certaines actions sensibles restent interdites en impersonation (ex. **gestion des délégués** : refus explicite si `user.isImpersonating()`).

## Intégration front

1. Se connecter avec `commission_relais` / `123456`.
2. Appeler les listes pour choisir une entreprise ou une AC.
3. Appeler `POST .../impersonate/...` et **remplacer** le token stocké par celui retourné.
4. Utiliser les API métier habituelles avec ce token (rôle et périmètre effectifs).
5. Appeler `POST .../release` pour revenir au profil commission relais, ou se reconnecter via `/api/auth/login`.
