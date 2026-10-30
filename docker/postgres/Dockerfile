# Mirrors docs/devops/postgres/Dockerfile for the backend repository's GHCR release build.
# Keep PostgreSQL 16 data compatibility; replace only the privilege-drop helper.
FROM postgres:16-alpine@sha256:0cefaabd882e42bcb2a2f3dc56d3ce4770560ba62fd7f197f1a2e008fd5731ab

# Alpine's su-exec replaces the upstream gosu binary that embeds an obsolete Go stdlib.
# Fail closed if upstream changes its entrypoint instead of removing a helper it needs.
RUN apk add --no-cache su-exec \
 && test "$(grep -c 'exec gosu postgres ' /usr/local/bin/docker-entrypoint.sh)" = 1 \
 && sed -i 's/exec gosu postgres /exec su-exec postgres /' /usr/local/bin/docker-entrypoint.sh \
 && rm /usr/local/bin/gosu
