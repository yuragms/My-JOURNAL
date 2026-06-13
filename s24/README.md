# S24 Vision

Нативное Android-приложение для **Samsung Galaxy S24**: распознаёт объекты камерой
(YOLOE-26), пишет видео с рамками, обучает/дообучает профили людей (лицо + тело) и
объектов по роликам, показывает нагрузку CPU/GPU. Всё работает **на устройстве**,
без облака и интернета.

## Что умеет

- GUI на Jetpack Compose, доступ к **обеим камерам** (селфи и основная).
- Распознавание объектов в реальном времени (YOLOE-26, prompt-free).
- Запись видео с нарисованными рамками в папку при обнаружении объектов.
- Обучение по ролику: вводите имя и отвечаете «это человек?»:
  - **человек** → лицо (InsightFace) + тело (OSNet ReID) раздельно;
  - **объект** → эмбеддинг кропа (MobileCLIP), бокс берётся автоматически.
- Дообучение по новому ролику: профиль есть → «улучшен: было N, стало M»;
  нет → «создан новый».
- Оверлей нагрузки: CPU % и GPU % (GPU — best-effort, может быть `n/a`).

## Требования

- Mac M1 (или любой) с **Android Studio** (Apple Silicon) и Android SDK 35.
  Android Studio приносит JDK 17, SDK, `adb` и Gradle — отдельно их ставить не нужно.
- Samsung S24 с Android 9+.
- Кабель USB.

---

## 1. Подготовка моделей (один раз, на Mac)

Модели не лежат в git (большие). Готовятся скриптом в venv проекта `hikvision-test1`:

```bash
cd /Users/yuragms/Cursor-cash/hikvision-test1
source .venv/bin/activate
pip install -U ultralytics
python /Users/yuragms/Cursor-cash/s24/tools/export_models.py
ls -la /Users/yuragms/Cursor-cash/s24/app/src/main/assets/models
```

Должны появиться: `yoloe26s.onnx`, `face_det.onnx`, `face_rec.onnx`,
`body_reid.onnx`, `object_encoder.onnx`. Подробности и про MobileCLIP — в
`tools/README.md`.

> После первого экспорта откройте модели в [netron](https://netron.app) и сверьте
> имена/формы входов-выходов с Kotlin-обёртками (`OnnxModel`, `YoloeDetector`,
> `*Recognizer`). При расхождении поправьте декодирование — см. комментарии в коде.

---

## 2. Сборка APK (Mac)

Через Android Studio: `Open` → выбрать папку `s24` → `Run`.

Или из терминала (после первой генерации Gradle wrapper):

```bash
cd /Users/yuragms/Cursor-cash/s24
# один раз, если нет gradle wrapper:
gradle wrapper --gradle-version 8.7
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Юнит-тесты ядра:

```bash
./gradlew :app:testDebugUnitTest
```

---

## 3. Установка на Samsung S24

1. На S24: **Настройки → Сведения о телефоне → Сведения о ПО** → 7 раз нажать
   «Номер сборки» → включится режим разработчика.
2. **Настройки → Параметры разработчика** → включить **Отладка по USB**.
3. Подключить S24 к Mac кабелем, на телефоне разрешить отладку с этого компьютера.
4. Проверить подключение:

```bash
adb devices    # должен появиться серийный номер устройства
```

5. Установить и запустить:

```bash
cd /Users/yuragms/Cursor-cash/s24
./gradlew :app:installDebug
adb shell am start -n com.s24vision.app/.ui.MainActivity
```

(или вручную: `adb install -r app/build/outputs/apk/debug/app-debug.apk`)

6. При первом запуске разрешите доступ к камере.

---

## 4. Использование

- **Камера**: кнопка «Камера» переключает селфи/основную; рамки и подписи рисуются
  в реальном времени; сверху CPU %/GPU %; «REC» начинает запись, «Стоп» завершает.
- **Записи**: список роликов; «Обучить» открывает экран обучения по ролику.
- **Обучение**: ввести имя, переключатель «Это человек?», «Обучить / Дообучить».
  Появится сообщение «создан новый» или «улучшен: было N, стало M».
- **Профили**: список людей/объектов, удаление.

---

## 5. Где файлы на телефоне

```
Android/data/com.s24vision.app/files/
├── recordings/            видео с рамками (mp4)
└── profiles/
    ├── faces/             профили лиц (<имя>.bin)
    ├── bodies/            профили тел
    └── objects/           профили объектов
```

Видны при подключении по USB (режим передачи файлов / MTP).

---

## 6. Отладка

```bash
adb logcat -s S24Vision:* *:E
```

---

## 7. Известные места для доводки на устройстве

Эти участки помечены в коде и зависят от реальных артефактов (форма ONNX, железо):

- `YoloeDetector.decode()` / `CocoLabels` — формат выхода и словарь меток YOLOE
  после экспорта.
- `FaceRecognizer.detectAndAlign()` — декодирование SCRFD и выравнивание по 5 точкам
  (временно — ресайз кропа).
- `OverlayView` — масштаб боксов под режим превью.
- `GpuMonitor` — доступность Adreno sysfs (иначе `GPU: n/a`).

---

## Архитектура

```
CameraX → YOLOE-26 (боксы) → person? ── да → InsightFace (лицо) + OSNet (тело)
                                       └ нет → MobileCLIP (эмбеддинг кропа)
                                                   │
                                   косинус с профилями (profiles/)
                                                   │
                              подпись: имя / класс / «неопознанный объект»
                                                   │
                          оверлей + запись кадров с рамками (recordings/)
```

«Обучение» = сбор эмбеддингов из кадров видео (не тренировка весов). Распознавание
= косинусная близость к сохранённым эмбеддингам. Та же парадигма, что в desktop-
проекте `hikvision-test1`.
