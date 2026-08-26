#!/usr/bin/env bash
# Download the local analog of the on-device default (Gemma 3 1B IT, Q4).
# Requires HF_TOKEN because Gemma files are gated.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_DIR="${SMS_AI_MODEL_DIR:-$ROOT/.models/sms-ai}"
REPO="${SMS_AI_HF_REPO:-ggml-org/gemma-3-1b-it-GGUF}"
FILE="${SMS_AI_HF_FILE:-gemma-3-1b-it-Q4_K_M.gguf}"
SIZE="${SMS_AI_HF_SIZE:-806058240}"
DEST="$DEST_DIR/$FILE"

if [ -z "${HF_TOKEN:-}" ]; then
  echo "HF_TOKEN is unset. Create a Read token at https://huggingface.co/settings/tokens" >&2
  echo "and accept the Gemma license on https://huggingface.co/$REPO" >&2
  exit 1
fi

mkdir -p "$DEST_DIR"
if [ -f "$DEST" ] && [ "$(stat -c%s "$DEST")" -eq "$SIZE" ]; then
  echo "Already downloaded: $DEST ($SIZE bytes)"
  exit 0
fi

echo "Downloading $REPO/$FILE -> $DEST"
if command -v hf >/dev/null 2>&1; then
  hf download "$REPO" "$FILE" --local-dir "$DEST_DIR" --token "$HF_TOKEN"
else
  url="https://huggingface.co/$REPO/resolve/main/$FILE"
  tmp="$DEST.partial"
  curl -fL --retry 3 --retry-all-errors \
    -H "Authorization: Bearer $HF_TOKEN" \
    -o "$tmp" "$url"
  mv -f "$tmp" "$DEST"
fi

got="$(stat -c%s "$DEST")"
if [ "$got" -ne "$SIZE" ]; then
  echo "unexpected size $got (expected $SIZE)" >&2
  exit 1
fi
echo "Downloaded $DEST ($got bytes)"
