<#
.SYNOPSIS
  Сборка APK Smart Dashboard без Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.

.DESCRIPTION
  Собирает apk\SmartDashboard-1.3.apk из исходников app\ без Android Studio и Gradle.

  Нужные инструменты (по умолчанию в C:\AndroidBuild, поиск рекурсивный):
    jdk17\      JDK 17 (java, javac, keytool)
    buildtools\ Android build-tools (aapt2, d8, zipalign, apksigner)
    platform24\ android.jar (API 24)
  Установить окружение: powershell -ExecutionPolicy Bypass -File scripts\setup_build_env.ps1

  aapt2 не работает с кириллицей в путях, а путь к репозиторию может её содержать.
  Поэтому все временные файлы сборки лежат в отдельном каталоге:
    <StageRoot>\work, по умолчанию %TEMP%\smart-dashboard-build.
  Каталог BuildRoot (с инструментами) используется только для чтения.
  Если TEMP содержит кириллицу, укажите ASCII-путь вручную: -StageRoot C:\temp\sd-build.

  Ключ подписи создаётся при первой сборке автоматически:
    build\keystore.jks и build\keystore.properties (пароль генерируется случайно).
  Эти файлы не коммитятся. Сохраните их: без них новая сборка получит другую подпись,
  и APK не установится поверх уже установленной версии.

.PARAMETER ProjRoot
  Корень проекта (каталог, в котором лежат app\, build\, apk\).
  По умолчанию — родительский каталог каталога build\.

.PARAMETER BuildRoot
  Каталог с инструментами сборки. По умолчанию C:\AndroidBuild. Может быть только для чтения.

.PARAMETER StageRoot
  Каталог для временных файлов сборки. Должен быть ASCII-путём.
  По умолчанию %TEMP%\smart-dashboard-build.

.PARAMETER Adb
  Путь к adb для -Install. По умолчанию пусто: adb ищется в PATH,
  затем в C:\platform-tools\adb.exe.

.PARAMETER Install
  После сборки установить APK на подключённое устройство (adb install -r).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\build\build.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\build\build.ps1 -Install

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\build\build.ps1 -StageRoot C:\temp\sd-build
#>
[CmdletBinding()]
param(
    [string]$ProjRoot = '',
    [string]$BuildRoot = 'C:\AndroidBuild',
    [string]$StageRoot = '',
    [string]$Adb = '',
    [switch]$Install
)

$ErrorActionPreference = 'Continue'
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

# В PowerShell 5.1 значение по умолчанию параметра advanced-скрипта не видит $PSScriptRoot,
# поэтому корень проекта вычисляется здесь.
if ([string]::IsNullOrWhiteSpace($ProjRoot)) {
    if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
        throw 'Не удалось определить каталог скрипта. Укажите корень проекта явно: -ProjRoot <путь>.'
    }
    $ProjRoot = Split-Path $PSScriptRoot -Parent
}

$AppVersionName = '1.3'
$AppVersionCode = 4
$FinalApkName = 'SmartDashboard-' + $AppVersionName + '.apk'
$MinSdk = 24
$TargetSdk = 24

$ProjectPath = [System.IO.Path]::GetFullPath($ProjRoot)
$AppDir = Join-Path $ProjectPath 'app'
$ApkDir = Join-Path $ProjectPath 'apk'
$LogDir = Join-Path $ProjectPath 'logs'
$LogFile = Join-Path $LogDir 'build.log'
$BuildDir = Join-Path $ProjectPath 'build'
$KeystorePath = Join-Path $BuildDir 'keystore.jks'
$KeystorePropsPath = Join-Path $BuildDir 'keystore.properties'

# --- журнал: logs\build.log (каталог logs\ в .gitignore) ---
$script:LogWriter = $null
try {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
    $script:LogWriter = New-Object System.IO.StreamWriter($LogFile, $false, (New-Object System.Text.UTF8Encoding($false)))
    $script:LogWriter.AutoFlush = $true
} catch {
    Write-Warning ('Не удалось открыть журнал ' + $LogFile + ': ' + $_.Exception.Message)
}

function Log {
    param([string]$Message)
    $line = '[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss'), $Message
    if ($script:LogWriter) { $script:LogWriter.WriteLine($line) }
    Write-Host $line
}

function Log-Error {
    param([string]$Message)
    $line = '[{0}] ОШИБКА: {1}' -f (Get-Date -Format 'HH:mm:ss'), $Message
    if ($script:LogWriter) { $script:LogWriter.WriteLine($line) }
    Write-Host $line -ForegroundColor Red
}

function Log-FileOnly {
    param([string]$Text)
    if ($script:LogWriter) { $script:LogWriter.WriteLine($Text) }
}

function Find-Tool {
    param([string]$Root, [string]$Pattern, [string]$MustMatch = '')
    if (-not (Test-Path -LiteralPath $Root)) { return $null }
    $items = @(Get-ChildItem -Path $Root -Recurse -Filter $Pattern -ErrorAction SilentlyContinue)
    if ($MustMatch) { $items = @($items | Where-Object { $_.FullName -match $MustMatch }) }
    if ($items.Count -eq 0) { return $null }
    return $items[0].FullName
}

function Test-AsciiPath {
    param([string]$Path)
    return -not ($Path -match '[^\x20-\x7E]')
}

function Assert-ChildPath {
    param([string]$Path, [string]$Parent)
    $parentFull = [System.IO.Path]::GetFullPath($Parent).TrimEnd('\')
    $pathFull = [System.IO.Path]::GetFullPath($Path).TrimEnd('\')
    $prefix = $parentFull + '\'
    if (-not $pathFull.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw ('Небезопасный путь для очистки: ' + $pathFull + ' (должен находиться внутри ' + $parentFull + ')')
    }
    return $pathFull
}

function New-RandomPassword {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        if ($rng) { $rng.Dispose() }
    }
    $sb = New-Object System.Text.StringBuilder
    foreach ($b in $bytes) {
        [void]$sb.Append($b.ToString('x2'))
    }
    return $sb.ToString()
}

function Read-PropertiesFile {
    param([string]$Path)
    $result = @{}
    foreach ($line in (Get-Content -LiteralPath $Path -Encoding UTF8)) {
        $trimmed = ([string]$line).Trim()
        if ($trimmed.Length -eq 0) { continue }
        if ($trimmed.StartsWith('#') -or $trimmed.StartsWith(';')) { continue }
        $eq = $trimmed.IndexOf('=')
        if ($eq -lt 1) { continue }
        $key = $trimmed.Substring(0, $eq).Trim()
        $value = $trimmed.Substring($eq + 1).Trim()
        if ($key.Length -gt 0) { $result[$key] = $value }
    }
    return $result
}

function Write-KeystoreProperties {
    param(
        [string]$Path,
        [string]$StorePassword,
        [string]$KeyPassword,
        [string]$AliasName
    )
    [string[]]$lines = @(
        '# Локальный ключ подписи Smart Dashboard.',
        '# Файл создан автоматически при первой сборке. НЕ КОММИТИТЬ в git!',
        '# Сохраните этот файл вместе с build\keystore.jks: без них не установить обновление поверх.',
        ('storePassword=' + $StorePassword),
        ('keyPassword=' + $KeyPassword),
        ('alias=' + $AliasName)
    )
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($Path, $lines, $utf8NoBom)
}

function Ensure-Keystore {
    param([string]$KeytoolPath)

    $hasKeystore = Test-Path -LiteralPath $KeystorePath
    $hasProps = Test-Path -LiteralPath $KeystorePropsPath
    $info = @{
        storePassword = $null
        keyPassword = $null
        alias = 'smartdashboard'
        created = $false
    }

    if ($hasProps) {
        $props = Read-PropertiesFile -Path $KeystorePropsPath
        foreach ($required in @('storePassword', 'keyPassword', 'alias')) {
            if (-not $props.ContainsKey($required) -or [string]::IsNullOrWhiteSpace([string]$props[$required])) {
                throw ('В файле ' + $KeystorePropsPath + ' нет обязательного параметра "' + $required + '". Ожидаются строки: storePassword=..., keyPassword=..., alias=...')
            }
        }
        $info.storePassword = [string]$props['storePassword']
        $info.keyPassword = [string]$props['keyPassword']
        $info.alias = [string]$props['alias']
    }

    if ($hasKeystore -and -not $hasProps) {
        throw ('Найден ключ подписи ' + $KeystorePath + ', но нет файла ' + $KeystorePropsPath + ' с паролем. Подписать APK нечем. Восстановите keystore.properties из резервной копии либо удалите keystore.jks — тогда при следующей сборке будет создан новый ключ (подпись изменится).')
    }

    if ($hasKeystore) {
        Log ('Ключ подписи: ' + $KeystorePath + ' (alias: ' + $info.alias + ', не коммитится)')
        return $info
    }

    if ($hasProps) {
        Log 'Ключ подписи не найден, но keystore.properties есть: создаю новый ключ с сохранённым паролем.'
        Log 'ВНИМАНИЕ: подпись нового ключа отличается от прежней.'
    } else {
        $info.storePassword = New-RandomPassword
        $info.keyPassword = $info.storePassword
        $info.alias = 'smartdashboard'
        Log 'Ключ подписи не найден: создаю новый (RSA 2048, срок 10000 дней, случайный пароль).'
    }

    & $KeytoolPath -genkeypair -keystore $KeystorePath -alias $info.alias -keyalg RSA -keysize 2048 -validity 10000 -storepass $info.storePassword -keypass $info.keyPassword -dname 'CN=Smart Dashboard, O=Smart Dashboard, C=RU' -storetype PKCS12
    if ($LASTEXITCODE -ne 0) {
        throw ('keytool: не удалось создать ключ подписи (код ' + $LASTEXITCODE + ').')
    }
    if (-not (Test-Path -LiteralPath $KeystorePath)) {
        throw ('keytool завершился без ошибки, но файл ключа не создан: ' + $KeystorePath)
    }

    if (-not $hasProps) {
        Write-KeystoreProperties -Path $KeystorePropsPath -StorePassword $info.storePassword -KeyPassword $info.keyPassword -AliasName $info.alias
        Log ('Пароль сохранён в ' + $KeystorePropsPath)
    }

    $info.created = $true
    return $info
}

function Show-KeystoreWarning {
    Log 'ВАЖНО: build\keystore.jks и build\keystore.properties — ваши локальные секреты.'
    Log '  Не коммитьте их (они уже в .gitignore) и обязательно сохраните в надёжном месте.'
    Log '  Без них новая сборка получит другую подпись, и APK не установится поверх старого.'
}

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

$script:BuildOk = $false

try {
    Log '============================================================'
    Log ('Smart Dashboard ' + $AppVersionName + ' (versionCode ' + $AppVersionCode + '): сборка APK')
    Log ('Проект:    ' + $ProjectPath)
    Log ('BuildRoot: ' + $BuildRoot)
    Log ('PowerShell: ' + $PSVersionTable.PSVersion)
    if ($env:USERNAME) { Log ('Пользователь: ' + $env:USERNAME) }

    if (-not (Test-Path -LiteralPath $AppDir)) {
        throw ('Не найден каталог исходников: ' + $AppDir + '. Проверьте -ProjRoot.')
    }
    foreach ($required in @('AndroidManifest.xml', 'src', 'res')) {
        if (-not (Test-Path -LiteralPath (Join-Path $AppDir $required))) {
            throw ('В ' + $AppDir + ' нет ' + $required + '. Репозиторий неполный.')
        }
    }

    # --- staging: только ASCII-путь, вне репозитория и вне BuildRoot ---
    if ([string]::IsNullOrWhiteSpace($StageRoot)) {
        if ([string]::IsNullOrWhiteSpace($env:TEMP)) {
            throw 'Переменная окружения TEMP не задана. Укажите -StageRoot с ASCII-путём.'
        }
        $StageRoot = Join-Path $env:TEMP 'smart-dashboard-build'
    }
    $StageRootPath = [System.IO.Path]::GetFullPath($StageRoot)
    if (-not (Test-AsciiPath $StageRootPath)) {
        throw ('StageRoot содержит не-ASCII символы: ' + $StageRootPath + '. aapt2 не работает с кириллицей в путях. Укажите ASCII-путь: -StageRoot C:\temp\sd-build')
    }
    $projectPrefix = $ProjectPath.TrimEnd('\') + '\'
    if (($StageRootPath.TrimEnd('\') + '\').StartsWith($projectPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw ('StageRoot не должен находиться внутри проекта: ' + $StageRootPath + '. Укажите каталог вне репозитория, например %TEMP%\smart-dashboard-build.')
    }
    Log ('Staging:   ' + $StageRootPath)

    # --- инструменты ---
    $JdkRoot = Join-Path $BuildRoot 'jdk17'
    $BuildToolsRoot = Join-Path $BuildRoot 'buildtools'
    $PlatformRoot = Join-Path $BuildRoot 'platform24'

    $Java = Find-Tool -Root $JdkRoot -Pattern 'java.exe' -MustMatch '\\bin\\java\.exe$'
    $Javac = Find-Tool -Root $JdkRoot -Pattern 'javac.exe' -MustMatch '\\bin\\javac\.exe$'
    $Keytool = Find-Tool -Root $JdkRoot -Pattern 'keytool.exe' -MustMatch '\\bin\\keytool\.exe$'
    $Aapt2 = Find-Tool -Root $BuildToolsRoot -Pattern 'aapt2.exe'
    $D8 = Find-Tool -Root $BuildToolsRoot -Pattern 'd8.bat'
    $Zipalign = Find-Tool -Root $BuildToolsRoot -Pattern 'zipalign.exe'
    $ApkSigner = Find-Tool -Root $BuildToolsRoot -Pattern 'apksigner.bat'
    $AndroidJar = Find-Tool -Root $PlatformRoot -Pattern 'android.jar'

    $tools = [ordered]@{
        'java (JDK 17)' = $Java
        'javac' = $Javac
        'keytool' = $Keytool
        'aapt2' = $Aapt2
        'd8' = $D8
        'zipalign' = $Zipalign
        'apksigner' = $ApkSigner
        'android.jar (API 24)' = $AndroidJar
    }
    $missing = @()
    foreach ($toolName in $tools.Keys) {
        $toolValue = $tools[$toolName]
        if ([string]::IsNullOrEmpty($toolValue)) {
            $missing += $toolName
        } else {
            Log ('{0}: {1}' -f $toolName, $toolValue)
        }
    }
    if ($missing.Count -gt 0) {
        $setupScript = Join-Path $ProjectPath 'scripts\setup_build_env.ps1'
        throw ('Не найдены инструменты: ' + ($missing -join ', ') + '. Проверьте, что в ' + $BuildRoot + ' есть каталоги jdk17\, buildtools\ и platform24\. Установить окружение: powershell -ExecutionPolicy Bypass -File "' + $setupScript + '"')
    }

    $JdkBin = Split-Path -Parent $Javac
    $env:JAVA_HOME = Split-Path -Parent $JdkBin
    $env:PATH = $JdkBin + ';' + $env:PATH
    Log ('JAVA_HOME: ' + $env:JAVA_HOME)

    # --- очистка и наполнение staging ---
    $WorkDir = Join-Path $StageRootPath 'work'
    $WorkDir = Assert-ChildPath -Path $WorkDir -Parent $StageRootPath
    if (Test-Path -LiteralPath $WorkDir) {
        Log ('Очистка временного каталога: ' + $WorkDir)
        Remove-Item -LiteralPath $WorkDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null

    $StageApp = Join-Path $WorkDir 'app'
    Log 'Копирование app\ во временный ASCII-каталог...'
    Copy-Item -Path $AppDir -Destination $StageApp -Recurse -Force
    if (-not (Test-Path -LiteralPath (Join-Path $StageApp 'AndroidManifest.xml'))) {
        throw 'Копирование app\ в staging не удалось: манифест не найден.'
    }

    $SrcDir = Join-Path $StageApp 'src'
    $ResDir = Join-Path $StageApp 'res'
    $Manifest = Join-Path $StageApp 'AndroidManifest.xml'
    $GenDir = Join-Path $WorkDir 'gen'
    $ClassesDir = Join-Path $WorkDir 'classes'
    $DexDir = Join-Path $WorkDir 'dex'
    $StageOutDir = Join-Path $WorkDir 'out'
    $ResZip = Join-Path $WorkDir 'resources.zip'
    $UnsignedApk = Join-Path $WorkDir 'app-unsigned.apk'
    $AlignedApk = Join-Path $WorkDir 'app-aligned.apk'
    $FinalStage = Join-Path $StageOutDir $FinalApkName

    foreach ($dir in @($GenDir, $ClassesDir, $DexDir, $StageOutDir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    New-Item -ItemType Directory -Path $ApkDir -Force | Out-Null

    # --- aapt2 compile ---
    Log '--- aapt2: компиляция ресурсов ---'
    & $Aapt2 compile --dir $ResDir -o $ResZip
    if ($LASTEXITCODE -ne 0) { throw ('aapt2 compile: код ошибки ' + $LASTEXITCODE) }
    if (-not (Test-Path -LiteralPath $ResZip)) { throw 'aapt2 compile: resources.zip не создан.' }

    # --- aapt2 link ---
    Log '--- aapt2: линковка ресурсов и манифеста ---'
    & $Aapt2 link -o $UnsignedApk -I $AndroidJar --manifest $Manifest -R $ResZip --java $GenDir --auto-add-overlay --min-sdk-version $MinSdk --target-sdk-version $TargetSdk --version-code $AppVersionCode --version-name $AppVersionName
    if ($LASTEXITCODE -ne 0) { throw ('aapt2 link: код ошибки ' + $LASTEXITCODE) }
    if (-not (Test-Path -LiteralPath $UnsignedApk)) { throw 'aapt2 link: APK не создан.' }

    # --- javac ---
    $sources = @()
    $sources += @(Get-ChildItem -Path $SrcDir -Recurse -Filter '*.java' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    $sources += @(Get-ChildItem -Path $GenDir -Recurse -Filter '*.java' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    if ($sources.Count -eq 0) { throw ('Не найдено ни одного .java-файла в ' + $SrcDir) }
    Log ('--- javac: компиляция ' + $sources.Count + ' файлов (source/target 8, encoding UTF-8) ---')
    & $Javac -encoding UTF-8 -source 8 -target 8 -nowarn -classpath $AndroidJar -d $ClassesDir $sources
    if ($LASTEXITCODE -ne 0) { throw ('javac: код ошибки ' + $LASTEXITCODE) }

    # --- d8 ---
    $classFiles = @(Get-ChildItem -Path $ClassesDir -Recurse -Filter '*.class' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    if ($classFiles.Count -eq 0) { throw 'javac не создал ни одного .class-файла.' }
    $ArgFile = Join-Path $WorkDir 'classes.txt'
    [System.IO.File]::WriteAllLines($ArgFile, [string[]]$classFiles, (New-Object System.Text.UTF8Encoding($false)))
    Log ('--- d8: dex (min-api ' + $MinSdk + ', ' + $classFiles.Count + ' классов) ---')
    & $D8 --release --min-api $MinSdk --lib $AndroidJar --output $DexDir ('@' + $ArgFile)
    if ($LASTEXITCODE -ne 0) { throw ('d8: код ошибки ' + $LASTEXITCODE) }
    $DexFile = Join-Path $DexDir 'classes.dex'
    if (-not (Test-Path -LiteralPath $DexFile)) { throw 'd8: classes.dex не создан.' }

    # --- упаковка classes.dex в APK ---
    Log '--- Упаковка classes.dex в APK ---'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.IO.Compression
    $zip = $null
    try {
        $zip = [System.IO.Compression.ZipFile]::Open($UnsignedApk, [System.IO.Compression.ZipArchiveMode]::Update)
        $existing = $zip.GetEntry('classes.dex')
        if ($existing) { $existing.Delete() }
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $DexFile, 'classes.dex', [System.IO.Compression.CompressionLevel]::Optimal)
    } finally {
        if ($zip) { $zip.Dispose() }
    }

    # --- zipalign ---
    Log '--- zipalign ---'
    & $Zipalign -f 4 $UnsignedApk $AlignedApk
    if ($LASTEXITCODE -ne 0) { throw ('zipalign: код ошибки ' + $LASTEXITCODE) }

    # --- ключ подписи ---
    Log '--- Ключ подписи ---'
    $keystoreInfo = Ensure-Keystore -KeytoolPath $Keytool
    if ($keystoreInfo.created) { Show-KeystoreWarning }

    # --- подпись ---
    Log '--- apksigner: подпись ---'
    & $ApkSigner sign --ks $KeystorePath --ks-key-alias $keystoreInfo.alias --ks-pass ('pass:' + $keystoreInfo.storePassword) --key-pass ('pass:' + $keystoreInfo.keyPassword) --out $FinalStage $AlignedApk
    if ($LASTEXITCODE -ne 0) { throw ('apksigner sign: код ошибки ' + $LASTEXITCODE) }

    # --- проверка подписи ---
    Log '--- apksigner: проверка подписи ---'
    & $ApkSigner verify --print-certs $FinalStage
    if ($LASTEXITCODE -ne 0) { throw 'apksigner verify: подпись не прошла проверку.' }

    # --- готовый APK в репозиторий ---
    $FinalApk = Join-Path $ApkDir $FinalApkName
    Copy-Item -LiteralPath $FinalStage -Destination $FinalApk -Force
    $finalSizeKb = [math]::Round(((Get-Item -LiteralPath $FinalApk).Length / 1KB), 1)
    $finalHash = (Get-FileHash -LiteralPath $FinalApk -Algorithm SHA256).Hash
    Log ('ГОТОВО: ' + $FinalApk)
    Log ('Размер: ' + $finalSizeKb + ' КБ; SHA-256: ' + $finalHash)

    # --- установка на устройство (необязательно) ---
    if ($Install) {
        $adbPath = Resolve-AdbPath -ExplicitPath $Adb
        if (-not $adbPath) {
            throw 'adb не найден. Установите Android Platform Tools, добавьте adb в PATH или укажите -Adb "C:\platform-tools\adb.exe".'
        }
        Log ('--- adb install -r (' + $adbPath + ') ---')
        & $adbPath install -r $FinalApk
        if ($LASTEXITCODE -ne 0) {
            throw ('adb install завершился с ошибкой (код ' + $LASTEXITCODE + '). Если APK подписан другим ключом, удалите старую версию: scripts\uninstall.ps1')
        }
        Log 'APK установлен на подключённое устройство.'
    } else {
        Log ('Установка пропущена. Установить вручную: adb install -r apk\' + $FinalApkName + ' или запустить scripts\install_deploy.ps1.')
    }

    Log '===== BUILD OK ====='
    $script:BuildOk = $true
} catch {
    Log-Error $_.Exception.Message
    Log-FileOnly ''
    Log-FileOnly 'Стек вызовов:'
    Log-FileOnly ([string]$_.ScriptStackTrace)
    Log ('Журнал сборки: ' + $LogFile)
} finally {
    if ($script:LogWriter) {
        try { $script:LogWriter.Flush() } catch { }
        try { $script:LogWriter.Dispose() } catch { }
    }
}

if (-not $script:BuildOk) {
    exit 1
}
