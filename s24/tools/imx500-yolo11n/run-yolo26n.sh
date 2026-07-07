#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

THRESHOLD="${THRESHOLD:-0.30}"

exec python3 "$DIR/yolo26n_fps.py" \
  --model "$DIR/models/imx500_network_yolo26n_pp.rpk" \
  --labels "$DIR/assets/coco_labels.txt" \
  --threshold "$THRESHOLD" \
  "$@"
