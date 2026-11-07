<#
.SYNOPSIS
  Управление панелью Smart Dashboard через adb: запуск, остановка, скриншот, тесты.

.DESCRIPTION
  Скрипт не привязан к модели и серийному номеру планшета: adb ищется автоматически
  (параметр -Adb, затем PATH, затем C:\platform-tools\adb.exe).
  Если подключено несколько устройств — укажите нужное через -Serial.

  Действия (-Action):
    status             статус приложения и экрана (действие по умолчанию)
    start              запустить панель в дневном режиме
    stop               остановить приложение
    shot               скриншот экрана в logs\screenshots\
    test_alarm         ночной режим + будильник через -Seconds секунд
    flip_auto          ориентация по акселерометру (как в обычной работе)
    flip_180           принудительно перевернуть интерфейс на 180°
    flip_0             вернуть обычную ориентацию
    packing            показать вечерний список вещей
    packing_check_all  отметить все пункты и показать анимацию успеха
    power_menu         открыть системное меню питания тапом (нужны -X и -Y)

  Координаты кнопки питания зависят от разрешения экрана, поэтому power_menu
  без явных -X и -Y ничего не нажимает. Координаты можно посмотреть на скриншоте
  (-Action shot) или включив «Показывать касания» в настройках разработчика.

.PARAMETER Action
  Действие: start, stop, shot, test_alarm, status, flip_auto, flip_180, flip_0,
  packing, packing_check_all, power_menu.

.PARAMETER Adb
  Путь к adb. По умолчанию пусто — adb ищется в PATH, затем в C:\platform-tools\adb.exe.

.PARAMETER Serial
  Серийный номер устройства (adb -s). Нужен только если подключено несколько устройств.

.PARAMETER Seconds
  Задержка будильника для test_alarm, секунды. По умолчанию 60.

.PARAMETER X
  Координата X тапа для power_menu (пиксели). По умолчанию не задана.

.PARAMETER Y
  Координата Y тапа для power_menu (пиксели). По умолчанию не задана.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\dashboard.ps1 -Action start

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\dashboard.ps1 -Action shot

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\dashboard.ps1 -Action test_alarm -Seconds 60

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\dashboard.ps1 -Action power_menu -X 560 -Y 980
#>
[CmdletBinding()]
param(
    [ValidateSet('start', 'stop', 'shot', 'test_alarm', 'status', 'flip_auto', 'flip_180', 'flip_0', 'packing', 'packing_check_all', 'power_menu')]
    [string]$Action = 'status',
    [string]$Adb = '',
    [string]$Serial = '',
    [int]$Seconds = 60,
    [int]$X = -1,
    [int]$Y = -1
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

if (-not $script:AdbPath) {
    Write-Host 'adb не найден.' -ForegroundColor Red
    Write-Host 'Установите Android Platform Tools и добавьте adb в PATH, либо укажите путь:' -ForegroundColor Yellow
    Write-Host '  powershell -ExecutionPolicy Bypass -File .\dashboard.ps1 -Action status -Adb "C:\platform-tools\adb.exe"' -ForegroundColor White
    exit 1
}

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
    Write-Host 'Не найдено ни одного подключённого устройства.' -ForegroundColor Red
    Write-Host 'Проверьте кабель, включите «Отладку по USB» и подтвердите окно авторизации на планшете.' -ForegroundColor Yellow
    exit 1
}
if ([string]::IsNullOrWhiteSpace($script:TargetSerial)) {
    if ($serials.Count -gt 1) {
        Write-Host ('Подключено несколько устройств: ' + ($serials -join ', ') + '. Укажите нужное: -Serial <номер>.') -ForegroundColor Red
        exit 1
    }
    $script:TargetSerial = $serials[0]
} elseif (-not ($serials -contains $script:TargetSerial)) {
    Write-Host ('Устройство ' + $script:TargetSerial + ' не найдено. Доступные: ' + ($serials -join ', ')) -ForegroundColor Red
    exit 1
}

function Get-Lines {
    param([string]$Text)
    return @($Text -split "`r?`n" | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_.Length -gt 0 })
}

switch ($Action) {
    'start' {
        Sh ('am start -n ' + $PackageName + '/.MainActivity -e force_mode day') | Out-Null
        Write-Host 'Панель запущена (дневной режим).' -ForegroundColor Green
    }
    'stop' {
        Sh ('am force-stop ' + $PackageName) | Out-Null
        Write-Host 'Панель остановлена.' -ForegroundColor Green
    }
    'shot' {
        $dir = Join-Path $ProjectPath 'logs\screenshots'
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        $name = 'shot_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '.png'
        $remote = '/sdcard/_dsh_shot.png'
        $dest = Join-Path $dir $name
        Sh ('screencap -p ' + $remote) | Out-Null
        & $script:AdbPath -s $script:TargetSerial pull $remote $dest 2>&1 | Out-Null
        $pullCode = $LASTEXITCODE
        Sh ('rm -f ' + $remote) | Out-Null
        if ($pullCode -ne 0 -or -not (Test-Path -LiteralPath $dest)) {
            Write-Host 'Не удалось получить скриншот.' -ForegroundColor Red
            exit 1
        }
        Write-Host ('Скриншот: ' + $dest) -ForegroundColor Green
    }
    'test_alarm' {
        if ($Seconds -lt 1) {
            Write-Host '-Seconds должен быть больше 0.' -ForegroundColor Red
            exit 1
        }
        Write-Host ('Ночной режим + тестовый будильник через ' + $Seconds + ' с...')
        Sh ('am force-stop ' + $PackageName) | Out-Null
        Sh ('am start -n ' + $PackageName + '/.MainActivity -e force_mode night --ei test_alarm ' + $Seconds) | Out-Null
        Start-Sleep -Seconds ($Seconds + 8)
        $wakeLine = @((Get-Lines (Sh 'dumpsys power')) | Where-Object { $_ -match 'mWakefulness=' }) | Select-Object -First 1
        if ($wakeLine) { Write-Host ('Экран: ' + $wakeLine) } else { Write-Host 'Экран: статус не определён' -ForegroundColor Yellow }
        $resumedLine = @((Get-Lines (Sh 'dumpsys activity activities')) | Where-Object { $_ -match 'mResumedActivity' }) | Select-Object -First 1
        if ($resumedLine) { Write-Host ('Активность: ' + $resumedLine) }
    }
    'flip_auto' {
        Sh ('am start -n ' + $PackageName + '/.MainActivity -e force_mode day') | Out-Null
        Write-Host 'Ориентация: автоматически по акселерометру.' -ForegroundColor Green
    }
    'flip_180' {
        Sh ('am start -n ' + $PackageName + '/.MainActivity -e force_mode day --ei flip 1') | Out-Null
        Write-Host 'Ориентация принудительно перевёрнута на 180°.' -ForegroundColor Green
    }
    'flip_0' {
        Sh ('am start -n ' + $PackageName + '/.MainActivity -e force_mode day --ei flip 0') | Out-Null
        Write-Host 'Ориентация принудительно обычная.' -ForegroundColor Green
    }
    'packing' {
        Sh ('am start -n ' + $PackageName + '/.MainActivity -e force_mode day --ez manual_list true') | Out-Null
        Write-Host 'Открыт вечерний список вещей.' -ForegroundColor Green
    }
    'packing_check_all' {
        Sh ('am start -n ' + $PackageName + '/.MainActivity -e force_mode day --ez check_all true') | Out-Null
        Write-Host 'Все пункты отмечены, показана анимация успеха.' -ForegroundColor Green
    }
    'power_menu' {
        if ($X -lt 0 -or $Y -lt 0) {
            Write-Host 'Для power_menu нужны координаты кнопки питания: -X <пиксели> -Y <пиксели>.' -ForegroundColor Yellow
            Write-Host 'Координаты зависят от разрешения и плотности экрана — тап вслепую может нажать не туда.' -ForegroundColor Yellow
            Write-Host 'Подсказка: сделайте скриншот (-Action shot) или включите «Показывать касания» в настройках разработчика.' -ForegroundColor Yellow
            exit 1
        }
        Sh ('am start -n ' + $PackageName + '/.MainActivity -e force_mode day') | Out-Null
        Start-Sleep -Seconds 2
        Sh ('input tap ' + $X + ' ' + $Y) | Out-Null
        Write-Host ('Нажата кнопка питания в точке (' + $X + ', ' + $Y + ').') -ForegroundColor Green
    }
    'status' {
        $psLines = @((Get-Lines (Sh 'ps')) | Where-Object { $_ -match [regex]::Escape($PackageName) })
        if ($psLines.Count -gt 0) {
            Write-Host ('Процесс:    ' + $psLines[0])
        } else {
            Write-Host 'Процесс:    не найден (приложение не запущено?)' -ForegroundColor Yellow
        }

        $resumedLine = @((Get-Lines (Sh 'dumpsys activity activities')) | Where-Object { $_ -match 'mResumedActivity' }) | Select-Object -First 1
        if ($resumedLine) { Write-Host ('Активность: ' + $resumedLine) }

        $wakeLine = @((Get-Lines (Sh 'dumpsys power')) | Where-Object { $_ -match 'mWakefulness=' }) | Select-Object -First 1
        if ($wakeLine) { Write-Host ('Экран:      ' + $wakeLine) }

        $memLine = @((Get-Lines (Sh ('dumpsys meminfo ' + $PackageName))) | Where-Object { $_ -match 'TOTAL' }) | Select-Object -First 1
        if ($memLine) { Write-Host ('Память:     ' + $memLine) }

        $logLines = @((Get-Lines (Sh 'logcat -d -v brief')) | Where-Object { $_ -match 'FATAL EXCEPTION' })
        Write-Host ('FATAL:      ' + $logLines.Count)

        $packageDump = Sh ('dumpsys package ' + $PackageName)
        $versionLine = @((Get-Lines $packageDump) | Where-Object { $_ -match 'versionName' }) | Select-Object -First 1
        if ($versionLine) { Write-Host ('Версия:     ' + $versionLine) }

        $model = Sh 'getprop ro.product.model'
        if ($model) { Write-Host ('Модель:     ' + $model) }
    }
}
