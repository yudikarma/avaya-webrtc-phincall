package com.phincall.webrtc.config

/**
 * Validates and sanitizes user-entered endpoints, SIP identifiers, and log filenames
 * to prevent URL authority hijacking, path traversal, and SIP header injection.
 */
object InputValidator {

    private val HOST_PATTERN = Regex("^[A-Za-z0-9._:\\[\\]-]{1,253}$")
    private val SAFE_PATH_PATTERN = Regex("^[A-Za-z0-9/._-]{0,256}$")
    private val SAFE_SIP_USER_PATTERN = Regex("^[A-Za-z0-9._+*#@-]{1,64}$")
    private val UNSAFE_TEXT_CHARS = Regex("[\\x00-\\x1F\\x7F<>\";\\\\]")
    private val SAFE_LOG_FILENAME_PATTERN = Regex("^[A-Za-z0-9._-]{1,64}$")
    private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val LOOPBACK_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")

    fun isValidHost(host: String): Boolean {
        val trimmed = host.trim()
        if (trimmed.isEmpty() || trimmed.contains("..")) return false
        return HOST_PATTERN.matches(trimmed)
    }

    fun isLoopbackHost(host: String): Boolean =
        LOOPBACK_HOSTS.contains(host.trim().lowercase())

    fun isValidPort(port: Int?): Boolean = port != null && port in 1..65535

    fun isValidUrlPath(path: String): Boolean {
        val trimmed = path.trim()
        if (trimmed.contains("..") || trimmed.contains("//")) return false
        return SAFE_PATH_PATTERN.matches(trimmed)
    }

    fun isValidSipUser(value: String): Boolean =
        value.isBlank() || SAFE_SIP_USER_PATTERN.matches(value.trim())

    fun isSafeDisplayText(value: String, maxLength: Int = 80): Boolean {
        val trimmed = value.trim()
        return trimmed.length <= maxLength && !UNSAFE_TEXT_CHARS.containsMatchIn(trimmed)
    }

    fun isValidPriority(priority: Int?): Boolean = priority != null && priority in 1..10

    fun sanitizeLogFileName(name: String): String {
        val cleaned = name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (SAFE_LOG_FILENAME_PATTERN.matches(cleaned)) cleaned else "WebRTCReferenceClient"
    }

    fun isValidEmailOrBlank(email: String): Boolean =
        email.isBlank() || EMAIL_PATTERN.matches(email.trim())
}
