"""Скачивание и экспорт специализированного детектора дронов → drone_det.onnx.

Модель по умолчанию: sapoepsilon/yolov11s-drone-detector (~36 MB ONNX, лучше на реальных дронах).
Альтернатива (~9 MB): danivelikova/drone-detection-fred-yolo26n (узкий датасет FRED).

Запуск из venv hikvision-test1:
  pip install -U ultralytics huggingface_hub
  python tools/export_drone_model.py
"""
from __future__ import annotations

import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/models"

# YOLO11s (~36 MB ONNX), обучена на дронах в полёте.
DEFAULT_REPO = "sapoepsilon/yolov11s-drone-detector"
WEIGHT_FILE = "best.pt"


def export_drone(repo_id: str = DEFAULT_REPO) -> None:
    from huggingface_hub import hf_hub_download
    from ultralytics import YOLO

    OUT.mkdir(parents=True, exist_ok=True)
    weights = hf_hub_download(repo_id, WEIGHT_FILE)
    print("weights <-", weights)
    model = YOLO(weights)
    print("classes:", model.names)
    path = model.export(format="onnx", imgsz=640, simplify=True, opset=12, dynamic=False)
    dest = OUT / "drone_det.onnx"
    shutil.copy(path, dest)
    print("drone_det.onnx <-", repo_id, "|", dest.stat().st_size // 1024, "KB")


if __name__ == "__main__":
    export_drone()
