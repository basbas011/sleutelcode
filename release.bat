@echo off
rem Rolt een nieuwe versie uit: commit, tag en push. GitHub Actions bouwt daarna
rem de APK en zet hem in een Release; de app vindt hem via "Zoek naar updates".
rem Gebruik:  release.bat 1.0.1
setlocal
cd /d "%~dp0"

if "%1"=="" (
  echo Gebruik: release.bat ^<versie^>   bijvoorbeeld: release.bat 1.0.1
  exit /b 1
)

git add -A
git commit -m "Release %1"
git tag v%1
if errorlevel 1 exit /b 1
git push
git push origin v%1
echo.
echo Tag v%1 gepusht. Voortgang: Actions-tab van de repo op GitHub.
