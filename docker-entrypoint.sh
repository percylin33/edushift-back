#!/bin/sh
# Ensure log/upload dirs exist and are owned by the runtime user.
# Named volumes can remount over image dirs as root on first use.
set -e

LOG_DIR="${LOG_PATH:-/var/log/edushift}"
mkdir -p "${LOG_DIR}/archive" /app/logs/archive /app/uploads

if [ "$(id -u)" = "0" ]; then
  chown -R edushift:edushift "${LOG_DIR}" /app/logs /app/uploads
  # Drop privileges before starting the JVM (uid/gid 1001 = edushift).
  if command -v setpriv >/dev/null 2>&1; then
    exec setpriv --reuid=1001 --regid=1001 --clear-groups \
      java $JAVA_OPTS -jar /app/app.jar "$@"
  fi
  exec runuser -u edushift -- java $JAVA_OPTS -jar /app/app.jar "$@"
fi

exec java $JAVA_OPTS -jar /app/app.jar "$@"
