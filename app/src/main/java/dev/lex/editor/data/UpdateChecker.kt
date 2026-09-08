package dev.lex.editor.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Outcome of asking GitHub what the newest published version is. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val versionName: String) : UpdateStatus
    data object Failed : UpdateStatus
}

/**
 * Reads a small version file committed next to the source rather than calling the
 * GitHub API: the raw file needs no authentication, is not subject to the API's 60
 * requests per hour per IP, and is cached by GitHub's CDN.
 *
 * This is the only network call in Lex, it happens only when the user asks for it, and
 * it deliberately stops at comparing version numbers. Downloading and installing an APK
 * would need REQUEST_INSTALL_PACKAGES -- a sensitive permission and a well-worn malware
 * path -- so that part is left to the browser and the system installer.
 */
class UpdateChecker(context: Context) {

    private val appContext = context.applicationContext

    suspend fun check(): UpdateStatus = withContext(Dispatchers.IO) {
        val installed = installedVersionCode() ?: return@withContext UpdateStatus.Failed

        runCatching {
            val connection = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                // No credentials, no cookies, no identifying headers beyond the default.
                setRequestProperty("Accept", "application/json")
            }
            val body = connection.use { it.inputStream.bufferedReader().readText() }
            val json = JSONObject(body)
            val latestCode = json.getLong("versionCode")
            val latestName = json.getString("versionName")

            if (latestCode > installed) UpdateStatus.Available(latestName) else UpdateStatus.UpToDate
        }.getOrElse {
            Log.w(TAG, "Update check failed", it)
            UpdateStatus.Failed
        }
    }

    /**
     * The installed version, read from the package manager because BuildConfig is not
     * generated for this module.
     */
    private fun installedVersionCode(): Long? = runCatching {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode
    }.getOrNull()

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }

    companion object {
        private const val TAG = "Lex"
        const val VERSION_URL = "https://raw.githubusercontent.com/029527/lex/main/version.json"
        const val RELEASES_URL = "https://github.com/029527/lex/releases/latest"
    }
}
