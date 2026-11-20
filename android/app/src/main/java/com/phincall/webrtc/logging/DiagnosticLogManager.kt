package com.phincall.webrtc.logging

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.phincall.webrtc.config.InputValidator
import com.phincall.webrtc.config.LoggingConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes sanitized diagnostic events to app-private internal storage (filesDir/logs/)
 * with bounded file size and shares them via FileProvider temporary URI grants only.
 * Never writes authorization tokens, proxy API keys, or external-storage files.
 */
class DiagnosticLogManager(private val context: Context) {

    @Volatile
    private var activeConfig: LoggingConfig = LoggingConfig()

    private val lock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    fun configure(config: LoggingConfig) {
        activeConfig = config
    }

    fun recordEvent(tag: String, message: String) {
        val cfg = activeConfig
        if (!cfg.logToDevice) return

        synchronized(lock) {
            runCatching {
                val file = resolveLogFile(cfg.logFileName)
                if (file.exists() && file.length() >= MAX_LOG_BYTES) {
                    val trimmed = file.readText().takeLast(MAX_LOG_BYTES / 2)
                    file.writeText(trimmed)
                }
                val stamp = dateFormat.format(Date())
                val safeMsg = redactSecrets(message)
                file.appendText("$stamp [$tag] $safeMsg\n")
            }
        }
    }

    fun recordCrash(threadName: String, error: Throwable) {
        val cfg = activeConfig
        if (!cfg.logToDevice) return

        synchronized(lock) {
            runCatching {
                val file = resolveLogFile(cfg.logFileName)
                val stamp = dateFormat.format(Date())
                val summary = buildString {
                    append("${error.javaClass.name}: ${redactSecrets(error.message.orEmpty())}")
                    error.cause?.let { cause ->
                        append(" | cause=${cause.javaClass.name}: ${redactSecrets(cause.message.orEmpty())}")
                    }
                    val frames = error.stackTrace.take(8).joinToString(" <- ") {
                        "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
                    }
                    if (frames.isNotEmpty()) {
                        append(" | trace=$frames")
                    }
                }
                file.appendText("$stamp [FATAL/$threadName] $summary\n")
            }
        }
    }

    fun createShareLogIntent(config: LoggingConfig): Intent? {
        val file = resolveLogFile(config.logFileName)
        if (!file.exists() || file.length() == 0L) {
            return null
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.logprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (config.supportEmail.isNotBlank()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(config.supportEmail.trim()))
            }
            putExtra(Intent.EXTRA_SUBJECT, "PhinCall Diagnostic Logs (${file.name})")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun resolveLogFile(rawName: String): File {
        val logsDir = File(context.filesDir, "logs").apply { mkdirs() }
        val safeBase = InputValidator.sanitizeLogFileName(rawName)
        val fileName = if (safeBase.endsWith(".log", ignoreCase = true)) safeBase else "$safeBase.log"
        return File(logsDir, fileName)
    }

    private fun redactSecrets(input: String): String =
        input.replace(TOKEN_REDACT_REGEX, "$1=[REDACTED]")

    companion object {
        private const val MAX_LOG_BYTES = 512 * 1024
        private val TOKEN_REDACT_REGEX = Regex(
            "(encryptedToken|X-Client-Key|authorizationToken)\\s*[:=]\\s*[^\\s,;\"'}]+",
            RegexOption.IGNORE_CASE
        )
    }
}
