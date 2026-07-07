# IMX500 YOLO — детекция на акселераторе AI Camera

Распознавание объектов на Raspberry Pi 5 + **Sony IMX500** (Raspberry Pi AI Camera).  
Инференс на **чипе камеры**, не на CPU Pi.

| Модель | FPS на IMX500 | Источник `.rpk` |
|--------|---------------|-----------------|
| **YOLO11n** | ~30 | [raspberrypi/imx500-models](https://github.com/raspberrypi/imx500-models) |
| **YOLO26n** | ~30 (замер на Pi; модель тяжелее, ~90% памяти NPU) | [tweenietomatoes/YOLO26n-for-Sony-IMX500](https://github.com/tweenietomatoes/YOLO26n-for-Sony-IMX500) |

> **Важно:** официальный `yolo export format=imx` в Ultralytics поддерживает только **YOLO11n** и **YOLOv8n**.  
> YOLO26n здесь — готовая community-модель, уже квантованная и упакованная под IMX500.

## Содержимое папки

```
imx_yolo11n/
├── README.md
├── yolo11n_fps.py
├── yolo26n_fps.py
├── run.sh                    # YOLO11n + превью
├── run-headless.sh           # YOLO11n, только терминал
├── run-yolo26n.sh            # YOLO26n + превью
├── run-yolo26n-headless.sh   # YOLO26n, только терминал
├── download-model.sh
├── models/
│   ├── imx500_network_yolo11n_pp.rpk   (~3.1 MB)
│   └── imx500_network_yolo26n_pp.rpk   (~3.2 MB)
└── assets/
    └── coco_labels.txt
```

## Требования

```bash
sudo apt update
sudo apt install -y imx500-all python3-opencv python3-picamera2
```

## Быстрый старт

```bash
cd ~/imx_yolo11n
chmod +x *.sh yolo11n_fps.py yolo26n_fps.py
./download-model.sh   # если моделей нет

# YOLO11n (~30 FPS)
./run.sh

# YOLO26n (~30 FPS, архитектура YOLO26)
./run-yolo26n.sh
```

Остановка: **Ctrl+C**. Камера занята → `pkill -f yolo; sleep 2`.

## Замер FPS без окна

```bash
./run-headless.sh           # YOLO11n
./run-yolo26n-headless.sh   # YOLO26n
```

В терминале раз в секунду: `FPS: 29.9  obj: 2`

На экране (превью): `FPS: … | obj: … | IMX500 YOLO11n` или `YOLO26n`.

## Порог чувствительности

```bash
THRESHOLD=0.25 ./run.sh
THRESHOLD=0.40 ./run-yolo26n.sh
```

## Сравнение моделей

| | YOLO11n | YOLO26n |
|---|---------|---------|
| FPS на IMX500 | ~30 | ~30 (замер); NPU загружен сильнее |
| Память чипа | ~40% | ~90% (7.16/8 MB) |
| NMS | на чипе (pp) | на чипе (pp) |
| Классы | 80 COCO | 80 COCO |
| Официальный imx export | да | нет (community RPK) |

## SSH с Mac

```bash
ssh pi5
cd ~/imx_yolo11n
./run-yolo26n-headless.sh   # замер из SSH
./run-yolo26n.sh            # превью на мониторе Pi
```

VNC: `192.168.4.232:5900`

## Своя модель

Только **YOLO11n / YOLOv8n** через Ultralytics на Linux (Bookworm, Python 3.11):

```bash
yolo export model=yolo11n.pt format=imx data=coco8.yaml
imx500-package -i yolo11n_imx_model/packerOut.zip -o models/custom_rpk
python3 yolo11n_fps.py --model models/custom_rpk/network.rpk --bbox-normalization --bbox-order xy
```

## Устранение проблем

| Симптом | Решение |
|---------|---------|
| `Device or resource busy` | `pkill -f yolo; sleep 2` |
| Нет окна | Запуск на рабочем столе Pi, не SSH |
| YOLO26n медленнее на других сетапах | У автора RPK указано ~8 FPS; на нашем Pi — ~30 |
| Долгий старт | Первый раз заливается прошивка в IMX500 (~1 мин) |

## Pi

| | |
|---|---|
| IP | `192.168.4.232` |
| SSH | `ssh pi5` |
| Папка | `~/imx_yolo11n` |

Исходники: `s24/tools/imx500-yolo11n/`
