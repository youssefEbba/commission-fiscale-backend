#!/usr/bin/env bash
# Déploie le front (commission-fiscale-bd18374b) sur srvmf, depuis une branche GitHub (main par défaut).
#
#   sudo bash /home/mf/commission-fiscale-backend/scripts/deploiement/deployer-front.sh
#   sudo bash /home/mf/commission-fiscale-backend/scripts/deploiement/deployer-front.sh claude/front
#
# Reprend les étapes de .github/workflows/deploy-frontend.yml du dépôt front (désactivé sur GitHub),
# avec les mêmes garde-fous que deployer-back.sh.
set -euo pipefail

# Tout le script est lu avant d'être exécuté : le git reset --hard réécrit ce fichier en cours de route.
main() {

local BRANCHE="${1:-main}"
DEPOT=/home/mf/commission-fiscale-bd18374b
DEPLOY=/home/mf/commission-fiscale-deploy
SAUVEGARDES=/home/mf/deploy-backups
# Fichiers propres au serveur : sauvegardés avant la mise à jour, remis en place après.
LOCAUX=("src/lib/apiConfig.ts" "nginx.conf" "Dockerfile")
NOMS=("apiConfig.ts.frontend.server" "nginx.conf.frontend.server" "Dockerfile.frontend.server")

cd "$DEPOT"
mkdir -p "$SAUVEGARDES"

echo "== Modifications locales du dépôt serveur"
INATTENDUES=$(git status --porcelain | awk '{print $2}' \
    | grep -vxF -e "${LOCAUX[0]}" -e "${LOCAUX[1]}" -e "${LOCAUX[2]}" || true)
if [ -n "$INATTENDUES" ]; then
    echo "ARRÊT : ces fichiers sont modifiés sur le serveur et seraient effacés par le déploiement :" >&2
    echo "$INATTENDUES" >&2
    exit 1
fi

echo "== Sauvegarde des fichiers propres au serveur"
for i in 0 1 2; do cp "${LOCAUX[$i]}" "$SAUVEGARDES/${NOMS[$i]}"; done

echo "== Mise à jour du code depuis origin/$BRANCHE"
git fetch origin "$BRANCHE"
git reset --hard "origin/$BRANCHE"
for i in 0 1 2; do cp "$SAUVEGARDES/${NOMS[$i]}" "${LOCAUX[$i]}"; done
git log --oneline -3

echo "== Construction et redémarrage du front"
cd "$DEPLOY"
docker compose up -d --build frontend

sleep 5
CODE=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8082/ || true)
if [ "$CODE" = "200" ]; then
    echo "OK : front en ligne (HTTP 200) sur la branche $BRANCHE."
else
    echo "ÉCHEC : le front répond $CODE sur http://127.0.0.1:8082/" >&2
    docker logs --tail 20 commission-frontend >&2 || true
    exit 1
fi
}

main "$@"
exit $?
