#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 8 installation}"
: "${CATALINA_HOME:?Set CATALINA_HOME to an extracted Tomcat 9 distribution}"
PORT=${PORT:-8080}
DB_URL=${DB_URL:-jdbc:postgresql://127.0.0.1:5432/wholesale}
DB_USER=${DB_USER:-wholesale}
DB_PASSWORD=${DB_PASSWORD-wholesale-local}
case "$PORT" in
    ''|*[!0-9]*) echo 'PORT must be numeric.' >&2; exit 2 ;;
esac
if [ "$PORT" -lt 1024 ] || [ "$PORT" -gt 65535 ]; then
    echo 'PORT must be between 1024 and 65535.' >&2
    exit 2
fi
WAR="$ROOT/wholesale-web/target/wholesale.war"
if [ ! -f "$WAR" ]; then
    echo 'Run mvn verify first; the WAR does not exist.' >&2
    exit 1
fi
CATALINA_BASE="$ROOT/.runtime/tomcat"
export CATALINA_BASE JAVA_HOME CATALINA_HOME
mkdir -p "$CATALINA_BASE/conf" "$CATALINA_BASE/logs" "$CATALINA_BASE/temp" "$CATALINA_BASE/webapps" "$CATALINA_BASE/work"
cp "$CATALINA_HOME"/conf/* "$CATALINA_BASE/conf/"
cp "$ROOT/runtime/tomcat/server.xml" "$CATALINA_BASE/conf/server.xml"
cp "$ROOT/runtime/tomcat/context.xml" "$CATALINA_BASE/conf/context.xml"
cp "$WAR" "$CATALINA_BASE/webapps/wholesale.war"
CATALINA_OPTS="-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Tokyo -Dhttp.port=$PORT -Dhttp.address=127.0.0.1"
CATALINA_OPTS="$CATALINA_OPTS \"-Ddb.url=$DB_URL\" \"-Ddb.username=$DB_USER\" \"-Ddb.password=$DB_PASSWORD\""
export CATALINA_OPTS
printf 'Opening http://127.0.0.1:%s/wholesale/ (foreground; Ctrl+C stops this server)\n' "$PORT"
exec "$CATALINA_HOME/bin/catalina.sh" run
