#!/usr/bin/env bash
# Déploie le back (commission-fiscale-backend) sur srvmf, depuis une branche GitHub (main par défaut).
#
#   sudo bash /home/mf/commission-fiscale-backend/scripts/deploiement/deployer-back.sh          # main
#   sudo bash /home/mf/commission-fiscale-backend/scripts/deploiement/deployer-back.sh newDev   # une autre branche
#
# Reprend les étapes de .github/workflows/deploy-backend.yml (désactivé sur GitHub), avec trois
# garde-fous ajoutés après le déploiement du 23/11/2026 :
#   - chemins absolus : sous root, « ~ » vaut /root et non /home/mf ;
#   - arrêt si le dépôt du serveur contient une modification locale autre que les deux fichiers
#     propres au serveur : le git reset --hard l'effacerait (c'est ainsi qu'une dérogation locale
#     a disparu et que le back a refusé de démarrer) ;
#   - attente du démarrage et lecture du journal : succès ou échec affiché clairement.
set -euo pipefail

# Tout le script est lu avant d'être exécuté : le git reset --hard réécrit ce fichier en cours de route.
main() {

local BRANCHE="${1:-main}"
DEPOT=/home/mf/commission-fiscale-backend
DEPLOY=/home/mf/commission-fiscale-deploy
SAUVEGARDES=/home/mf/deploy-backups
CONTENEUR=commission-backend
# Fichiers propres au serveur : sauvegardés avant la mise à jour, remis en place après.
LOCAUX=("src/main/resources/application-prod.properties" "Dockerfile")

cd "$DEPOT"
mkdir -p "$SAUVEGARDES"

echo "== Modifications locales du dépôt serveur"
INATTENDUES=$(git status --porcelain | awk '{print $2}' | grep -vxF -e "${LOCAUX[0]}" -e "${LOCAUX[1]}" || true)
if [ -n "$INATTENDUES" ]; then
    echo "ARRÊT : ces fichiers sont modifiés sur le serveur et seraient effacés par le déploiement :" >&2
    echo "$INATTENDUES" >&2
    echo "Sauvegardez-les (ou commitez-les dans le dépôt) avant de relancer." >&2
    exit 1
fi

echo "== Sauvegarde des fichiers propres au serveur"
cp "${LOCAUX[0]}" "$SAUVEGARDES/application-prod.properties.server"
cp "${LOCAUX[1]}" "$SAUVEGARDES/Dockerfile.backend.server"

echo "== Mise à jour du code depuis origin/$BRANCHE"
git fetch origin "$BRANCHE"
git reset --hard "origin/$BRANCHE"
cp "$SAUVEGARDES/application-prod.properties.server" "${LOCAUX[0]}"
cp "$SAUVEGARDES/Dockerfile.backend.server" "${LOCAUX[1]}"
git log --oneline -3

echo "== Construction et redémarrage du back"
cd "$DEPLOY"
docker compose up -d --build backend

echo "== Attente du démarrage (3 minutes au plus)"
DEBUT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
for _ in $(seq 1 36); do
    sleep 5
    if docker logs --since "$DEBUT" "$CONTENEUR" 2>&1 | grep -q "Started SgciApplication"; then
        docker logs --since "$DEBUT" "$CONTENEUR" 2>&1 | grep -E "SÉCURITÉ|référence\(s\) générée|Started SgciApplication" || true
        echo "OK : back démarré sur la branche $BRANCHE."
        exit 0
    fi
    if docker logs --since "$DEBUT" "$CONTENEUR" 2>&1 | grep -q "Application run failed"; then
        break
    fi
done
echo "ÉCHEC : le back n'a pas démarré. Dernières lignes utiles du journal :" >&2
docker logs --since "$DEBUT" "$CONTENEUR" 2>&1 | grep -E "ERROR|refusé|Caused by|Exception" | head -20 >&2
exit 1
}

main "$@"
exit $?
