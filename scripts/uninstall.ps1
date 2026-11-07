<#
.SYNOPSIS
  Удаление Smart Dashboard с подключённого Android-планшета через adb.

.DESCRIPTION
  Скрипт не привязан к модели и серийному номеру: adb ищется автоматически
  (параметр -Adb, затем PATH, затем C:\platform-tools\adb.exe).
  Если подключено несколько устройств — укажите нужное через -Serial.

  Что делает:
    1. останавливает приложение (am force-stop);
    2. убирает исключение из Doze;
    3. убирает службу доступности Smart Dashboard из настроек;
    4. удаляет пакет (adb uninstall);
    5. проверяет, что пакет больше не установлен.

  Данные приложения (свой список вещей, настройки) удаляются вместе с пакетом.

.PARAMETER Adb
  Путь к adb. По умолчанию пусто — adb ищется в PATH, затем в C:\platform-tools\adb.exe.

.PARAMETER Serial
  Серийный номер устройства (adb -s). Нужен только если подключено несколько устройств.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\uninstall.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\uninstall.ps1 -Serial ABC123DEF
#>
[CmdletBinding()]
param(
    [string]$Adb = '',
    [string]$Serial = ''
)

$ErrorActionPreference = 'Continue'
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$PackageName = 'ru.home.dashboard'

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

function Get-Lines {
    param([string]$Text)
    return @($Text -split "`r?`n" | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_.Length -gt 0 })
}

Write-Host '=== Smart Dashboard: удаление с устройства ===' -ForegroundColor Cyan

if (-not $script:AdbPath) {
    Write-Host 'adb не найден.' -ForegroundColor Red
    Write-Host 'Установите Android Platform Tools и добавьте adb в PATH, либо укажите путь:' -ForegroundColor Yellow
    Write-Host '  powershell -ExecutionPolicy Bypass -File .\uninstall.ps1 -Adb "C:\platform-tools\adb.exe"' -ForegroundColor White
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

Write-Host ('Устройство: ' + $script:TargetSerial + ' | ' + (Sh 'getprop ro.product.model'))

$installed = @((Get-Lines (Sh 'pm path ' + $PackageName)) | Where-Object { $_ -match '^package:' })
if ($installed.Count -eq 0) {
    Write-Host 'Пакет Smart Dashboard не установлен — удалять нечего.' -ForegroundColor Yellow
} else {
    Write-Host ''
    Write-Host '--- Остановка и очистка настроек ---' -ForegroundColor Cyan
    Sh ('am force-stop ' + $PackageName) | Out-Null
    Sh ('dumpsys deviceidle whitelist -' + $PackageName) | Out-Null
    Write-Host 'Приложение остановлено, исключение из Doze убрано.'

    $accessibility = Sh 'settings get secure enabled_accessibility_services'
    if ($accessibility -match [regex]::Escape($PackageName)) {
        $remaining = @($accessibility -split ':' | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_.Length -gt 0 -and -not ($_ -match [regex]::Escape($PackageName)) })
        if ($remaining.Count -eq 0) {
            Sh 'settings delete secure enabled_accessibility_services' | Out-Null
        } else {
            Sh ('settings put secure enabled_accessibility_services ' + ($remaining -join ':')) | Out-Null
        }
        Write-Host 'Служба доступности Smart Dashboard отключена.'
    }

    Write-Host ''
    Write-Host '--- Удаление пакета ---' -ForegroundColor Cyan
    $uninstallOut = (& $script:AdbPath -s $script:TargetSerial uninstall $PackageName 2>&1 | Out-String)
    $uninstallCode = $LASTEXITCODE
    Write-Host $uninstallOut.Trim()
    if ($uninstallCode -ne 0) {
        Write-Host 'adb uninstall вернул ошибку. Проверьте, не является ли приложение администратором устройства.' -ForegroundColor Yellow
    }
}

# --- проверка ---
Write-Host ''
$check = @((Get-Lines (Sh 'pm path ' + $PackageName)) | Where-Object { $_ -match '^package:' })
if ($check.Count -gt 0) {
    Write-Host 'ВНИМАНИЕ: пакет всё ещё установлен.' -ForegroundColor Red
    Write-Host 'Если удаление запрещено системой, отключите приложение вручную в настройках.' -ForegroundColor Yellow
    exit 1
} else {
    Write-Host 'Пакет удалён. Настройки и список вещей приложения тоже удалены.' -ForegroundColor Green
}
