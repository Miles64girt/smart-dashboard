# Сборка из исходников

Как собрать `SmartDashboard-1.3.apk` на Windows без Android Studio и Gradle: только PowerShell, JDK и официальные Android-утилиты.

> ⚠️ **Проверено только на одном планшете.** Сборка проверена на Windows с Huawei MediaPad T3 7 (BG2-U01), Android 7.0. На других версиях Windows и прошивках возможны отличия. Перед использованием проверьте результат по [чек-листу](TESTING.md). Используете на свой риск.

## Что понадобится

| Требование | Детали |
|---|---|
| ОС | Windows 7 или новее (проверялось на Windows 10/11) |
| PowerShell | 5.1 или новее — встроенная «Windows PowerShell» подходит |
| Интернет | только для первой установки инструментов, скачивается около 320–350 МБ |
| Свободное место | примерно 1 ГБ на диске `C:` |
| ADB | нужен, только если хотите ставить APK сразу с компьютера |
| Android Studio / Gradle | **не нужны** |

Для первой установки инструментов понадобится разрешение на запись в `C:\AndroidBuild` (папка создаётся автоматически).

## Как устроена сборка

```
scripts\setup_build_env.ps1   → один раз скачивает инструменты в C:\AndroidBuild
build\build.ps1               → собирает APK: aapt2 → javac → d8 → zipalign → apksigner
apk\SmartDashboard-1.3.apk    → готовый подписанный файл
```

`build.ps1` не использует Gradle: он вручную компилирует ресурсы и Java-код, собирает `classes.dex` и подписывает APK. Так сборка занимает секунды и не тянет сотни мегабайт зависимостей.

## Шаг 1. Установите среду сборки

Один раз запустите из корня репозитория:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup_build_env.ps1
```

Скрипт скачивает и распаковывает в `C:\AndroidBuild`:

- **JDK 17** (Adoptium/Temurin) — `C:\AndroidBuild\jdk17`;
- **Android build-tools 34** — `aapt2`, `d8`, `zipalign`, `apksigner` — `C:\AndroidBuild\buildtools`;
- **platform API 24** — `android.jar` — `C:\AndroidBuild\platform24`.

Если что-то уже скачано, повторный запуск не будет качать заново: скрипт проверяет каталоги и архивы. Логи и понятные сообщения об ошибках скрипт выводит в консоль.

Другой каталог для инструментов можно задать параметром `-Base`, но тогда тот же путь нужно передать в `build.ps1` через `-BuildRoot`.

> Путь `C:\AndroidBuild` выбран не случайно: `aapt2` плохо работает с кириллицей в путях, поэтому среда сборки и временный рабочий каталог должны быть в ASCII-пути. Если ваш `%TEMP%` содержит русские буквы, сборка подскажет указать `-StageRoot` вручную.

## Шаг 2. Соберите APK

```powershell
powershell -ExecutionPolicy Bypass -File .\build\build.ps1
```

Что происходит по шагам:

1. ресурсы компилируются `aapt2 compile` и линкуются `aapt2 link` с манифестом;
2. Java-исходники компилируются `javac` (Java 8) против `android.jar` (API 24);
3. байткод переводится в `classes.dex` утилитой `d8` (`--release`, `--min-api 24`);
4. `classes.dex` упаковывается в APK, выполняется `zipalign`;
5. APK подписывается `apksigner`, затем подпись проверяется (`apksigner verify`).

Готовый файл: **`apk\SmartDashboard-1.3.apk`** (versionName 1.3, versionCode 4). В конце сборки скрипт печатает размер и SHA-256 файла.

Сборка идёт во временном каталоге вне репозитория — по умолчанию `%TEMP%\smart-dashboard-build\work`. Это сделано специально: путь к репозиторию может содержать кириллицу, а `aapt2` её не понимает. Если хочется задать свой ASCII-путь — используйте параметр `-StageRoot`. Подробный лог пишется в `logs\build.log` (каталог в `.gitignore`, в репозиторий не попадает).

### Параметры `build.ps1`

| Параметр | По умолчанию | Зачем |
|---|---|---|
| `-ProjRoot` | каталог репозитория | путь к проекту, если скрипт запускают из другого места |
| `-BuildRoot` | `C:\AndroidBuild` | где лежат JDK, build-tools и platform (только чтение) |
| `-StageRoot` | `%TEMP%\smart-dashboard-build` | каталог для временных файлов сборки; должен быть ASCII-путём и вне репозитория |
| `-Adb` | пусто | путь к `adb.exe`; если не задан, adb ищется в `PATH` и `C:\platform-tools` |
| `-Install` | выключен | сразу установить собранный APK на подключённый планшет |

Пример сборки и установки одной командой:

```powershell
powershell -ExecutionPolicy Bypass -File .\build\build.ps1 -Install
```

## Ключ подписи

Android не устанавливает APK без подписи. Ключ создаётся **локально при первой сборке** — в репозитории его нет и быть не должно.

| Файл | Что это |
|---|---|
| `build\keystore.jks` | сам ключ подписи (генерируется автоматически) |
| `build\keystore.properties` | пароли и alias к этому ключу (генерируются автоматически) |
| `logs\build.log` | журнал сборки |

Правила простые:

- **оба файла в `.gitignore`** — никогда не коммитьте их и не выкладывайте;
- пароль генерируется случайным при первой сборке и записывается в `keystore.properties`;
- **сохраните эти два файла**. Если потерять ключ, новую сборку нельзя будет установить поверх старой — придётся удалять приложение вместе с настройками;
- перенос сборки на другой компьютер: скопируйте `build\keystore.jks` и `build\keystore.properties` вместе с проектом;
- если `keystore.jks` есть, а `keystore.properties` нет, сборка остановится с понятной ошибкой — восстановите properties или удалите оба файла, чтобы сгенерировать новую пару (тогда обновление поверх старой установки станет невозможным).

> APK из папки `apk/` подписан ключом автора. Сборка из исходников даёт **другую подпись**: такой APK не встанет поверх репозиторного, пока вы не удалите старую версию.

## Шаг 3. Установите на планшет

Если включена отладка по USB и планшет виден в `adb devices`:

```powershell
# установить или обновить
adb install -r .\apk\SmartDashboard-1.3.apk

# выдать разрешение на календарь
adb shell pm grant ru.home.dashboard android.permission.READ_CALENDAR

# запустить
adb shell am start -n ru.home.dashboard/.MainActivity
```

Либо просто скопируйте APK на планшет и установите вручную — как в [INSTALL.md](INSTALL.md).

Если `adb` не установлен, скачайте platform-tools с сайта Android или укажите путь вручную:

```powershell
powershell -ExecutionPolicy Bypass -File .\build\build.ps1 -Adb "C:\platform-tools\adb.exe" -Install
```

## Чистая пересборка

Если сборка ведёт себя странно, удалите временные файлы и соберите заново:

```powershell
Remove-Item -Recurse -Force "$env:TEMP\smart-dashboard-build" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\logs -ErrorAction SilentlyContinue
powershell -ExecutionPolicy Bypass -File .\build\build.ps1
```

Если вы задавали свой каталог через `-StageRoot`, удаляйте его. **Не удаляйте** `build\keystore.jks` и `build\keystore.properties` — это ключ подписи. Временный каталог сборки можно удалять в любой момент, он пересоздаётся.

## Типичные ошибки

| Ошибка | Причина | Что делать |
|---|---|---|
| `Не найдены инструменты: ...` | среда не установлена или лежит в другом месте — нет JDK, build-tools или platform | запустите `scripts\setup_build_env.ps1`; проверьте `-BuildRoot` и каталоги `jdk17\`, `buildtools\`, `platform24\` |
| `aapt2: error ...` на путях с русскими буквами | `aapt2` не дружит с кириллицей | не переносите `BuildRoot` и `StageRoot` в путь с русскими буквами |
| `StageRoot содержит не-ASCII символы` | в `%TEMP%` есть кириллица | задайте временный каталог вручную: `-StageRoot C:\temp\sd-build` |
| `Найден ключ подписи ..., но нет файла ...keystore.properties` | потерян файл с паролем | восстановите `keystore.properties` из копии или удалите `keystore.jks` (подпись станет другой) |
| `не удалось скачать ...` при setup | нет интернета, блокировка, прокси | проверьте сеть и повторите; при корпоративном прокси настройте его в системе |
| `adb: no devices/emulators found` | планшет не подключён или нет драйвера | другой кабель/порт, включите отладку по USB, проверьте `adb devices` |
| `adb: device unauthorized` | не подтверждён запрос на компьютере | разблокируйте планшет и нажмите «Разрешить» в диалоге отладки |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | установленная версия подписана другим ключом | удалите старую версию (`adb uninstall ru.home.dashboard`) и ставьте новую |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | `versionCode` новой сборки ниже установленной | соберите версию с большим `versionCode` или удалите старую |
| `выполнение сценария запрещено` | политика выполнения PowerShell | запускайте с `-ExecutionPolicy Bypass`, как в примерах |
| `apksigner verify: подпись не прошла проверку` | повреждён или не тот ключ подписи | проверьте `build\keystore.*`, при необходимости пересоберите |
| синтаксические ошибки, «не удаётся распознать термин» | Windows PowerShell старше 5.1 | обновите PowerShell (Windows Management Framework) или работайте на Windows 10/11 |

Если ошибка не из этого списка — пришлите полный текст и последние строки `logs\build.log`: там видно, на каком шаге всё остановилось.

## Что дальше

- [INSTALL.md](INSTALL.md) — установка и первичная настройка на планшете.
- [ADAPT.md](ADAPT.md) — как изменить город, расписание, список вещей и вёрстку перед сборкой.
- [TESTING.md](TESTING.md) — что проверить после установки своей сборки.
- [build/README.md](../../build/README.md) — краткая шпаргалка по сборке.
