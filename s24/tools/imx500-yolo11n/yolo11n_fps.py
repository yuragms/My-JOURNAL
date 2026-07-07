#!/usr/bin/env python3
"""YOLO11n object detection on Sony IMX500 (Raspberry Pi AI Camera) with FPS overlay."""

import argparse
import os
import sys
import time
from functools import lru_cache
from pathlib import Path

import cv2

from picamera2 import MappedArray, Picamera2
from picamera2.devices import IMX500
from picamera2.devices.imx500 import NetworkIntrinsics, postprocess_nanodet_detection

ROOT = Path(__file__).resolve().parent
DEFAULT_MODEL = ROOT / "models" / "imx500_network_yolo11n_pp.rpk"
DEFAULT_LABELS = ROOT / "assets" / "coco_labels.txt"

last_detections = []
fps_value = 0.0
fps_frames = 0
fps_t0 = time.perf_counter()


class Detection:
    def __init__(self, coords, category, conf, metadata):
        self.category = category
        self.conf = conf
        self.box = imx500.convert_inference_coords(coords, metadata, picam2)


def parse_detections(metadata: dict):
    global last_detections
    np_outputs = imx500.get_outputs(metadata, add_batch=True)
    input_w, input_h = imx500.get_input_size()
    if np_outputs is None:
        return last_detections

    if intrinsics.postprocess == "nanodet":
        boxes, scores, classes = postprocess_nanodet_detection(
            outputs=np_outputs[0],
            conf=args.threshold,
            iou_thres=args.iou,
            max_out_dets=args.max_detections,
        )[0]
        from picamera2.devices.imx500.postprocess import scale_boxes

        boxes = scale_boxes(boxes, 1, 1, input_h, input_w, False, False)
    else:
        boxes, scores, classes = np_outputs[0][0], np_outputs[1][0], np_outputs[2][0]
        if intrinsics.bbox_normalization:
            boxes = boxes / input_h
        if intrinsics.bbox_order == "xy":
            boxes = boxes[:, [1, 0, 3, 2]]

    last_detections = [
        Detection(box, category, score, metadata)
        for box, score, category in zip(boxes, scores, classes)
        if score > args.threshold
    ]
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
        hud = f"FPS: {fps_value:.1f}  |  obj: {len(detections)}  |  IMX500 YOLO11n"
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
    parser = argparse.ArgumentParser(description="YOLO11n на IMX500 с FPS")
    parser.add_argument("--model", type=str, default=str(DEFAULT_MODEL), help="Путь к .rpk модели")
    parser.add_argument("--fps", type=int, help="Целевой FPS камеры")
    parser.add_argument("--headless", action="store_true", help="Без окна, только FPS в терминал")
    parser.add_argument("--bbox-normalization", action=argparse.BooleanOptionalAction)
    parser.add_argument("--bbox-order", choices=["yx", "xy"], default="xy")
    parser.add_argument("--threshold", type=float, default=0.35)
    parser.add_argument("--iou", type=float, default=0.65)
    parser.add_argument("--max-detections", type=int, default=20)
    parser.add_argument("--ignore-dash-labels", action=argparse.BooleanOptionalAction)
    parser.add_argument("--postprocess", choices=["", "nanodet"], default=None)
    parser.add_argument("-r", "--preserve-aspect-ratio", action=argparse.BooleanOptionalAction)
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
    intrinsics = imx500.network_intrinsics
    if not intrinsics:
        intrinsics = NetworkIntrinsics()
        intrinsics.task = "object detection"
    elif intrinsics.task != "object detection":
        print("Network is not an object detection task", file=sys.stderr)
        sys.exit(1)

    for key, value in vars(args).items():
        if key == "labels" and value is not None:
            with open(value, "r") as f:
                intrinsics.labels = f.read().splitlines()
        elif hasattr(intrinsics, key) and value is not None:
            setattr(intrinsics, key, value)

    if intrinsics.labels is None and args.labels and os.path.isfile(args.labels):
        with open(args.labels, "r") as f:
            intrinsics.labels = f.read().splitlines()
    intrinsics.update_with_defaults()

    if args.print_intrinsics:
        print(intrinsics)
        sys.exit(0)

    picam2 = Picamera2(imx500.camera_num)
    target_fps = args.fps or intrinsics.inference_rate
    config = picam2.create_preview_configuration(controls={"FrameRate": target_fps}, buffer_count=12)

    imx500.show_network_fw_progress_bar()
    if args.headless:
        picam2.start(config)
    else:
        picam2.start(config, show_preview=True)
        picam2.pre_callback = draw_overlay

    if intrinsics.preserve_aspect_ratio:
        imx500.set_auto_aspect_ratio()

    mode = "headless" if args.headless else "preview"
    print(f"Модель: {args.model}", flush=True)
    print(f"Целевой FPS: {target_fps}  |  режим: {mode}", flush=True)
    last_results = None
    while True:
        last_results = parse_detections(picam2.capture_metadata())
        tick_fps()
