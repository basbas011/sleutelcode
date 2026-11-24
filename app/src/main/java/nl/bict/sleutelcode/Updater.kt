package nl.bict.sleutelcode

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updater: zoekt de nieuwste GitHub Release van BuildConfig.GITHUB_REPO,
 * downloadt de .apk uit die release en start de Android-installer.
 *
 * Werkt met een PUBLIEKE repo. Releases worden gemaakt door de GitHub Actions
 * workflow (tag v1.2.3 -> release met app-1.2.3.apk).
 */
object Updater {

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val size: Long
    )

    /** Haalt de nieuwste release op. Geeft null als er geen release met een .apk is. Blokkerend: niet op de main thread aanroepen. */
    fun fetchLatest(): Release? {
        val repo = BuildConfig.GITHUB_REPO
        require(repo.isNotBlank()) { "githubRepo is niet ingesteld in gradle.properties" }

        val conn = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode == 404) return null // nog geen release
            if (conn.responseCode != 200) error("GitHub antwoordde met HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk", ignoreCase = true)) {
                    return Release(
                        version = json.getString("tag_name").removePrefix("v"),
                        notes = json.optString("body", ""),
                        apkUrl = a.getString("browser_download_url"),
                        apkName = a.getString("name"),
                        size = a.optLong("size", 0)
                    )
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** True als [remote] een hogere versie is dan [local] (1.10.0 > 1.9.0). */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Downloadt de APK naar de cache-map. Blokkerend. [onProgress] krijgt (gedaan, totaal) in bytes. */
    fun download(context: Context, release: Release, onProgress: (Long, Long) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, release.apkName)

        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode != 200) error("Download mislukt: HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 100) {
                            onProgress(done, total)
                            lastReport = now
                        }
                    }
                    onProgress(done, total)
                }
            }
        } finally {
            conn.disconnect()
        }
        return target
    }

    /**
     * Start de Android-installer voor [apk]. Geeft false als de gebruiker eerst
     * "Installeren van onbekende apps" voor deze app moet toestaan; het
     * instellingenscherm wordt dan geopend en de update moet daarna opnieuw gestart worden.
     */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
