#!/usr/bin/env bash
#
# Gera um novo keystore de release para assinar o APK e imprime o base64
# pronto para colar no secret KEYSTORE_BASE64 do GitHub Actions.
#
# Uso:
#   ./scripts/generate-keystore.sh
#
# O script pede interativamente o alias e as senhas (nunca aceita senha por
# argumento, para não vazar em `history`/`ps`). O arquivo .jks gerado fica
# em app/keystore.jks (já ignorado pelo .gitignore) e NUNCA é commitado.

set -euo pipefail

KEYSTORE_PATH="app/keystore.jks"
VALIDITY_DAYS=10000

if [ -f "$KEYSTORE_PATH" ]; then
  read -r -p "Já existe um keystore em $KEYSTORE_PATH. Sobrescrever? [y/N] " confirm
  if [[ ! "$confirm" =~ ^[yY]$ ]]; then
    echo "Cancelado."
    exit 1
  fi
  rm -f "$KEYSTORE_PATH"
fi

read -r -p "Alias da chave [release]: " KEY_ALIAS
KEY_ALIAS="${KEY_ALIAS:-release}"

read -r -s -p "Senha do keystore: " KEYSTORE_PASSWORD
echo
read -r -s -p "Confirme a senha do keystore: " KEYSTORE_PASSWORD_CONFIRM
echo
if [ "$KEYSTORE_PASSWORD" != "$KEYSTORE_PASSWORD_CONFIRM" ]; then
  echo "As senhas não coincidem." >&2
  exit 1
fi

read -r -s -p "Senha da chave (Enter para usar a mesma do keystore): " KEY_PASSWORD
echo
KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"

read -r -p "Nome do titular (CN) [OpenMonetis]: " DNAME_CN
DNAME_CN="${DNAME_CN:-OpenMonetis}"

mkdir -p "$(dirname "$KEYSTORE_PATH")"

keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE_PATH" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "$VALIDITY_DAYS" \
  -storepass "$KEYSTORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -dname "CN=$DNAME_CN, OU=Dev, O=OpenMonetis, L=, ST=, C=BR"

echo
echo "Keystore gerado em: $KEYSTORE_PATH (não commitado — está no .gitignore)"
echo
echo "Configure os secrets do GitHub Actions (Settings > Secrets and variables > Actions):"
echo "  KEY_ALIAS         = $KEY_ALIAS"
echo "  KEYSTORE_PASSWORD = (a senha que você digitou)"
echo "  KEY_PASSWORD       = (a senha que você digitou)"
echo
echo "KEYSTORE_BASE64 (copie o bloco abaixo inteiro, sem adicionar quebras de linha):"
echo "----------------------------------------------------------------------"
base64 -i "$KEYSTORE_PATH"
echo "----------------------------------------------------------------------"

if command -v pbcopy >/dev/null 2>&1; then
  base64 -i "$KEYSTORE_PATH" | pbcopy
  echo
  echo "(já copiado para a área de transferência)"
fi
