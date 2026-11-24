#!/bin/sh
set -eu
cd "$(dirname "$0")"
HTTP_PORT=${1:-24000}
FCGI_PORT=${2:-24001}
for port in "$HTTP_PORT" "$FCGI_PORT"; do
 case "$port" in ''|*[!0-9]*) echo 'Порт должен быть числом' >&2; exit 1;; esac
 [ "$port" -gt 1024 ] && [ "$port" -le 65535 ] || exit 1
done
[ "$HTTP_PORT" != "$FCGI_PORT" ] || exit 1
ROOT_DIR=$(pwd)
mkdir -p httpd-root/mutex-dir httpd-root/fcgi-bin
sed -e "s|/home/studs/sXXXXXX/lab2|$ROOT_DIR|g" \
    -e "s|24000|$HTTP_PORT|g" -e "s|24001|$FCGI_PORT|g" \
    -e "s|^User www|User $(id -un)|" -e "s|^Group www|Group $(id -gn)|" \
    httpd-root/conf/httpd.conf.template > httpd-root/conf/httpd.conf
echo "Конфигурация: $ROOT_DIR/httpd-root/conf/httpd.conf"
