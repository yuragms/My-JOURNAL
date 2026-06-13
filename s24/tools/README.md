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
ls -la /Users/yuragms/Cursor-cash/s24/app/src/main/assets/models
```

Ожидаемые файлы в `assets/models/`:

| Файл | Модель |
| --- | --- |
| `yoloe26s.onnx` | YOLOE-26 small (prompt-free, локализация) |
| `face_det.onnx` | InsightFace SCRFD (детекция лица) |
| `face_rec.onnx` | InsightFace ArcFace (эмбеддинг лица) |
| `body_reid.onnx` | OSNet x0_25 (эмбеддинг тела) |
| `object_encoder.onnx` | MobileCLIP-S0 image encoder (эмбеддинг объекта) |

## MobileCLIP image encoder

Экспортируется отдельно из репозитория `ml-mobileclip` / `open_clip` в файл
`mobileclip_s0_image.onnx` (вход `1x3x256x256`, выход — вектор эмбеддинга), затем
кладётся рядом и подхватывается `export_models.py` при повторном запуске.

## После экспорта

Откройте каждую модель в [netron](https://netron.app) и зафиксируйте **имена и
формы входов/выходов**. Сверьте их с Kotlin-обёртками:

- `OnnxModel.kt` — использует первый вход/выход;
- `YoloeDetector.decode()` — формат выходных боксов;
- `CocoLabels` — словарь id→имя для prompt-free YOLOE;
- `FaceRecognizer.detectAndAlign()` — формат выхода SCRFD.

Если имена/формы отличаются — поправьте соответствующий Kotlin-код.
