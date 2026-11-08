#!/usr/bin/env bash
# Prueba una migración Flyway de la API de identidad sobre una COPIA desechable, nunca sobre la base real.
#
# Hace el ciclo completo de subida, reversión y nueva subida:
#   1. Crea un clúster PostgreSQL temporal (initdb). Solo escucha por un socket local; no abre TCP.
#   2. Carga el esquema previo de una de estas dos formas:
#        - Sin --dump: aplica V1..V(N-1).
#        - Con --dump <archivo>: restaura una copia (pg_dump custom o SQL plano) de la BD real y aplica
#          las migraciones que le falten.
#   3. Ejecuta db/pruebas/VN__semilla.sql (datos sintéticos previos), si existe.
#   4. Aplica VN y ejecuta db/pruebas/VN__verificar.sql.
#   5. Aplica db/rollback/VN__*.sql dos veces (comprueba que es idempotente) y verifica que el esquema,
#      los triggers, las funciones y el número de filas de cada tabla vuelven al estado previo a VN.
#   6. Vuelve a aplicar VN y repite las pruebas.
#
# Uso: scripts/probar-migracion.sh <versión> [--dump ruta/al/respaldo]
#      ejemplo: scripts/probar-migracion.sh 25
# Requiere en el PATH: initdb, pg_ctl, psql, createdb (y pg_restore si se usa --dump en formato custom).
set -euo pipefail

uso() { echo "Uso: $0 <versión> [--dump ruta/al/respaldo]" >&2; exit 2; }
[[ $# -ge 1 && "$1" =~ ^[0-9]+$ ]] || uso
VERSION="$1"; shift
DUMP=""
if [[ "${1:-}" == "--dump" ]]; then
    DUMP="${2:?Falta la ruta del respaldo}"
    [[ -r "$DUMP" ]] || { echo "No se puede leer el respaldo: $DUMP" >&2; exit 2; }
elif [[ $# -gt 0 ]]; then
    uso
fi

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IDENTIDAD="$RAIZ/tracking-identidad-api"
MIGRACIONES="$IDENTIDAD/core/src/main/resources/db/migration"
unico() { local m=("$@"); [[ ${#m[@]} -eq 1 && -f "${m[0]}" ]] || { echo "Se esperaba un único archivo: $*" >&2; exit 2; }; echo "${m[0]}"; }
MIGRACION=$(unico "$MIGRACIONES"/V"${VERSION}"__*.sql)
ROLLBACK=$(unico "$IDENTIDAD"/db/rollback/V"${VERSION}"__*.sql)
PRUEBAS=$(unico "$IDENTIDAD"/db/pruebas/V"${VERSION}"__verificar.sql)
SEMILLA="$IDENTIDAD/db/pruebas/V${VERSION}__semilla.sql"

for bin in initdb pg_ctl psql createdb; do
    command -v "$bin" >/dev/null || { echo "Falta $bin en el PATH" >&2; exit 2; }
done

TRABAJO="$(mktemp -d "${TMPDIR:-/tmp}/migracion-copia.XXXXXX")"
chmod 700 "$TRABAJO"
DATOS="$TRABAJO/datos"
SOCKET="$TRABAJO/socket"
# La ruta de un socket Unix tiene un límite de unos 107 bytes. Si TMPDIR es largo, usar /tmp solo para el socket.
if (( ${#SOCKET} > 80 )); then SOCKET="$(mktemp -d /tmp/pgsock.XXXXXX)"; fi
mkdir -p "$SOCKET"
chmod 700 "$SOCKET"

limpiar() {
    pg_ctl -D "$DATOS" -m immediate stop >/dev/null 2>&1 || true
    rm -rf "$TRABAJO"
    [[ "$SOCKET" == /tmp/pgsock.* ]] && rm -rf "$SOCKET"
    return 0
}
trap limpiar EXIT

echo "==> Creando clúster temporal"
initdb -D "$DATOS" -U postgres --auth=trust --no-sync -E UTF8 --locale=C.UTF-8 >/dev/null
pg_ctl -D "$DATOS" -w -l "$TRABAJO/postgres.log" \
    -o "-c listen_addresses='' -c unix_socket_directories='$SOCKET' -c fsync=off" start >/dev/null
createdb -h "$SOCKET" -U postgres tracking_copia

export PSQLRC=/dev/null
PSQL=(psql -X -q -v ON_ERROR_STOP=1 -h "$SOCKET" -U postgres -d tracking_copia)
aplicar() { "${PSQL[@]}" --single-transaction -f "$1" >/dev/null; }
version_de() { basename "$1" | sed -E 's/^V([0-9]+)__.*/\1/'; }

ULTIMA=0
if [[ -n "$DUMP" ]]; then
    echo "==> Restaurando la copia $DUMP"
    if head -c 5 "$DUMP" | grep -q PGDMP; then
        pg_restore -h "$SOCKET" -U postgres -d tracking_copia --no-owner --no-acl --exit-on-error "$DUMP"
    else
        "${PSQL[@]}" -f "$DUMP" >/dev/null
    fi
    ULTIMA=$("${PSQL[@]}" -At -c "SELECT COALESCE(max(version::int), 0) FROM flyway_schema_history WHERE success" 2>/dev/null || echo 0)
    if (( ULTIMA >= VERSION )); then
        echo "La copia ya tiene V$VERSION aplicada. Restaura un respaldo anterior." >&2; exit 1
    fi
fi

echo "==> Aplicando las migraciones previas pendientes (posteriores a V$ULTIMA, hasta V$((VERSION - 1)))"
while IFS= read -r archivo; do
    v=$(version_de "$archivo")
    if (( v > ULTIMA && v < VERSION )); then aplicar "$archivo"; fi
done < <(find "$MIGRACIONES" -name 'V*__*.sql' | sort -t V -k2 -n)

if [[ -f "$SEMILLA" ]]; then
    echo "==> Sembrando datos sintéticos previos a V$VERSION"
    aplicar "$SEMILLA"
fi

huella_esquema() {
    "${PSQL[@]}" -At -c "
        SELECT md5(coalesce(string_agg(x, ',' ORDER BY x), '')) FROM (
            SELECT 'c:' || table_name || '.' || column_name || ':' || data_type || ':' || is_nullable
                   || ':' || coalesce(column_default, '') AS x
              FROM information_schema.columns WHERE table_schema = 'public'
            UNION ALL SELECT 'k:' || conrelid::regclass || '.' || conname || ':' || pg_get_constraintdef(oid)
              FROM pg_constraint WHERE connamespace = 'public'::regnamespace
            UNION ALL SELECT 't:' || tgrelid::regclass || '.' || tgname FROM pg_trigger WHERE NOT tgisinternal
            UNION ALL SELECT 'f:' || proname FROM pg_proc WHERE pronamespace = 'public'::regnamespace
            UNION ALL SELECT 'i:' || indexname || ':' || indexdef FROM pg_indexes WHERE schemaname = 'public'
        ) s"
}
huella_filas() {
    "${PSQL[@]}" -At -c "
        SELECT string_agg(format('%s=%s', c.relname,
               (xpath('/row/n/text()', query_to_xml(format('SELECT count(*) AS n FROM %I', c.relname), false, true, '')))[1]::text),
               ',' ORDER BY c.relname)
          FROM pg_class c WHERE c.relnamespace = 'public'::regnamespace AND c.relkind = 'r'"
}
ESQUEMA_PREVIO=$(huella_esquema)
FILAS_PREVIAS=$(huella_filas)

echo "==> [1/2] Aplicando $(basename "$MIGRACION") y ejecutando las pruebas"
aplicar "$MIGRACION"
"${PSQL[@]}" -f "$PRUEBAS"

echo "==> Aplicando el rollback (dos veces, para comprobar la idempotencia)"
aplicar "$ROLLBACK"
aplicar "$ROLLBACK"
[[ "$(huella_esquema)" == "$ESQUEMA_PREVIO" ]] || { echo "FALLA: el rollback no restauró el esquema previo" >&2; exit 1; }
[[ "$(huella_filas)" == "$FILAS_PREVIAS" ]] || { echo "FALLA: el rollback alteró las filas de las tablas previas" >&2; exit 1; }
echo "    Rollback OK: esquema (columnas, constraints, triggers, funciones, índices) y filas iguales al estado previo"

echo "==> [2/2] Reaplicando V$VERSION tras el rollback y repitiendo las pruebas"
aplicar "$MIGRACION"
"${PSQL[@]}" -f "$PRUEBAS" >/dev/null
echo "==> V$VERSION OK: subida, pruebas, reversión y nueva subida verificadas sobre la copia"
