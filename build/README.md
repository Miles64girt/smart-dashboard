# Сборка Smart Dashboard

Сборка без Gradle и Android Studio: `aapt2` → `javac` → `d8` → `zipalign` → `apksigner`.
Проверялось на Windows 10/11 с PowerShell 5.1.

Актуальный APK: `apk/SmartDashboard-1.3.apk`.

## Требования

- Windows 10/11 и PowerShell 5.1+ (встроенный в Windows подходит).
- JDK 17, Android build-tools 34 и `android.jar` API 24. Их ставит
  [`scripts/setup_build_env.ps1`](../scripts/setup_build_env.ps1) в `C:\AndroidBuild`.
- Интернет при первой установке инструментов (~350 МБ).
- Для установки на планшет — `adb` из Android Platform Tools (в `PATH` или `C:\platform-tools\adb.exe`).

## 1. Установить окружение сборки

Из корня репозитория:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup_build_env.ps1
```

Скрипт скачивает JDK 17 (Adoptium), build-tools 34 и platform API 24 в `C:\AndroidBuild`.
Повторный запуск безопасен: уже установленное пропускается.

Если нужен другой каталог:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup_build_env.ps1 -Base D:\AndroidBuild
```

## 2. Собрать APK

```powershell
powershell -ExecutionPolicy Bypass -File build\build.ps1
```

Готовый файл: `apk\SmartDashboard-1.3.apk` (versionName 1.3, versionCode 4).
Журнал сборки: `logs\build.log` (каталог `logs/` в `.gitignore`).

Сразу установить на подключённый планшет:

```powershell
powershell -ExecutionPolicy Bypass -File build\build.ps1 -Install
```

Полная настройка после установки (разрешения, служба доступности, Doze) —
[`scripts/install_deploy.ps1`](../scripts/install_deploy.ps1).

### Параметры build.ps1

| Параметр | По умолчанию | Смысл |
|---|---|---|
| `-ProjRoot` | родитель `build\` | корень проекта (где лежат `app\`, `apk\`) |
| `-BuildRoot` | `C:\AndroidBuild` | каталог с JDK и Android-инструментами (только чтение) |
| `-StageRoot` | `%TEMP%\smart-dashboard-build` | каталог для временных файлов сборки, только ASCII-путь |
| `-Adb` | пусто | путь к adb; пусто — искать в `PATH` и `C:\platform-tools\adb.exe` |
| `-Install` | выключено | после сборки выполнить `adb install -r` |

Почему staging в `%TEMP%`: `aapt2` не работает с кириллицей в путях, а каталог проекта может
содержать кириллические папки. Если `%TEMP%` тоже содержит кириллицу, укажите ASCII-путь явно:

```powershell
powershell -ExecutionPolicy Bypass -File build\build.ps1 -StageRoot C:\temp\sd-build
```

Каталог `C:\AndroidBuild` при сборке используется только для чтения — писать туда не нужно.

## 3. Ключ подписи (локально, не коммитится)

При первой сборке автоматически создаются два файла:

- `build\keystore.jks` — ключ подписи;
- `build\keystore.properties` — пароль и alias ключа (`storePassword=...`, `keyPassword=...`, `alias=...`).

Пароль генерируется случайно и нигде в репозитории не хранится.

> **Важно.** Эти файлы не попадают в git (они в `.gitignore`). Сохраните их в надёжном месте:
> с другим ключом подписи APK не установится поверх уже установленной версии — придётся удалять
> приложение и терять его настройки (свой список вещей).

Если `build\keystore.jks` есть, а `build\keystore.properties` пропал, сборка остановится
с понятным сообщением: пароля нет, подписать APK нечем.

## 4. Установка APK

Вручную:

```powershell
adb install -r apk\SmartDashboard-1.3.apk
```

Или скриптом с первичной настройкой (разрешение календаря, служба доступности, Doze, запуск):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\install_deploy.ps1
```

## Другие скрипты

| Скрипт | Назначение |
|---|---|
| [`scripts/install_deploy.ps1`](../scripts/install_deploy.ps1) | установка и первичная настройка на планшете |
| [`scripts/dashboard.ps1`](../scripts/dashboard.ps1) | запуск/остановка, скриншот, тесты (start, stop, shot, test_alarm, status и др.) |
| [`scripts/uninstall.ps1`](../scripts/uninstall.ps1) | удаление приложения с очисткой Doze и службы доступности |
| [`scripts/setup_build_env.ps1`](../scripts/setup_build_env.ps1) | установка окружения сборки |

Все скрипты ищут adb автоматически (в `PATH` или `C:\platform-tools\adb.exe`) и не привязаны
к модели или серийному номеру планшета.

## Подробнее

- Русский гайд по сборке и ошибкам: [docs/ru/BUILD.md](../docs/ru/BUILD.md)
- English build guide: [docs/en/BUILD.md](../docs/en/BUILD.md)
- Установка готового APK без сборки: [docs/ru/INSTALL.md](../docs/ru/INSTALL.md)
- Подгонка под своё устройство: [docs/ru/ADAPT.md](../docs/ru/ADAPT.md)

## Частые проблемы

| Проблема | Что делать |
|---|---|
| `Не найдены инструменты: ...` | Запустить `scripts\setup_build_env.ps1` или проверить `-BuildRoot` |
| `StageRoot содержит не-ASCII символы` | Задать `-StageRoot C:\temp\sd-build` |
| `aapt2 compile: код ошибки` | Проверить, что staging на ASCII-пути и в `app\res` нет ошибок |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | APK подписан другим ключом: удалить старую версию (`scripts\uninstall.ps1`) и установить заново |
| `adb не найден` | Установить Android Platform Tools, добавить в `PATH` или указать `-Adb "C:\platform-tools\adb.exe"` |
