# Déploiement en production (srvmf)

Les workflows GitHub « Deploy Backend » et « Deploy Frontend » sont **désactivés** depuis le
31/10/2026 : fusionner dans `main` ne déploie plus rien. Le déploiement se fait à la main sur
`srvmf`, avec les trois scripts de ce dossier.

## Procédure

1. **Fusionner** sur GitHub, par une PR **base `main` ← compare `newDev`** (back) et **base `main`
   ← compare `claude/front`** (front). Vérifier que la PR ne contient que les commits attendus.
2. **Se connecter à `srvmf`**. Les scripts utilisent des chemins absolus (`/home/mf/…`) : ils
   fonctionnent sous `mf` comme sous `root`.
3. **Sauvegarder la base** :
   ```bash
   sudo bash /home/mf/commission-fiscale-backend/scripts/deploiement/sauvegarder-base.sh
   ```
4. **Déployer le back** (branche `main` par défaut, ou une autre en argument) :
   ```bash
   sudo bash /home/mf/commission-fiscale-backend/scripts/deploiement/deployer-back.sh
   ```
5. **Déployer le front** :
   ```bash
   sudo bash /home/mf/commission-fiscale-backend/scripts/deploiement/deployer-front.sh
   ```
6. **Vérifier** https://commission-fiscale.gov.mr : connexion, main courante, une fiche.

Chaque script s'arrête avec « ARRÊT » ou « ÉCHEC » et un message clair si quelque chose ne va pas,
et affiche « OK » sinon.

## Ce que font les scripts

- Ils **refusent de continuer** si le dépôt du serveur contient une modification locale autre que
  les fichiers propres au serveur : le `git reset --hard` l'effacerait.
- Ils **sauvegardent puis remettent en place** les fichiers propres au serveur, dans
  `/home/mf/deploy-backups/` :
  - back : `src/main/resources/application-prod.properties`, `Dockerfile` ;
  - front : `src/lib/apiConfig.ts`, `nginx.conf`, `Dockerfile`.
- Ils **reconstruisent** le conteneur (`docker compose up -d --build backend|frontend` dans
  `/home/mf/commission-fiscale-deploy`).
- Le script du back **attend le démarrage**, puis affiche les lignes utiles du journal : démarrage,
  références rattrapées, alertes de sécurité. En cas d'échec, il affiche l'erreur.

## Les secrets

Ils sont dans `/home/mf/commission-fiscale-deploy/.env` (lisible par root seulement), jamais dans
le dépôt :

- `APP_JWT_SECRET` ;
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` ;
- `MINIO_URL`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`, `APP_STORAGE_BACKEND` ;
- `APP_ALLOW_LEGACY_DB_PASSWORD=true` : **dérogation provisoire**. Le mot de passe MySQL actuel
  figure dans l'historique du dépôt ; sans cette variable, le back refuse de démarrer.

Pour retirer la dérogation, il faut changer le mot de passe de l'utilisateur MySQL de
l'application, le reporter dans `SPRING_DATASOURCE_PASSWORD`, puis supprimer la ligne et
redéployer le back.

## Pièges déjà rencontrés

| Date | Ce qui s'est passé | Ce qui l'évite désormais |
|---|---|---|
| 23/11/2026 | Commandes en `~/…` lancées sous `root` : `~` valait `/root`, dossier introuvable. | Chemins absolus dans les scripts. |
| 23/11/2026 | `git reset --hard` a effacé une modification locale non commitée (la dérogation du mot de passe) : le back refusait de démarrer. | La dérogation est dans le code (`ExigencesProduction`) ; les scripts s'arrêtent sur toute modification locale inattendue. |
| 23/11/2026 | Fusion dans `main` sans déploiement : les workflows étaient désactivés. | Cette procédure. |

## Revenir en arrière

- **Code :** relancer le script sur une autre branche GitHub (`deployer-back.sh <branche>`), par
  exemple une branche créée sur le commit précédent.
- **Base :** recharger la sauvegarde de l'étape 3, après accord, car elle remplace les données :
  ```bash
  sudo docker exec -i mysql-server sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < /home/mf/deploy-backups/base-AAAAMMJJ-HHMM.sql
  ```
