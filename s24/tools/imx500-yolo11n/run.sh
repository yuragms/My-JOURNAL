#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

THRESHOLD="${THRESHOLD:-0.35}"

exec python3 "$DIR/yolo11n_fps.py" \
  --model "$DIR/models/imx500_network_yolo11n_pp.rpk" \
  --labels "$DIR/assets/coco_labels.txt" \
  --bbox-normalization \
  --bbox-order xy \
  --threshold "$THRESHOLD" \
  "$@"
