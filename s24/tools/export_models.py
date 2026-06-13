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
    """prompt-free YOLOE-26 small (seg, встроенный словарь RAM++)."""
    from ultralytics import YOLO

    # Реальные имена ассетов ultralytics: yoloe-26s-seg-pf.pt (prompt-free).
    candidates = ["yoloe-26s-seg-pf.pt", "yoloe-11s-seg-pf.pt"]
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


def _consolidate(path: Path) -> None:
    """Сливает внешние данные весов в один .onnx файл (для Android assets)."""
    import onnx

    model = onnx.load(str(path), load_external_data=True)
    onnx.save_model(model, str(path), save_as_external_data=False)
    data = Path(str(path) + ".data")
    if data.exists():
        data.unlink()


def export_osnet() -> None:
    """OSNet x0_25 ReID -> ONNX одним файлом (вход 1x3x256x128)."""
    import torch
    import torchreid

    model = torchreid.models.build_model(
        "osnet_x0_25", num_classes=1000, pretrained=True
    )
    model.eval()
    dummy = torch.randn(1, 3, 256, 128)
    out = OUT / "body_reid.onnx"
    torch.onnx.export(
        model,
        dummy,
        str(out),
        input_names=["input"],
        output_names=["embedding"],
        opset_version=12,
        do_constant_folding=True,
        dynamo=False,
    )
    _consolidate(out)
    print("body_reid.onnx exported (single file)")


def export_object_encoder() -> None:
    """Лёгкий объектный энкодер: torchvision MobileNetV3-Small features -> вектор.

    Заменяет MobileCLIP надёжной встроенной моделью (KISS, без open_clip).
    Вход 1x3x256x256, выход — pooled-эмбеддинг (576 чисел).
    """
    import torch
    import torch.nn as nn
    from torchvision.models import MobileNet_V3_Small_Weights, mobilenet_v3_small

    base = mobilenet_v3_small(weights=MobileNet_V3_Small_Weights.IMAGENET1K_V1)

    class Encoder(nn.Module):
        def __init__(self, backbone: nn.Module) -> None:
            super().__init__()
            self.features = backbone.features
            self.pool = nn.AdaptiveAvgPool2d(1)

        def forward(self, x: torch.Tensor) -> torch.Tensor:
            x = self.features(x)
            x = self.pool(x)
            return torch.flatten(x, 1)

    model = Encoder(base).eval()
    dummy = torch.randn(1, 3, 256, 256)
    out = OUT / "object_encoder.onnx"
    torch.onnx.export(
        model,
        dummy,
        str(out),
        input_names=["input"],
        output_names=["embedding"],
        opset_version=12,
        do_constant_folding=True,
        dynamo=False,
    )
    _consolidate(out)
    print("object_encoder.onnx exported (mobilenet_v3_small features)")


def main() -> None:
    ensure_out()
    export_yoloe()
    copy_insightface()
    export_osnet()
    export_object_encoder()
    print("Done ->", OUT)


if __name__ == "__main__":
    main()
