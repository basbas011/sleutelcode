@echo off
rem Bouwt een signed release-APK op deze pc. Resultaat: dist\app-<versie>.apk
rem Gebruik:  build-local.bat            (versie 1.0.0)
rem           build-local.bat 1.2.3
setlocal
cd /d "%~dp0"

set VERSION=%1
if "%VERSION%"=="" set VERSION=1.0.0

rem Java: Android Studio levert een JDK mee (JBR)
if "%JAVA_HOME%"=="" (
  if exist "C:\Program Files\Android\Android Studio\jbr" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
)
if "%JAVA_HOME%"=="" (
  echo JAVA_HOME is niet ingesteld en Android Studio JBR is niet gevonden.
  exit /b 1
)

rem Android SDK-locatie
if not exist local.properties (
  if exist "%LOCALAPPDATA%\Android\Sdk" (
    set "SDKPATH=%LOCALAPPDATA%\Android\Sdk"
    setlocal enabledelayedexpansion
    echo sdk.dir=!SDKPATH:\=/!> local.properties
    endlocal
  ) else (
    echo Android SDK niet gevonden. Open het project eenmaal in Android Studio of maak local.properties met sdk.dir=...
    exit /b 1
  )
)

if not exist keystore.properties (
  echo keystore.properties ontbreekt. Zet keystore\release.jks en keystore.properties in deze map ^(zie README^).
  exit /b 1
)

call gradlew.bat assembleRelease -PversionName=%VERSION%
if errorlevel 1 exit /b 1

if not exist dist mkdir dist
copy /y app\build\outputs\apk\release\app-release.apk dist\app-%VERSION%.apk >nul
echo.
echo Klaar: dist\app-%VERSION%.apk
