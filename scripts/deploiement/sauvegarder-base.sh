#!/usr/bin/env bash
# Sauvegarde complète de MySQL (toutes les bases) avant un déploiement, sur srvmf.
#
#   sudo bash /home/mf/commission-fiscale-backend/scripts/deploiement/sauvegarder-base.sh
#
# Le mot de passe root est lu DANS le conteneur (variable MYSQL_ROOT_PASSWORD) : il n'apparaît
# ni dans la commande, ni dans l'historique du shell.
set -euo pipefail

CONTENEUR_MYSQL="${CONTENEUR_MYSQL:-mysql-server}"
DOSSIER="${DOSSIER_SAUVEGARDES:-/home/mf/deploy-backups}"
FICHIER="$DOSSIER/base-$(date +%Y%m%d-%H%M).sql"

mkdir -p "$DOSSIER"
echo "Sauvegarde de la base dans $FICHIER ..."
docker exec "$CONTENEUR_MYSQL" sh -c \
    'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --all-databases --single-transaction --routines --events' \
    > "$FICHIER"

TAILLE=$(stat -c %s "$FICHIER")
if [ "$TAILLE" -lt 100000 ]; then
    echo "ERREUR : la sauvegarde ne fait que $TAILLE octets, elle est sans doute vide. Déploiement à suspendre." >&2
    exit 1
fi
echo "Sauvegarde faite : $(du -h "$FICHIER" | cut -f1)"
