# Скриншоты / Screenshots

Демонстрационные скриншоты [Smart Dashboard](../..) с планшета **Huawei MediaPad T3 7 (BG2-U01)**, Android 7.0, экран **600×1024**.
Каждое изображение — ровно 600×1024 px, формат PNG.

> **Демонстрационные данные: события и вещи вымышлены.**
> Все события календаря, названия, время, предметы в списке вещей и город на скриншотах — выдуманные и набраны заново при подготовке изображений. Ничего личного в этих файлах нет.
> Actual screenshots are anonymised demos: every calendar event, item and the city are fictional.

| Файл | Что показано |
|---|---|
| [`01_dashboard_day.png`](01_dashboard_day.png) | Дневная панель: слева погода, «что надеть сегодня» и подпись города «Москва»; справа — демо-события «Встреча», «Обед», «Тренировка» с временем начала и окончания. |
| [`02_dashboard_night.png`](02_dashboard_night.png) | Ночной режим: экран притушен почти до чёрного, текст не читается; внизу подсказка «касание — включить экран». |
| [`03_packing_empty.png`](03_packing_empty.png) | Вечернее окно со списком вещей в исходном состоянии: «Список пока пуст» и подсказка нажать «+ Добавить свой пункт» ниже. Фон панели размыт. |
| [`04_packing_items.png`](04_packing_items.png) | То же окно с демо-пунктами: «Документы» и «Ключи» отмечены (зелёный квадрат с галочкой, текст зачёркнут), «Зарядка», «Наушники», «Бутылка воды» — нет. |
| [`05_add_item.png`](05_add_item.png) | Диалог «Добавить свой пункт»: в поле введено «Ключи», кнопки «Отмена» и «Добавить». |
| [`06_success.png`](06_success.png) | Анимация успеха: зелёная галочка, «Всё собрано!» и «Можно ложиться спать». |

## English

| File | What it shows |
|---|---|
| `01_dashboard_day.png` | Day dashboard: weather, "what to wear today" and the city label "Москва" on the left; demo events "Встреча", "Обед", "Тренировка" with start and end times on the right. |
| `02_dashboard_night.png` | Night mode: the screen is dimmed to near-black, nothing is readable; the hint "касание — включить экран" ("touch to wake the screen") sits at the bottom. |
| `03_packing_empty.png` | Evening packing window in its default state: "Список пока пуст" ("The list is empty") plus a hint to press "+ Добавить свой пункт" below. The dashboard behind it is blurred. |
| `04_packing_items.png` | The same window with demo items: "Документы" and "Ключи" are checked (green box with a tick, strikethrough text); "Зарядка", "Наушники" and "Бутылка воды" are not. |
| `05_add_item.png` | The "Добавить свой пункт" ("Add your own item") dialog with "Ключи" typed in and the "Отмена" / "Добавить" buttons. |
| `06_success.png` | The success animation: a green tick, "Всё собрано!" ("All packed!") and "Можно ложиться спать" ("You can go to bed"). |

## Источник / How the images were made

Скриншоты собраны скриптом `screens_build.py` (Python + Pillow) из кадров, снятых на планшете: личные данные (имена преподавателей, реальные события календаря, личный список вещей, город, часы в статус-баре) закрашены фоном или вырезаны — не размыты «до читаемости», — а демо-события и демо-пункты набраны заново шрифтами Segoe UI.

The images were produced by `screens_build.py` (Python + Pillow) from real device captures: personal data (teacher names, real calendar events, the personal packing list, the city, the status-bar clock) is painted over or cut out, not blurred into readability, and the demo events and demo items are typeset anew.

Если вы сделали свои скриншоты — пришлите их pull request'ом; лучше без имён, личных событий и геолокации.

---

[← Назад к README](../../README.md) · [Back to README.en.md](../../README.en.md)
