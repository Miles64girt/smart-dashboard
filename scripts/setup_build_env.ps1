<#
.SYNOPSIS
  Установка окружения сборки Smart Dashboard: JDK 17 + Android build-tools 34 + platform API 24.

.DESCRIPTION
  Скачивает официальные архивы (Adoptium и dl.google.com) и распаковывает их в C:\AndroidBuild:
    jdk17\      JDK 17
    buildtools\ Android build-tools 34 (aapt2, d8, zipalign, apksigner)
    platform24\ android.jar (API 24)

  Нужен доступ в интернет, объём около 350 МБ. Повторный запуск безопасен:
  уже скачанные архивы и распакованные каталоги пропускаются.

  После установки собирайте APK: powershell -ExecutionPolicy Bypass -File ..\build\build.ps1

.PARAMETER Base
  Каталог для инструментов. По умолчанию C:\AndroidBuild.
  Если меняете его, передайте тот же путь в build.ps1: -BuildRoot D:\AndroidBuild

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\setup_build_env.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\setup_build_env.ps1 -Base D:\AndroidBuild
#>
[CmdletBinding()]
param(
    [string]$Base = 'C:\AndroidBuild'
)

$ErrorActionPreference = 'Continue'
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}
$ProgressPreference = 'SilentlyContinue'

function Step([string]$Message) {
    Write-Host ''
    Write-Host ('==> ' + $Message) -ForegroundColor Cyan
}

function Ok([string]$Message) {
    Write-Host ('[OK]   ' + $Message) -ForegroundColor Green
}

function Warn([string]$Message) {
    Write-Host ('[!]    ' + $Message) -ForegroundColor Yellow
}

function Find-First {
    param([string]$Root, [string]$Pattern, [string]$MustMatch = '')
    if (-not (Test-Path -LiteralPath $Root)) { return $null }
    $items = @(Get-ChildItem -Path $Root -Recurse -Filter $Pattern -ErrorAction SilentlyContinue)
    if ($MustMatch) { $items = @($items | Where-Object { $_.FullName -match $MustMatch }) }
    if ($items.Count -eq 0) { return $null }
    return $items[0]
}

$BasePath = [System.IO.Path]::GetFullPath($Base)
$DownloadDir = Join-Path $env:TEMP 'smart-dashboard-build-dl'

Write-Host '=== Smart Dashboard: установка окружения сборки ===' -ForegroundColor Cyan
Write-Host ('Каталог инструментов: ' + $BasePath)
Write-Host ('Временные загрузки:    ' + $DownloadDir)

try {
    New-Item -ItemType Directory -Path $BasePath -Force | Out-Null
    New-Item -ItemType Directory -Path $DownloadDir -Force | Out-Null
} catch {
    Write-Host ('Не удалось создать каталог ' + $BasePath + ': ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host 'Запустите PowerShell от имени администратора или укажите другой каталог: -Base D:\AndroidBuild' -ForegroundColor Yellow
    exit 1
}

$items = @(
    @{
        name = 'jdk17'
        file = 'jdk17.zip'
        url  = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse'
        hint = 'JDK 17 (Adoptium Temurin)'
    },
    @{
        name = 'buildtools'
        file = 'build-tools-34.zip'
        url  = 'https://dl.google.com/android/repository/build-tools_r34-windows.zip'
        hint = 'Android build-tools 34'
    },
    @{
        name = 'platform24'
        file = 'platform-24.zip'
        url  = 'https://dl.google.com/android/repository/platform-24_r02.zip'
        hint = 'Android platform API 24 (android.jar)'
    }
)

$toInstall = @()
foreach ($it in $items) {
    $target = Join-Path $BasePath $it.name
    if (Test-Path -LiteralPath $target) {
        Write-Host ('[=]    ' + $it.hint + ': уже установлен (' + $target + ')')
    } else {
        $toInstall += $it
    }
}

if ($toInstall.Count -gt 0) {
    Step ('Скачивание и распаковка (' + $toInstall.Count + ' компонент(ов), ~350 МБ)')
    foreach ($it in $toInstall) {
        $archive = Join-Path $DownloadDir $it.file
        $target = Join-Path $BasePath $it.name
        $needDownload = $true
        if (Test-Path -LiteralPath $archive) {
            $archiveSize = (Get-Item -LiteralPath $archive).Length
            if ($archiveSize -gt 1MB) {
                $needDownload = $false
                Write-Host ('  Архив уже скачан: ' + $archive + ' (' + [math]::Round($archiveSize / 1MB, 1) + ' МБ)')
            } else {
                Warn ('Архив ' + $archive + ' повреждён (мал по размеру), скачиваю заново.')
            }
        }
        if ($needDownload) {
            Write-Host ('  Скачивание ' + $it.hint + ':')
            Write-Host ('    ' + $it.url)
            try {
                Invoke-WebRequest -Uri $it.url -OutFile $archive -UseBasicParsing -TimeoutSec 1800
            } catch {
                Write-Host ('  Не удалось скачать ' + $it.url) -ForegroundColor Red
                Write-Host ('  ' + $_.Exception.Message) -ForegroundColor Red
                Write-Host '  Проверьте интернет/прокси и запустите скрипт ещё раз (скачанное не пропадёт).' -ForegroundColor Yellow
                exit 1
            }
        }
        if (-not (Test-Path -LiteralPath $archive)) {
            Write-Host ('  Архив не найден после скачивания: ' + $archive) -ForegroundColor Red
            exit 1
        }
        Write-Host ('  Распаковка в ' + $target + ' ...')
        try {
            Expand-Archive -Path $archive -DestinationPath $target -Force
        } catch {
            Write-Host ('  Не удалось распаковать ' + $archive + ': ' + $_.Exception.Message) -ForegroundColor Red
            Write-Host ('  Удалите неполный каталог ' + $target + ' и повторите запуск.') -ForegroundColor Yellow
            exit 1
        }
        Ok ($it.hint + ' установлен: ' + $target)
    }
}

Step 'Проверка установленных инструментов'
$checks = [ordered]@{
    'java'        = Find-First -Root (Join-Path $BasePath 'jdk17') -Pattern 'java.exe' -MustMatch '\\bin\\java\.exe$'
    'javac'       = Find-First -Root (Join-Path $BasePath 'jdk17') -Pattern 'javac.exe' -MustMatch '\\bin\\javac\.exe$'
    'keytool'     = Find-First -Root (Join-Path $BasePath 'jdk17') -Pattern 'keytool.exe' -MustMatch '\\bin\\keytool\.exe$'
    'aapt2'       = Find-First -Root (Join-Path $BasePath 'buildtools') -Pattern 'aapt2.exe'
    'd8'          = Find-First -Root (Join-Path $BasePath 'buildtools') -Pattern 'd8.bat'
    'zipalign'    = Find-First -Root (Join-Path $BasePath 'buildtools') -Pattern 'zipalign.exe'
    'apksigner'   = Find-First -Root (Join-Path $BasePath 'buildtools') -Pattern 'apksigner.bat'
    'android.jar' = Find-First -Root (Join-Path $BasePath 'platform24') -Pattern 'android.jar'
}

$failed = @()
foreach ($name in $checks.Keys) {
    $found = $checks[$name]
    if ($found) {
        Ok ('{0}: {1}' -f $name, $found.FullName)
    } else {
        Write-Host ('[FAIL] ' + $name + ' не найден') -ForegroundColor Red
        $failed += $name
    }
}

$javaExe = $checks['java']
if ($javaExe) {
    Write-Host ''
    Write-Host 'Версия Java:'
    & $javaExe.FullName -version 2>&1 | ForEach-Object { Write-Host ('  ' + $_) }
}

if ($failed.Count -gt 0) {
    Write-Host ''
    Write-Host ('Не всё установилось: ' + ($failed -join ', ')) -ForegroundColor Red
    Write-Host ('Удалите каталог ' + $BasePath + ' и запустите скрипт заново.') -ForegroundColor Yellow
    exit 1
}

Write-Host ''
Write-Host 'Готово. Теперь можно собирать APK:' -ForegroundColor Cyan
Write-Host '  powershell -ExecutionPolicy Bypass -File ..\build\build.ps1' -ForegroundColor White
Write-Host 'или из корня репозитория:' -ForegroundColor Cyan
Write-Host '  powershell -ExecutionPolicy Bypass -File .\build\build.ps1' -ForegroundColor White
