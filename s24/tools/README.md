# tools — экспорт моделей в ONNX

Готовит ONNX-модели для приложения и кладёт их в
`../app/src/main/assets/models/`. Запускать **один раз** на Mac (M1) в venv от
проекта `hikvision-test1`, где уже установлены `ultralytics`, `insightface`,
`torchreid`.

## Запуск

```bash
cd /Users/yuragms/Cursor-cash/hikvision-test1
source .venv/bin/activate
pip install -U ultralytics
python /Users/yuragms/Cursor-cash/s24/tools/export_models.py
python /Users/yuragms/Cursor-cash/s24/tools/export_drone_models.py
ls -la /Users/yuragms/Cursor-cash/s24/app/src/main/assets/models
```

Ожидаемые файлы в `assets/models/`:

| Файл | Модель |
| --- | --- |
| `yoloe26s.onnx` | YOLOE-26 small (prompt-free, локализация) |
| `drone_det_n.onnx` | YOLO11n, 1 класс `drone` (HuggingFace) |
| `drone_det_s.onnx` | YOLO11s, эталон (HuggingFace) |
| `drone_det_m.onnx` / `drone_det_l.onnx` | YOLO11m/l, дообучение Seraphim mini |
| `face_det.onnx` | InsightFace SCRFD (детекция лица) |
| `face_rec.onnx` | InsightFace ArcFace (эмбеддинг лица) |
| `body_reid.onnx` | OSNet x0_25 (эмбеддинг тела) |
| `object_encoder.onnx` | MobileNetV3-Small features (эмбеддинг объекта, 576-d) |

Скрипт `export_drone_models.py` — см. docstring: `--train-ml` только для m/l.
Старый `export_drone_model.py` (один YOLO26n) оставлен для совместимости, в приложении не используется.

## Объектный энкодер

Используется `torchvision` MobileNetV3-Small (features + global pool) — лёгкая и
надёжная замена MobileCLIP, не требует `open_clip`. Вход `1x3x256x256`, выход —
вектор 576 чисел. Веса скачиваются автоматически при первом запуске скрипта.
При желании можно заменить на MobileCLIP, экспортировав его image encoder в
`object_encoder.onnx` с тем же входом и обновив размер вектора в коде.

## После экспорта

Откройте каждую модель в [netron](https://netron.app) и зафиксируйте **имена и
формы входов/выходов**. Сверьте их с Kotlin-обёртками:

- `OnnxModel.kt` — использует первый вход/выход;
- `YoloeDetector.decode()` — формат выходных боксов;
- `CocoLabels` — словарь id→имя для prompt-free YOLOE;
- `FaceRecognizer.detectAndAlign()` — формат выхода SCRFD.

Если имена/формы отличаются — поправьте соответствующий Kotlin-код.
