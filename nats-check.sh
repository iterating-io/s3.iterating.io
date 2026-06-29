#!/usr/bin/env bash

set -euo pipefail

# Load .env into environment
set -a
source .env
set +a

echo "== NATS environment variables =="

print_var() {
  name="$1"
  value="${!name-}"
  if [ "$name" = "NATS_CREDENTIALS_PATH" ]; then
    if [ -z "$value" ]; then
      echo "$name: (empty)"
    else
      # If the value is a path to a file, show a preview of the file contents; otherwise treat value as inline creds
      if [ -f "$value" ]; then
        preview=$(sed -e ':a;N;$!ba;s/\n/\\n/g' "$value" | cut -c1-200)
        echo "$name (preview from file, newlines escaped): $preview"
        echo "$name (file size bytes): $(wc -c < "$value")"
        echo "$name (first line): $(sed -n '1p' "$value")"
      else
        preview=$(printf '%s' "$value" | sed -e ':a;N;$!ba;s/\n/\\n/g' | cut -c1-200)
        echo "$name (preview, newlines escaped): $preview"
        echo "$name (length bytes): $(printf '%s' "$value" | wc -c)"
        echo "$name (first line): $(printf '%s' "$value" | sed -n '1p')"
      fi
    fi
  else
    echo "$name: $value"
  fi
}

print_var NATS_SERVERS
print_var NATS_CONNECTION_NAME
print_var NATS_STREAM
print_var NATS_DURABLE
print_var NATS_FILTER_SUBJECT
print_var NATS_BATCH_SIZE
print_var NATS_FETCH_TIMEOUT
print_var NATS_IDLE_DELAY
print_var NATS_CREDENTIALS_PATH

echo
echo "== TCP connectivity check =="
IFS=',' read -ra SARR <<< "${NATS_SERVERS-}"
for s in "${SARR[@]}"; do
  s_trim=$(printf '%s' "$s" | xargs)
  hostport=${s_trim#*://}
  host=$(printf '%s' "$hostport" | cut -d':' -f1)
  port=$(printf '%s' "$hostport" | cut -s -d':' -f2)
  if [ -z "$port" ]; then port=4222; fi
  echo -n "-> $host:$port ... "
  if command -v nc >/dev/null 2>&1; then
    if nc -z -w3 "$host" "$port" >/dev/null 2>&1; then
      echo "open"
    else
      echo "closed/unreachable"
    fi
  elif command -v timeout >/dev/null 2>&1; then
    if timeout 3 bash -c ">/dev/tcp/$host/$port" 2>/dev/null; then
      echo "open"
    else
      echo "closed/unreachable"
    fi
  else
    echo "skipped (no nc or timeout)"
  fi
done

echo
echo "Done."
