#!/usr/bin/env bash
set -euo pipefail

# Read-only Elasticsearch query wrapper built on curl.
ES_BASE_URL="${ES_BASE_URL:-http://es.jdx-ka-v2-dev.building2-dev.jdt.com.cn}"
ES_USER="${ES_USER:-admin}"
ES_PASS="${ES_PASS:-LOeJH2vJpcCAdg11}"

PATH_ARG="${1:-}"
METHOD="${2:-GET}"
BODY="${3:-}"

if [[ -z "$PATH_ARG" ]]; then
  echo "用法: $0 <path> [GET|POST] [json_body]" >&2
  exit 2
fi

if [[ "$PATH_ARG" != /* ]]; then
  echo "错误: path 必须以 / 开头，例如 /my-index/_search" >&2
  exit 2
fi

METHOD_UPPER="$(printf '%s' "$METHOD" | tr '[:lower:]' '[:upper:]')"

# Only allow GET and POST (POST is only for query endpoints).
if [[ "$METHOD_UPPER" != "GET" && "$METHOD_UPPER" != "POST" ]]; then
  echo "拒绝: 仅允许 GET 或 POST 查询请求" >&2
  exit 3
fi

PATH_LC="$(printf '%s' "$PATH_ARG" | tr '[:upper:]' '[:lower:]')"

# Hard deny list for mutation endpoints.
DENY_PATTERNS=(
  "/_bulk"
  "/_reindex"
  "/_update"
  "/_update_by_query"
  "/_delete"
  "/_delete_by_query"
  "/_scripts"
  "/_ingest"
  "/_template"
  "/_index_template"
  "/_ilm"
  "/_aliases"
  "/_rollover"
)

for p in "${DENY_PATTERNS[@]}"; do
  if [[ "$PATH_LC" == *"$p"* ]]; then
    echo "拒绝: 命中禁止端点 $p" >&2
    exit 3
  fi
done

# Allow-list for query/read endpoints.
ALLOW_REGEX='(^/_cluster/health([/?].*)?$)|(^/_cat/[^[:space:]]+([/?].*)?$)|(^/[^/?]+/_search([/?].*)?$)|(^/[^/?]+/_count([/?].*)?$)|(^/[^/?]+/_mapping([/?].*)?$)|(^/[^/?]+/_settings([/?].*)?$)|(^/[^/?]+/_doc/[^/?]+([/?].*)?$)'

if ! [[ "$PATH_LC" =~ $ALLOW_REGEX ]]; then
  echo "拒绝: 非允许的查询端点 -> $PATH_ARG" >&2
  exit 3
fi

if [[ "$METHOD_UPPER" == "POST" ]]; then
  if [[ "$PATH_LC" != *"/_search"* && "$PATH_LC" != *"/_count"* ]]; then
    echo "拒绝: POST 仅允许用于 _search 或 _count" >&2
    exit 3
  fi
fi

FULL_URL="${ES_BASE_URL%/}$PATH_ARG"

if [[ "$METHOD_UPPER" == "POST" ]]; then
  if [[ -z "$BODY" ]]; then
    BODY='{"query":{"match_all":{}},"size":10}'
  fi
  curl --fail --silent --show-error \
    -u "$ES_USER:$ES_PASS" \
    -H 'Content-Type: application/json' \
    -X POST "$FULL_URL" \
    -d "$BODY"
else
  curl --fail --silent --show-error \
    -u "$ES_USER:$ES_PASS" \
    -X GET "$FULL_URL"
fi

echo
