<#
.SYNOPSIS
  Установка и первичная настройка Smart Dashboard на подключённом Android-планшете через ADB.

.DESCRIPTION
  Скрипт не привязан к конкретной модели и серийному номеру: работает с любым устройством,
  которое видит adb. Если подключено несколько устройств, укажите нужное через -Serial.

  Что делает:
    1. находит adb (параметр -Adb, PATH или C:\platform-tools\adb.exe);
    2. находит самый свежий apk\SmartDashboard-*.apk (или берёт -Apk);
    3. ставит APK (adb install -r);
    4. выдаёт разрешение READ_CALENDAR;
    5. включает службу доступности Smart Dashboard (кнопка питания);
    6. выставляет screen_brightness_mode=1 и sound_effects_enabled=0;
    7. добавляет приложение в исключения Doze;
    8. запускает панель и печатает статус (модель, версия, процесс).

  Всё, что не удалось выставить через adb, можно включить вручную в настройках планшета.

.PARAMETER Adb
  Путь к adb. По умолчанию пусто — adb ищется в PATH, затем в C:\platform-tools\adb.exe.

.PARAMETER Apk
  Путь к APK. По умолчанию — самый свежий файл apk\SmartDashboard-*.apk.

.PARAMETER Serial
  Серийный номер устройства (adb -s). Нужен только если подключено несколько устройств.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\install_deploy.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\install_deploy.ps1 -Serial ABC123DEF -Apk .\apk\SmartDashboard-1.3.apk
#>
[CmdletBinding()]
param(
    [string]$Adb = '',
    [string]$Apk = '',
    [string]$Serial = ''
)

$ErrorActionPreference = 'Continue'
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$PackageName = 'ru.home.dashboard'
$ProjectPath = Split-Path $PSScriptRoot -Parent

function Resolve-AdbPath {
    param([string]$ExplicitPath)
    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        if (-not (Test-Path -LiteralPath $ExplicitPath)) {
            throw ('adb не найден по указанному пути: ' + $ExplicitPath)
        }
        return (Resolve-Path -LiteralPath $ExplicitPath).Path
    }
    $cmd = Get-Command -Name 'adb' -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cmd -and $cmd.Source) { return $cmd.Source }
    $standard = 'C:\platform-tools\adb.exe'
    if (Test-Path -LiteralPath $standard) { return $standard }
    return $null
}

$script:TargetSerial = $Serial
try {
    $script:AdbPath = Resolve-AdbPath -ExplicitPath $Adb
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}

function Sh {
    param([string]$Command)
    $output = & $script:AdbPath -s $script:TargetSerial shell $Command 2>&1
    return ($output | Out-String).Trim()
}

function ShLines {
    param([string]$Command)
    return @((Sh -Command $Command) -split "`r?`n" | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_.Length -gt 0 })
}

Write-Host '=== Smart Dashboard: установка на устройство ===' -ForegroundColor Cyan

if (-not $script:AdbPath) {
    Write-Host 'adb не найден.' -ForegroundColor Red
    Write-Host 'Установите Android Platform Tools и добавьте adb в PATH, либо укажите путь:' -ForegroundColor Yellow
    Write-Host '  powershell -ExecutionPolicy Bypass -File .\install_deploy.ps1 -Adb "C:\platform-tools\adb.exe"' -ForegroundColor White
    exit 1
}
Write-Host ('adb: ' + $script:AdbPath)

# --- выбор устройства ---
& $script:AdbPath start-server 2>&1 | Out-Null
$devicesRaw = (& $script:AdbPath devices 2>&1 | Out-String)
$serials = @()
foreach ($line in ($devicesRaw -split "`r?`n")) {
    $trimmed = $line.Trim()
    if ($trimmed -match '^(\S+)\s+device$') {
        $serials += $Matches[1]
    }
}

if ($serials.Count -eq 0) {
    Write-Host $devicesRaw.Trim() -ForegroundColor Yellow
    Write-Host 'Не найдено ни одного подключённого устройства.' -ForegroundColor Red
    Write-Host 'Проверьте кабель, включите «Отладку по USB» и подтвердите окно авторизации на планшете.' -ForegroundColor Yellow
    Write-Host 'Посмотреть устройства: adb devices' -ForegroundColor Yellow
    exit 1
}

if ([string]::IsNullOrWhiteSpace($script:TargetSerial)) {
    if ($serials.Count -gt 1) {
        Write-Host ('Подключено несколько устройств: ' + ($serials -join ', ')) -ForegroundColor Red
        Write-Host 'Укажите нужное: -Serial <серийный номер из списка выше>.' -ForegroundColor Yellow
        exit 1
    }
    $script:TargetSerial = $serials[0]
} elseif (-not ($serials -contains $script:TargetSerial)) {
    Write-Host ('Устройство ' + $script:TargetSerial + ' не найдено. Доступные: ' + ($serials -join ', ')) -ForegroundColor Red
    exit 1
}

Write-Host ('Устройство: ' + $script:TargetSerial)
$model = Sh 'getprop ro.product.model'
$androidVersion = Sh 'getprop ro.build.version.release'
$sdkVersion = Sh 'getprop ro.build.version.sdk'
Write-Host ('Модель: ' + $model + ' | Android ' + $androidVersion + ' (API ' + $sdkVersion + ')')

# --- APK ---
if ([string]::IsNullOrWhiteSpace($Apk)) {
    $apkDir = Join-Path $ProjectPath 'apk'
    if (-not (Test-Path -LiteralPath $apkDir)) {
        Write-Host ('Каталог ' + $apkDir + ' не найден. Сначала соберите APK: build\build.ps1') -ForegroundColor Red
        exit 1
    }
    $candidates = @(Get-ChildItem -LiteralPath $apkDir -Filter 'SmartDashboard-*.apk' -File -ErrorAction SilentlyContinue)
    if ($candidates.Count -eq 0) {
        Write-Host 'В папке apk\ нет файлов SmartDashboard-*.apk.' -ForegroundColor Red
        Write-Host 'Соберите APK: powershell -ExecutionPolicy Bypass -File .\build\build.ps1' -ForegroundColor Yellow
        exit 1
    }
    $latest = $candidates | Sort-Object -Property LastWriteTime, Name | Select-Object -Last 1
    $Apk = $latest.FullName
    Write-Host ('APK: ' + $Apk + ' (' + [math]::Round($latest.Length / 1KB, 1) + ' КБ, ' + $latest.LastWriteTime.ToString('yyyy-MM-dd HH:mm') + ')')
} else {
    if (-not (Test-Path -LiteralPath $Apk)) {
        Write-Host ('APK не найден: ' + $Apk) -ForegroundColor Red
        exit 1
    }
    $Apk = (Resolve-Path -LiteralPath $Apk).Path
    Write-Host ('APK: ' + $Apk)
}

# --- установка ---
Write-Host ''
Write-Host '--- Установка APK ---' -ForegroundColor Cyan
$installOut = (& $script:AdbPath -s $script:TargetSerial install -r $Apk 2>&1 | Out-String)
$installCode = $LASTEXITCODE
Write-Host $installOut.Trim()
if ($installCode -ne 0 -or $installOut -notmatch 'Success') {
    if ($installOut -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' -or $installOut -match 'signatures do not match') {
        Write-Host 'APK подписан другим ключом, чем установленная версия.' -ForegroundColor Red
        Write-Host 'Удалите старую версию и повторите: powershell -ExecutionPolicy Bypass -File .\scripts\uninstall.ps1' -ForegroundColor Yellow
    } else {
        Write-Host ('adb install завершился с ошибкой (код ' + $installCode + ').') -ForegroundColor Red
    }
    exit 1
}

# --- разрешение календаря ---
Write-Host ''
Write-Host '--- Разрешение на календарь (READ_CALENDAR) ---' -ForegroundColor Cyan
$grantOut = Sh ('pm grant ' + $PackageName + ' android.permission.READ_CALENDAR')
if ($grantOut -match 'Error|Exception|error') {
    Write-Host ('pm grant: ' + $grantOut) -ForegroundColor Yellow
    Write-Host 'Разрешение можно выдать вручную: Настройки → Приложения → Smart Dashboard → Разрешения → Календарь.' -ForegroundColor Yellow
}
$packageDump = Sh ('dumpsys package ' + $PackageName)
$grantLines = @($packageDump -split "`r?`n" | Where-Object { $_ -match 'READ_CALENDAR' } | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -gt 0 })
if ($grantLines.Count -gt 0) {
    Write-Host ('READ_CALENDAR: ' + ($grantLines -join ' | '))
} else {
    Write-Host 'READ_CALENDAR: статус не найден в dumpsys (проверьте разрешение в настройках).' -ForegroundColor Yellow
}

# --- служба доступности ---
Write-Host ''
Write-Host '--- Служба доступности (кнопка питания) ---' -ForegroundColor Cyan
Sh ('settings put secure enabled_accessibility_services ' + $PackageName + '/.PowerAccessibilityService') | Out-Null
Sh 'settings put secure accessibility_enabled 1' | Out-Null
Write-Host ('enabled_accessibility_services: ' + (Sh 'settings get secure enabled_accessibility_services'))
Write-Host ('accessibility_enabled: ' + (Sh 'settings get secure accessibility_enabled'))
Write-Host 'Если кнопка питания не работает: Настройки → Специальные возможности → Smart Dashboard → включить.' -ForegroundColor Yellow

# --- автояркость ---
Write-Host ''
Write-Host '--- Режим яркости ---' -ForegroundColor Cyan
Sh 'settings put system screen_brightness_mode 1' | Out-Null
Write-Host ('screen_brightness_mode: ' + (Sh 'settings get system screen_brightness_mode'))

# --- звуки ---
Write-Host ''
Write-Host '--- Системные звуки ---' -ForegroundColor Cyan
Sh 'settings put system sound_effects_enabled 0' | Out-Null
Write-Host ('sound_effects_enabled: ' + (Sh 'settings get system sound_effects_enabled'))

# --- Doze ---
Write-Host ''
Write-Host '--- Исключение из Doze ---' -ForegroundColor Cyan
Sh ('dumpsys deviceidle whitelist +' + $PackageName) | Out-Null
$dozeLines = @((Sh 'dumpsys deviceidle whitelist') -split "`r?`n" | Where-Object { $_ -match [regex]::Escape($PackageName) } | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -gt 0 })
if ($dozeLines.Count -gt 0) {
    Write-Host ($dozeLines -join ' | ')
} else {
    Write-Host 'Не удалось подтвердить исключение из Doze (обычно не критично).' -ForegroundColor Yellow
}

# --- автозапуск: подсказка ---
Write-Host ''
Write-Host '--- Автозапуск после перезагрузки ---' -ForegroundColor Cyan
Write-Host 'На прошивках Huawei/EMUI разрешите автозапуск вручную:' -ForegroundColor Yellow
Write-Host 'Настройки → Батарея → Запуск приложений → Smart Dashboard → разрешить.' -ForegroundColor Yellow

# --- запуск ---
Write-Host ''
Write-Host '--- Запуск панели ---' -ForegroundColor Cyan
Sh ('am start -n ' + $PackageName + '/.MainActivity') | Out-Null
Start-Sleep -Seconds 3

# --- статус ---
Write-Host ''
Write-Host '--- Статус ---' -ForegroundColor Cyan
$packageDump = Sh ('dumpsys package ' + $PackageName)
$versionLine = @($packageDump -split "`r?`n" | Where-Object { $_ -match 'versionName' } | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -gt 0 }) | Select-Object -First 1
if (-not $versionLine) { $versionLine = 'не определена' }
Write-Host ('Модель:  ' + (Sh 'getprop ro.product.model'))
Write-Host ('Версия:  ' + $versionLine)

$psLines = @((Sh 'ps') -split "`r?`n" | Where-Object { $_ -match [regex]::Escape($PackageName) } | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -gt 0 })
if ($psLines.Count -gt 0) {
    Write-Host ('Процесс: ' + $psLines[0])
} else {
    Write-Host 'Процесс: не найден (приложение не запущено?)' -ForegroundColor Yellow
}

$activityDump = Sh 'dumpsys activity activities'
$resumedLine = @($activityDump -split "`r?`n" | Where-Object { $_ -match 'mResumedActivity' } | ForEach-Object { $_.Trim() } | Where-Object { $_.Length -gt 0 }) | Select-Object -First 1
if ($resumedLine) { Write-Host ('Активность: ' + $resumedLine) }

$wakeLines = @((Sh 'dumpsys power') -split "`r?`n" | Where-Object { $_ -match 'mWakefulness=' } | ForEach-Object { $_.Trim() }) | Select-Object -First 1
if ($wakeLines) { Write-Host ('Экран: ' + $wakeLines) }

Write-Host ''
Write-Host 'Готово. Панель должна быть на экране планшета.' -ForegroundColor Green
Write-Host 'Если что-то не работает — docs\ru\INSTALL.md и docs\ru\FAQ.md.' -ForegroundColor Cyan
