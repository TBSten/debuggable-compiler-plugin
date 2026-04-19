package me.tbsten.debuggable.compiler.compat

/**
 * Lightweight Kotlin compiler version comparison used to pick the best [IrInjector.Factory].
 *
 * Accepts strings like `"2.3.20"`, `"2.4.0-Beta1"`, or `"2.3.20-dev-5706"`. Prerelease
 * classifiers (`Beta1`, `RC2`, `dev-xxx`) sort lower than the corresponding stable release
 * when the numeric parts are equal — this mirrors the ordering Kotlin itself uses and lets
 * a factory registered with `minVersion = "2.3.20"` match both `2.3.20` and `2.3.21`.
 */
internal data class SimpleKotlinVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** Lowercase, with `-` separators stripped — e.g. `"beta1"`, `"rc2"`, `"dev5706"`. */
    val prerelease: String? = null,
) : Comparable<SimpleKotlinVersion> {

    override fun compareTo(other: SimpleKotlinVersion): Int {
        major.compareTo(other.major).takeIf { it != 0 }?.let { return it }
        minor.compareTo(other.minor).takeIf { it != 0 }?.let { return it }
        patch.compareTo(other.patch).takeIf { it != 0 }?.let { return it }
        // Same numeric parts — prerelease is always "earlier" than a stable release.
        return when {
            prerelease == null && other.prerelease == null -> 0
            prerelease == null -> 1  // stable > prerelease
            other.prerelease == null -> -1
            else -> prerelease.compareTo(other.prerelease)
        }
    }

    companion object {
        private val REGEX = Regex("""(\d+)\.(\d+)\.(\d+)(?:[.\-]?([A-Za-z][\w\-]*))?""")

        fun parse(text: String): SimpleKotlinVersion {
            val m = REGEX.matchEntire(text.trim())
                ?: error("Cannot parse Kotlin version string: '$text'")
            val (major, minor, patch) = m.destructured
            val pre = m.groupValues.getOrNull(4)?.takeIf { it.isNotEmpty() }
                ?.lowercase()?.replace("-", "")
            return SimpleKotlinVersion(major.toInt(), minor.toInt(), patch.toInt(), pre)
        }
    }
}
