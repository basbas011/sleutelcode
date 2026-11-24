@echo off
rem Zet dit project eenmalig in de (lege) GitHub-repo basbas011/sleutelcode.
setlocal
cd /d "%~dp0"
if exist .git (
  echo Deze map is al een git-repo. Gebruik release.bat ^<versie^>.
  exit /b 1
)
git init -b main
git add -A
git commit -m "Sleutelcode: eerste versie"
git remote add origin https://github.com/basbas011/sleutelcode.git
git push -u origin main
if errorlevel 1 (
  echo.
  echo Pushen mislukt. Bestaat de repo basbas011/sleutelcode al op GitHub en is hij leeg?
  exit /b 1
)
echo.
echo Klaar. Draai nu: release.bat 1.0.0
