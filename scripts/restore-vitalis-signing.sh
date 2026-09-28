#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: restore-vitalis-signing.sh DESTINATION_DIRECTORY" >&2
  exit 2
fi

test -n "${RUNNER_TEMP:-}"
test -n "${VITALIS_SIGNING_BACKUP_PASSPHRASE:-}"
test -n "${VITALIS_SIGNING_VAULT_TOKEN:-}"
test -n "${VITALIS_VAULT_REPOSITORY:-}"
test -n "${VITALIS_VAULT_RECOVERY_PATH:-}"
grep -Fq 'Status: **ACTIVE — BACKUP_CONFIRMED**' docs/VITALIS_SIGNING_IDENTITY.md

DESTINATION="$1"
WORK_DIRECTORY=$(mktemp -d "$RUNNER_TEMP/vitalis-signing-restore.XXXXXX")
trap 'rm -rf "$WORK_DIRECTORY"' EXIT
umask 077

curl --fail-with-body --silent --show-error \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $VITALIS_SIGNING_VAULT_TOKEN" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/$VITALIS_VAULT_REPOSITORY/contents/$VITALIS_VAULT_RECOVERY_PATH" \
  > "$WORK_DIRECTORY/vault-recovery.json"
jq -er '.content' "$WORK_DIRECTORY/vault-recovery.json" | base64 --decode \
  > "$WORK_DIRECTORY/Vitalis-Production-Signing-Recovery.tar.gz.age"

(cd scripts/vitalis-age && go run . decrypt \
  "$WORK_DIRECTORY/Vitalis-Production-Signing-Recovery.tar.gz.age" \
  "$WORK_DIRECTORY/recovered.tar.gz")
mkdir -p "$DESTINATION"
tar -xzf "$WORK_DIRECTORY/recovered.tar.gz" -C "$DESTINATION"
chmod 600 "$DESTINATION/vitalis-production.jks" "$DESTINATION/signing-secrets.json"

STORE_PASSWORD=$(jq -er '.keystore_password' "$DESTINATION/signing-secrets.json")
ALIAS=$(jq -er '.alias' "$DESTINATION/signing-secrets.json")
KEY_PASSWORD=$(jq -er '.key_password' "$DESTINATION/signing-secrets.json")
test "$ALIAS" = "vitalis-production"
keytool -list -keystore "$DESTINATION/vitalis-production.jks" \
  -storepass "$STORE_PASSWORD" -alias "$ALIAS" >/dev/null

jar --create --file "$WORK_DIRECTORY/signing-proof.jar" -C "$DESTINATION" README-RECOVERY.txt
jarsigner -keystore "$DESTINATION/vitalis-production.jks" \
  -storepass "$STORE_PASSWORD" -keypass "$KEY_PASSWORD" \
  "$WORK_DIRECTORY/signing-proof.jar" "$ALIAS" >/dev/null
jarsigner -verify "$WORK_DIRECTORY/signing-proof.jar" >/dev/null

keytool -exportcert -keystore "$DESTINATION/vitalis-production.jks" \
  -storepass "$STORE_PASSWORD" -alias "$ALIAS" \
  -file "$WORK_DIRECTORY/restored-cert.der" >/dev/null
ACTUAL_SHA256=$(openssl x509 -inform DER -in "$WORK_DIRECTORY/restored-cert.der" \
  -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':' | tr '[:upper:]' '[:lower:]')
EXPECTED_SHA256=$(awk -F'|' '$2 ~ /Certificate SHA-256/ {gsub(/[ `]/, "", $3); print $3}' \
  docs/VITALIS_SIGNING_IDENTITY.md | tr -d ':' | tr '[:upper:]' '[:lower:]')
test -n "$EXPECTED_SHA256"
test "$EXPECTED_SHA256" != "pending"
test "$ACTUAL_SHA256" = "$EXPECTED_SHA256"

echo "Verified active Vitalis signing recovery package and public certificate fingerprint."
