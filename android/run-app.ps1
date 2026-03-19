# Android App Auto-Launch Script
# Build, install and launch PhotoFrame app

$ErrorActionPreference = "Stop"
$APP_PACKAGE = "com.photoframe"
$MAIN_ACTIVITY = "$APP_PACKAGE/.BindActivity"
$AVD_NAME = "PhotoFrame_Pixel5"
$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "==================================" -ForegroundColor Cyan
Write-Host "  PhotoFrame Android Launch Script" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host ""

# ========== Step 1: Find Android SDK ==========
Write-Host "[1/6] Finding Android SDK..." -ForegroundColor Yellow

$sdkPath = $null

# Read from local.properties first
$localProps = Join-Path $SCRIPT_DIR "local.properties"
if (Test-Path $localProps) {
    $content = Get-Content $localProps -Raw
    if ($content -match 'sdk\.dir=(.+)') {
        $sdkPath = $Matches[1].Trim() -replace '\\:', ':'
        $sdkPath = $sdkPath -replace '\\\\', '\'
    }
}

# Fallback: env vars and common paths
if (-not $sdkPath -or -not (Test-Path $sdkPath)) {
    $candidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
        "C:\Android\Sdk"
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) {
            $sdkPath = $c
            break
        }
    }
}

# Fallback: derive from adb location
if (-not $sdkPath -or -not (Test-Path $sdkPath)) {
    try {
        $adbPath = (Get-Command adb -ErrorAction Stop).Source
        $sdkPath = Split-Path (Split-Path $adbPath -Parent) -Parent
    } catch {}
}

if (-not $sdkPath -or -not (Test-Path $sdkPath)) {
    Write-Host "[FAIL] Android SDK not found" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Android SDK: $sdkPath" -ForegroundColor Green

# Tool paths
$emulatorExe   = Join-Path $sdkPath "emulator\emulator.exe"
$avdmanagerBat = Join-Path $sdkPath "cmdline-tools\latest\bin\avdmanager.bat"
$adbExe        = Join-Path $sdkPath "platform-tools\adb.exe"

if (-not (Test-Path $avdmanagerBat)) {
    $avdmanagerBat = Join-Path $sdkPath "tools\bin\avdmanager.bat"
}

# ========== Step 2: Check ADB ==========
Write-Host ""
Write-Host "[2/6] Checking ADB..." -ForegroundColor Yellow
try {
    $adbVersion = & adb version 2>&1 | Select-Object -First 1
    Write-Host "[OK] $adbVersion" -ForegroundColor Green
} catch {
    Write-Host "[FAIL] ADB not available" -ForegroundColor Red
    exit 1
}

# ========== Step 3: Device / Emulator ==========
Write-Host ""
Write-Host "[3/6] Checking connected devices..." -ForegroundColor Yellow

$devices = adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "offline" }

if ($devices) {
    Write-Host "[OK] Device found:" -ForegroundColor Green
    $devices | ForEach-Object { Write-Host "  $_" -ForegroundColor White }
} else {
    Write-Host "[WARN] No running device found" -ForegroundColor Yellow

    if (-not (Test-Path $emulatorExe)) {
        Write-Host "[FAIL] emulator.exe not found: $emulatorExe" -ForegroundColor Red
        Write-Host "  Please install Android Emulator via Android Studio SDK Manager" -ForegroundColor Red
        exit 1
    }

    # List existing AVDs
    Write-Host ""
    Write-Host "Scanning available AVDs..." -ForegroundColor Yellow
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $avdListRaw = & $emulatorExe -list-avds 2>&1
    $ErrorActionPreference = $prevEAP
    $avdList = @($avdListRaw | Where-Object {
        $line = $_.ToString().Trim()
        $line -ne "" -and $line -notmatch '^(INFO|WARNING|ERROR)'
    })

    if ($avdList.Count -eq 0) {
        Write-Host "[WARN] No AVD found, creating one automatically..." -ForegroundColor Yellow

        if (-not (Test-Path $avdmanagerBat)) {
            Write-Host "[FAIL] avdmanager not found: $avdmanagerBat" -ForegroundColor Red
            Write-Host "  Install 'Android SDK Command-line Tools' in SDK Manager" -ForegroundColor Red
            exit 1
        }

        # Find an installed system image (prefer google_apis x86_64)
        $sysImgRoot = Join-Path $sdkPath "system-images"
        $foundImage = $null

        if (Test-Path $sysImgRoot) {
            $apiDirs = Get-ChildItem $sysImgRoot -Directory | Sort-Object Name
            foreach ($apiDir in $apiDirs) {
                $tagDirs = Get-ChildItem $apiDir.FullName -Directory
                foreach ($tagDir in $tagDirs) {
                    $archDir = Join-Path $tagDir.FullName "x86_64"
                    if (Test-Path $archDir) {
                        $foundImage = "system-images;$($apiDir.Name);$($tagDir.Name);x86_64"
                        if ($tagDir.Name -eq "google_apis") {
                            break
                        }
                    }
                }
                if ($foundImage -and $foundImage -match "google_apis;") {
                    break
                }
            }
        }

        if (-not $foundImage) {
            Write-Host "[FAIL] No system image installed" -ForegroundColor Red
            Write-Host "  Download one in Android Studio SDK Manager" -ForegroundColor Red
            Write-Host "  Recommended: Android 11 (API 30) Google APIs x86_64" -ForegroundColor Yellow
            exit 1
        }

        Write-Host "  System image: $foundImage" -ForegroundColor Cyan
        Write-Host "  Creating AVD: $AVD_NAME ..." -ForegroundColor Yellow

        $prevEAP = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $createResult = echo "no" | & $avdmanagerBat create avd -n $AVD_NAME -k $foundImage -d "pixel_5" --force 2>&1
        $createExitCode = $LASTEXITCODE
        $ErrorActionPreference = $prevEAP
        if ($createExitCode -ne 0) {
            Write-Host "[FAIL] AVD creation failed:" -ForegroundColor Red
            Write-Host ($createResult | Out-String) -ForegroundColor Red
            exit 1
        }

        Write-Host "[OK] AVD created: $AVD_NAME" -ForegroundColor Green
        $avdList = @($AVD_NAME)
    }

    # Launch emulator
    $selectedAVD = $avdList[0].ToString().Trim()

    Write-Host ""
    Write-Host "Launching emulator: $selectedAVD" -ForegroundColor Yellow
    Write-Host "  This may take 1-2 minutes on first launch..." -ForegroundColor Gray

    Start-Process -FilePath $emulatorExe -ArgumentList "-avd", $selectedAVD -WindowStyle Normal

    # Wait for boot
    $timeout = 180
    $elapsed = 0
    $bootCompleted = $false

    while ($elapsed -lt $timeout) {
        Start-Sleep -Seconds 5
        $elapsed += 5

        $devCheck = adb devices 2>$null | Select-Object -Skip 1 | Where-Object { $_ -match "\bdevice\b" }
        if ($devCheck) {
            $bootProp = adb shell getprop sys.boot_completed 2>$null
            if ($bootProp -and $bootProp.ToString().Trim() -eq "1") {
                $bootCompleted = $true
                break
            }
        }

        if ($elapsed % 15 -eq 0) {
            Write-Host "  Waiting... ${elapsed}s / ${timeout}s" -ForegroundColor Gray
        }
    }

    if ($bootCompleted) {
        Write-Host "[OK] Emulator boot completed" -ForegroundColor Green
    } else {
        Write-Host "[WARN] Boot timeout (${timeout}s), continuing anyway..." -ForegroundColor Yellow
    }
}

# ========== Step 4: Build ==========
Write-Host ""
Write-Host "[4/6] Building app (Debug)..." -ForegroundColor Yellow

Push-Location $SCRIPT_DIR
$buildOutput = & .\gradlew.bat assembleDebug 2>&1
$buildOk = $LASTEXITCODE -eq 0
Pop-Location

if (-not $buildOk) {
    Write-Host "[FAIL] Build failed" -ForegroundColor Red
    $buildOutput | ForEach-Object { Write-Host $_ }
    exit 1
}
Write-Host "[OK] Build succeeded" -ForegroundColor Green

# ========== Step 5: Install ==========
Write-Host ""
Write-Host "[5/6] Installing app..." -ForegroundColor Yellow

Push-Location $SCRIPT_DIR
$installOutput = & .\gradlew.bat installDebug 2>&1
$installOk = $LASTEXITCODE -eq 0
Pop-Location

if (-not $installOk) {
    Write-Host "[FAIL] Install failed" -ForegroundColor Red
    $installOutput | ForEach-Object { Write-Host $_ }
    exit 1
}
Write-Host "[OK] Install succeeded" -ForegroundColor Green

# ========== Step 6: Launch ==========
Write-Host ""
Write-Host "[6/6] Launching app..." -ForegroundColor Yellow

adb shell am force-stop $APP_PACKAGE 2>$null
$launchResult = adb shell am start -n $MAIN_ACTIVITY 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] App launched" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Launch failed" -ForegroundColor Red
    Write-Host ($launchResult | Out-String)
    exit 1
}

Write-Host ""
Write-Host "==================================" -ForegroundColor Cyan
Write-Host "  PhotoFrame is now running!" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host ""
