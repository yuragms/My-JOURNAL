"""Экспорт всех ONNX-моделей в app/src/main/assets/models/.

Запуск из venv проекта hikvision-test1 (где уже установлены ultralytics,
insightface, torchreid). См. tools/README.md.

После первого экспорта зафиксируйте имена входов/выходов ONNX (netron) и
сверьте их с Kotlin-обёртками (OnnxModel/YoloeDetector/*Recognizer).
"""
from __future__ import annotations

import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/models"


def ensure_out() -> None:
    OUT.mkdir(parents=True, exist_ok=True)


def export_yoloe() -> None:
    """prompt-free YOLOE-26 small. Имя .pt актуализировать по релизу пакета."""
    from ultralytics import YOLO

    candidates = ["yoloe-26s.pt", "yoloe-26s-pf.pt", "yoloe-11s-seg-pf.pt"]
    weights = next((c for c in candidates if Path(c).exists()), candidates[0])
    model = YOLO(weights)
    path = model.export(format="onnx", opset=17, dynamic=False, simplify=True)
    shutil.copy(path, OUT / "yoloe26s.onnx")
    print("yoloe26s.onnx <-", weights)


def copy_insightface() -> None:
    """Копирует SCRFD-детектор и ArcFace-распознаватель из buffalo_sc."""
    base = Path.home() / ".insightface/models/buffalo_sc"
    det = next(base.glob("det_*.onnx"), None)
    rec = next(base.glob("w600k_*.onnx"), None)
    if det is None or rec is None:
        print("WARN: модели InsightFace не найдены в", base,
              "- запустите hikvision-test1 один раз для их скачивания")
        return
    shutil.copy(det, OUT / "face_det.onnx")
    shutil.copy(rec, OUT / "face_rec.onnx")
    print("face_det.onnx <-", det.name, "| face_rec.onnx <-", rec.name)


def export_osnet() -> None:
    """OSNet x0_25 ReID -> ONNX (вход 1x3x256x128)."""
    import torch
    import torchreid

    model = torchreid.models.build_model(
        "osnet_x0_25", num_classes=1000, pretrained=True
    )
    model.eval()
    dummy = torch.randn(1, 3, 256, 128)
    torch.onnx.export(
        model,
        dummy,
        str(OUT / "body_reid.onnx"),
        input_names=["input"],
        output_names=["embedding"],
        opset_version=17,
    )
    print("body_reid.onnx exported")


def export_object_encoder() -> None:
    """MobileCLIP-S0 image encoder -> ONNX.

    Экспортируется отдельным скриптом open_clip/mobileclip в
    mobileclip_s0_image.onnx; здесь только подхватываем готовый файл.
    """
    src = Path("mobileclip_s0_image.onnx")
    if src.exists():
        shutil.copy(src, OUT / "object_encoder.onnx")
        print("object_encoder.onnx <-", src.name)
    else:
        print("WARN: mobileclip_s0_image.onnx не найден - экспортируйте отдельно "
              "(см. README), затем перезапустите этот скрипт")


def main() -> None:
    ensure_out()
    export_yoloe()
    copy_insightface()
    export_osnet()
    export_object_encoder()
    print("Done ->", OUT)


if __name__ == "__main__":
    main()
