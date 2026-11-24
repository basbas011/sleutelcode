package nl.bict.sleutelcode

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import java.io.File

/**
 * Sleutelcode: de meet-app draait als webpagina (assets/www/index.html) in een WebView.
 * De pagina wordt geladen via https://appassets.androidplatform.net zodat het een
 * "veilige" context is (klembord, localStorage). Native bruggen: camera/galerij voor
 * <input type=file>, bestanden opslaan in Downloads, klembord en de GitHub-updater.
 */
class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var web: WebView

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null

    private var pending: Updater.Release? = null
    private var busy = false

    companion object {
        private const val REQ_FILE = 41
        private const val HOST = "appassets.androidplatform.net"
        private const val START_URL = "https://$HOST/assets/www/index.html"
        private val BG = Color.parseColor("#121417")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val outer = FrameLayout(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(BG)
        }
        web = WebView(this).apply { setBackgroundColor(BG) }
        outer.addView(web, FrameLayout.LayoutParams(-1, -1))
        setContentView(outer)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
        }
        web.addJavascriptInterface(Bridge(), "Android")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == HOST) return false
                // Externe links openen in de browser
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                if (params.isCaptureEnabled && openCamera()) return true
                return openGallery()
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(START_URL)

        checkForUpdates(manual = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    // ---------------------------------------------------------------- foto kiezen

    private fun openCamera(): Boolean {
        return try {
            val dir = File(cacheDir, "camera").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File.createTempFile("sleutel_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            cameraUri = uri
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, uri)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(intent, REQ_FILE)
            true
        } catch (e: ActivityNotFoundException) {
            cameraUri = null
            false
        } catch (e: Exception) {
            cameraUri = null
            false
        }
    }

    private fun openGallery(): Boolean {
        cameraUri = null
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        return try {
            startActivityForResult(Intent.createChooser(intent, "Kies een foto"), REQ_FILE)
            true
        } catch (e: ActivityNotFoundException) {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
            toast("Geen app gevonden om een foto te kiezen")
            false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FILE) return
        val cb = fileCallback ?: return
        fileCallback = null
        val result: Array<Uri>? = if (resultCode == RESULT_OK) {
            val cam = cameraUri
            if (cam != null) arrayOf(cam) else data?.data?.let { arrayOf(it) }
        } else null
        cameraUri = null
        cb.onReceiveValue(result)
    }

    // ---------------------------------------------------------------- brug naar de pagina

    inner class Bridge {
        @JavascriptInterface
        fun version(): String = BuildConfig.VERSION_NAME

        @JavascriptInterface
        fun copy(text: String) {
            ui.post {
                val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Sleutelcode", text))
                if (Build.VERSION.SDK_INT < 33) toast("Gekopieerd")
            }
        }

        /** Slaat een bestand op in Downloads/Sleutelcode. [base64] is de inhoud. */
        @JavascriptInterface
        fun saveFile(name: String, base64: String): Boolean {
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                saveToDownloads(name.replace(Regex("[\\\\/:*?\"<>|]"), "_"), bytes)
                true
            } catch (e: Exception) {
                ui.post { toast("Opslaan mislukt: ${e.message}") }
                false
            }
        }

        /** Deelt een bestand via het deel-menu (Drive, WhatsApp, mail...). */
        @JavascriptInterface
        fun shareFile(name: String, base64: String): Boolean {
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                ui.post { shareBytes(name, bytes) }
                true
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun checkUpdates() {
            ui.post { checkForUpdates(manual = true) }
        }
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "zip" -> "application/zip"
        "txt", "scad" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }

    private fun saveToDownloads(name: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(name))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Sleutelcode")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("kan geen bestand aanmaken")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("kan niet schrijven")
            ui.post { toast("Opgeslagen in Downloads/Sleutelcode") }
        } else {
            // Android 8/9: geen MediaStore.Downloads, dus via het deel-menu
            ui.post { shareBytes(name, bytes) }
        }
    }

    private fun shareBytes(name: String, bytes: ByteArray) {
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeFor(name))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Delen of opslaan"))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------------------------------------------------------------- updater

    private fun checkForUpdates(manual: Boolean) {
        if (busy) return
        busy = true
        if (manual) toast("Zoeken naar updates…")
        Thread {
            val result = runCatching { Updater.fetchLatest() }
            ui.post {
                busy = false
                if (isFinishing) return@post
                result.onSuccess { release ->
                    if (release != null && Updater.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                        pending = release
                        askUpdate(release)
                    } else if (manual) {
                        AlertDialog.Builder(this)
                            .setTitle("Geen update")
                            .setMessage("Je hebt de nieuwste versie (${BuildConfig.VERSION_NAME}).")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }.onFailure {
                    if (manual) toast("Updates controleren mislukt: ${it.message}")
                }
            }
        }.start()
    }

    private fun askUpdate(release: Updater.Release) {
        AlertDialog.Builder(this)
            .setTitle("Nieuwe versie ${release.version}")
            .setMessage(
                "Er is een update beschikbaar (je hebt ${BuildConfig.VERSION_NAME})." +
                    if (release.notes.isNotBlank()) "\n\n${release.notes.trim().take(1500)}" else ""
            )
            .setPositiveButton("Update") { _, _ -> startUpdate(release) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startUpdate(release: Updater.Release) {
        busy = true
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val status = TextView(this).apply { text = "Downloaden…" }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(status)
            addView(bar, LinearLayout.LayoutParams(-1, -2))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Update ${release.version}")
            .setView(box)
            .setCancelable(false)
            .show()

        Thread {
            val result = runCatching {
                Updater.download(this, release) { done, total ->
                    if (total > 0) ui.post { bar.progress = (done * 100 / total).toInt() }
                }
            }
            ui.post {
                busy = false
                dialog.dismiss()
                result.onSuccess { apk ->
                    val started = Updater.install(this, apk)
                    if (!started) {
                        AlertDialog.Builder(this)
                            .setTitle("Toestemming nodig")
                            .setMessage("Sta 'Installeren van onbekende apps' toe voor Sleutelcode en tik daarna opnieuw op Update.")
                            .setPositiveButton("Opnieuw") { _, _ -> pending?.let { startUpdate(it) } }
                            .setNegativeButton("Later", null)
                            .show()
                    }
                }.onFailure {
                    toast("Downloaden mislukt: ${it.message}")
                }
            }
        }.start()
    }
}
