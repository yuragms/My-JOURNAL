#!/usr/bin/env python3
"""YOLO26n object detection on Sony IMX500 (post-processed RPK) with FPS overlay."""

import argparse
import os
import sys
import time
from functools import lru_cache
from pathlib import Path

import cv2
import numpy as np

from picamera2 import MappedArray, Picamera2
from picamera2.devices import IMX500
from picamera2.devices.imx500 import NetworkIntrinsics

ROOT = Path(__file__).resolve().parent
DEFAULT_MODEL = ROOT / "models" / "imx500_network_yolo26n_pp.rpk"
DEFAULT_LABELS = ROOT / "assets" / "coco_labels.txt"

last_detections = []
fps_value = 0.0
fps_frames = 0
fps_t0 = time.perf_counter()


class Detection:
    def __init__(self, box, category, conf):
        self.category = category
        self.conf = conf
        self.box = box  # x, y, w, h in ISP coordinates


def parse_detections(metadata: dict):
    """Parse post-processed YOLO26n tensors (NMS already on IMX500)."""
    global last_detections
    outputs = imx500.get_outputs(metadata)
    if outputs is None:
        return last_detections

    boxes, scores, classes, num_dets = outputs
    n = int(num_dets[0]) if hasattr(num_dets, "__len__") else int(num_dets)
    n = min(n, len(scores))

    input_w, input_h = imx500.get_input_size()
    parsed = []
    for i in range(n):
        score = float(scores[i])
        if score < args.threshold:
            continue
        x1, y1, x2, y2 = boxes[i]
        coords = (y1 / input_h, x1 / input_w, y2 / input_h, x2 / input_w)
        x, y, w, h = imx500.convert_inference_coords(coords, metadata, picam2)
        parsed.append(Detection((x, y, w, h), int(classes[i]), score))

    last_detections = parsed
    return last_detections


@lru_cache
def get_labels():
    labels = intrinsics.labels
    if intrinsics.ignore_dash_labels:
        labels = [label for label in labels if label and label != "-"]
    return labels


def draw_overlay(request, stream="main"):
    detections = last_results if last_results is not None else []
    labels = get_labels()
    with MappedArray(request, stream) as m:
        hud = f"FPS: {fps_value:.1f}  |  obj: {len(detections)}  |  IMX500 YOLO26n"
        cv2.putText(m.array, hud, (12, 36), cv2.FONT_HERSHEY_SIMPLEX, 0.85, (0, 0, 0), 4)
        cv2.putText(m.array, hud, (12, 36), cv2.FONT_HERSHEY_SIMPLEX, 0.85, (0, 255, 255), 2)

        for detection in detections:
            x, y, w, h = detection.box
            label = f"{labels[int(detection.category)]} ({detection.conf:.2f})"
            (text_width, text_height), baseline = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
            text_x, text_y = x + 5, y + 15
            overlay = m.array.copy()
            cv2.rectangle(
                overlay,
                (text_x, text_y - text_height),
                (text_x + text_width, text_y + baseline),
                (255, 255, 255),
                cv2.FILLED,
            )
            cv2.addWeighted(overlay, 0.30, m.array, 0.70, 0, m.array)
            cv2.putText(m.array, label, (text_x, text_y), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 1)
            cv2.rectangle(m.array, (x, y), (x + w, y + h), (0, 255, 0, 0), thickness=2)


def tick_fps():
    global fps_value, fps_frames, fps_t0
    fps_frames += 1
    now = time.perf_counter()
    elapsed = now - fps_t0
    if elapsed >= 1.0:
        fps_value = fps_frames / elapsed
        det_n = len(last_results) if last_results else 0
        print(f"FPS: {fps_value:.1f}  obj: {det_n}", flush=True)
        fps_frames = 0
        fps_t0 = now


def get_args():
    parser = argparse.ArgumentParser(description="YOLO26n на IMX500 с FPS")
    parser.add_argument("--model", type=str, default=str(DEFAULT_MODEL))
    parser.add_argument("--fps", type=int, help="Целевой FPS камеры")
    parser.add_argument("--headless", action="store_true")
    parser.add_argument("--threshold", type=float, default=0.30)
    parser.add_argument("--labels", type=str, default=str(DEFAULT_LABELS))
    parser.add_argument("--print-intrinsics", action="store_true")
    return parser.parse_args()


if __name__ == "__main__":
    args = get_args()
    args.model = os.path.expanduser(args.model)
    if args.labels:
        args.labels = os.path.expanduser(args.labels)

    if not os.path.isfile(args.model):
        print(f"Модель не найдена: {args.model}", file=sys.stderr)
        sys.exit(1)

    imx500 = IMX500(args.model)
    intrinsics = imx500.network_intrinsics or NetworkIntrinsics()
    intrinsics.task = "object detection"

    if args.labels and os.path.isfile(args.labels):
        with open(args.labels, "r") as f:
            intrinsics.labels = f.read().splitlines()
    intrinsics.update_with_defaults()

    if args.print_intrinsics:
        print(intrinsics)
        sys.exit(0)

    picam2 = Picamera2(imx500.camera_num)
    target_fps = args.fps or intrinsics.inference_rate or 10
    config = picam2.create_preview_configuration(controls={"FrameRate": target_fps}, buffer_count=12)

    imx500.show_network_fw_progress_bar()
    if args.headless:
        picam2.start(config)
    else:
        picam2.start(config, show_preview=True)
        picam2.pre_callback = draw_overlay

    print(f"Модель: {args.model}", flush=True)
    print(f"Целевой FPS: {target_fps}  |  режим: {'headless' if args.headless else 'preview'}", flush=True)
    last_results = None
    while True:
        last_results = parse_detections(picam2.capture_metadata())
        tick_fps()
