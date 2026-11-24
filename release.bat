@echo off
rem Rolt een nieuwe versie uit: commit, tag en push naar GitHub.
rem GitHub Actions bouwt daarna de APK en zet hem bij Releases.
rem Dubbelklikken mag: dan vraagt hij om het versienummer.
rem Of vanaf de opdrachtregel:  release.bat 1.0.1
setlocal
cd /d "%~dp0"

where git >nul 2>&1
if errorlevel 1 (
  echo [FOUT] Git is niet gevonden. Installeer Git voor Windows: https://git-scm.com/download/win
  goto :einde
)

if not exist ".git" (
  echo [FOUT] Deze map is nog geen git-repo. Draai eerst eerste-keer.bat.
  goto :einde
)

set "VERSIE=%~1"
if "%VERSIE%"=="" set /p "VERSIE=Welke versie wil je uitrollen (bijv. 1.0.0)? "
if "%VERSIE%"=="" (
  echo [FOUT] Geen versie opgegeven.
  goto :einde
)
rem eventuele v ervoor weghalen
if /i "%VERSIE:~0,1%"=="v" set "VERSIE=%VERSIE:~1%"

git rev-parse -q --verify "refs/tags/v%VERSIE%" >nul 2>&1
if not errorlevel 1 (
  echo [FOUT] Tag v%VERSIE% bestaat al. Kies een hoger versienummer.
  goto :einde
)

echo.
echo === Wijzigingen vastleggen
git add -A
git diff --cached --quiet
if errorlevel 1 (
  git commit -m "Release %VERSIE%"
  if errorlevel 1 (
    echo [FOUT] Commit mislukt. Staat je naam/e-mail ingesteld?
    echo   git config --global user.name "Bas Verbeek"
    echo   git config --global user.email "jouw@email"
    goto :einde
  )
) else (
  echo Geen nieuwe wijzigingen, alleen een nieuwe tag.
)

echo.
echo === Pushen naar GitHub
git push -u origin HEAD
if errorlevel 1 (
  echo [FOUT] Pushen mislukt. Controleer of de repo op GitHub bestaat en of je bent ingelogd.
  goto :einde
)

echo.
echo === Tag v%VERSIE% maken en pushen
git tag "v%VERSIE%"
if errorlevel 1 goto :einde
git push origin "v%VERSIE%"
if errorlevel 1 (
  echo [FOUT] Tag pushen mislukt.
  git tag -d "v%VERSIE%" >nul
  goto :einde
)

echo.
echo Klaar! GitHub bouwt nu de APK (duurt een paar minuten).
for /f "delims=" %%u in ('git remote get-url origin') do set "URL=%%u"
set "URL=%URL:.git=%"
echo Voortgang: %URL%/actions
echo Daarna staat app-%VERSIE%.apk bij: %URL%/releases

:einde
echo.
pause
