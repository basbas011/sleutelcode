# Sleutelcode (Android)

Android-app om een sleutel op te meten vanaf een foto: inkepingen (positie en diepte in mm,
of als code), beide kanten, groeven via een foto van de punt, en een 3D-model (OpenSCAD)
om te testen.

Gebouwd op `basbas011/android-app-template`: Kotlin + WebView, updater via GitHub Releases.

## Opbouw

- `app/src/main/assets/www/index.html`: de hele meet-app (HTML/JS). Pas je de app aan, dan pas je dit bestand aan.
- `MainActivity.kt`: WebView, camera/galerij voor foto's, opslaan in `Downloads/Sleutelcode`, klembord en de updater.
- `Updater.kt`: ongewijzigd uit het template.

## Eerste keer (eenmalig)

1. Maak op GitHub een **lege, publieke** repo `basbas011/sleutelcode` (geen README toevoegen).
2. Voeg onder *Settings > Secrets and variables > Actions* de secrets `KEYSTORE_BASE64` en `KEYSTORE_PASSWORD` toe (dezelfde als bij je andere apps).
3. Pak deze map uit op je pc en draai `eerste-keer.bat`. Dat zet de code in de repo.
4. Draai `release.bat 1.0.0`. GitHub Actions bouwt de APK en zet hem bij *Releases*.
5. Download `app-1.0.0.apk` uit de Release op je telefoon en installeer hem.

Daarna: wijzigingen maken en `release.bat 1.0.1`. In de app verschijnt vanzelf de vraag om te updaten
(of tik onderaan op **Zoek naar updates**).

## Lokaal bouwen

`build-local.bat 1.0.0` (zie het template: `keystore\release.jks` en `keystore.properties` nodig).
