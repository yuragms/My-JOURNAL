#!/usr/bin/env bash
# Скачать .rpk модели для IMX500 (один раз)
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$DIR/models"

download() {
  local name="$1" url="$2" sha_url="$3"
  local out="$DIR/models/$name"
  if [[ -f "$out" ]]; then
    echo "✓ уже есть: $out ($(du -h "$out" | cut -f1))"
    return 0
  fi
  echo "→ Скачивание $name ..."
  curl -L -o "$out" "$url"
  if [[ -n "$sha_url" ]]; then
    curl -sL -o "$out.sha256" "$sha_url"
    (cd "$DIR/models" && sha256sum -c "$name.sha256")
  fi
  echo "✓ $out ($(du -h "$out" | cut -f1))"
}

download "imx500_network_yolo11n_pp.rpk" \
  "https://github.com/raspberrypi/imx500-models/raw/main/imx500_network_yolo11n_pp.rpk" ""

download "imx500_network_yolo26n_pp.rpk" \
  "https://github.com/tweenietomatoes/YOLO26n-for-Sony-IMX500/raw/main/imx500_network_yolo26n_pp.rpk" \
  "https://raw.githubusercontent.com/tweenietomatoes/YOLO26n-for-Sony-IMX500/main/imx500_network_yolo26n_pp.rpk.sha256"
