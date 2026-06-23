"""Скачивание и экспорт YOLO11 drone_det n/s/m/l → assets/models.

Готовые веса (класс drone) на HuggingFace:
  n — marie-kjelberg/drone-detector
  s — sapoepsilon/yolov11s-drone-detector (эталон)
  m, l — дообучение на Seraphim mini (400 кадров)

Запуск:
  pip install -U ultralytics huggingface_hub onnxslim
  python tools/export_drone_models.py
  python tools/export_drone_models.py --train-ml   # только m и l
"""
from __future__ import annotations

import argparse
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/models"
DATA_ROOT = ROOT / "tools/datasets/seraphim_mini"
SUBSET_ROOT = DATA_ROOT / "subset"

HF_VARIANTS: dict[str, tuple[str, str]] = {
    "n": ("marie-kjelberg/drone-detector", "yolo11n_drone.pt"),
    "s": ("sapoepsilon/yolov11s-drone-detector", "best.pt"),
}


def export_onnx(weights: str, dest: Path) -> None:
    from ultralytics import YOLO

    model = YOLO(weights)
    print("classes:", model.names)
    path = model.export(format="onnx", imgsz=640, simplify=True, opset=12, dynamic=False)
    shutil.copy(path, dest)
    print(dest.name, "←", dest.stat().st_size // 1024, "KB")


def export_hf(size: str) -> None:
    from huggingface_hub import hf_hub_download

    repo, wf = HF_VARIANTS[size]
    weights = hf_hub_download(repo, wf)
    OUT.mkdir(parents=True, exist_ok=True)
    export_onnx(weights, OUT / f"drone_det_{size}.onnx")


def prepare_seraphim_mini() -> Path:
    """400 кадров из Seraphim batch_001 — быстрое дообучение m/l."""
    from huggingface_hub import hf_hub_download

    images_dir = SUBSET_ROOT / "images"
    if not images_dir.exists() or len(list(images_dir.glob("*"))) < 100:
        for kind in ("images", "labels"):
            z = hf_hub_download(
                "lgrzybowski/seraphim-drone-detection-dataset",
                f"train/{kind}/batch_001.zip",
                repo_type="dataset",
            )
            zipfile.ZipFile(z).extractall(DATA_ROOT / "train" / kind)
        SUBSET_ROOT.mkdir(parents=True, exist_ok=True)
        (SUBSET_ROOT / "images").mkdir(exist_ok=True)
        (SUBSET_ROOT / "labels").mkdir(exist_ok=True)
        for i, img in enumerate(sorted((DATA_ROOT / "train" / "images").iterdir())):
            if i >= 400:
                break
            shutil.copy(img, SUBSET_ROOT / "images" / img.name)
            lbl = DATA_ROOT / "train" / "labels" / f"{img.stem}.txt"
            if lbl.exists():
                shutil.copy(lbl, SUBSET_ROOT / "labels" / lbl.name)

    yaml = DATA_ROOT / "data_subset.yaml"
    yaml.write_text(
        "path: %s\ntrain: images\nval: images\nnc: 1\nnames: [drone]\n" % SUBSET_ROOT,
        encoding="utf-8",
    )
    return yaml


def train_and_export(size: str, epochs: int = 8, batch: int = 4) -> None:
    from ultralytics import YOLO

    dest = OUT / f"drone_det_{size}.onnx"
    if dest.exists():
        print(f"skip {size}: {dest.name} already exists")
        return

    yaml = prepare_seraphim_mini()
    model = YOLO(f"yolo11{size}.pt")
    model.train(
        data=str(yaml),
        epochs=epochs,
        imgsz=640,
        batch=batch,
        patience=5,
        project=str(DATA_ROOT / "runs"),
        name=f"drone11{size}",
        exist_ok=True,
        device="mps",
        workers=2,
        plots=False,
        verbose=True,
    )
    best = DATA_ROOT / "runs" / f"drone11{size}" / "weights" / "best.pt"
    if not best.exists():
        raise FileNotFoundError(best)
    export_onnx(str(best), OUT / f"drone_det_{size}.onnx")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--train-ml", action="store_true", help="только дообучить m и l")
    parser.add_argument("--sizes", default="m,l", help="размеры для --train-ml, через запятую")
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--batch", type=int, default=4)
    args = parser.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    if args.train_ml:
        for size in args.sizes.split(","):
            size = size.strip()
            if not size:
                continue
            print(f"=== train+export {size}", flush=True)
            train_and_export(size, epochs=args.epochs, batch=args.batch)
    else:
        for size in HF_VARIANTS:
            print(f"=== HF export {size}")
            export_hf(size)
        shutil.copy(OUT / "drone_det_s.onnx", OUT / "drone_det.onnx")
        print("drone_det.onnx <- drone_det_s.onnx")


if __name__ == "__main__":
    main()
