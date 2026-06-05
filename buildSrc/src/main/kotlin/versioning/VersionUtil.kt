package versioning

/**
 * Utility for computing the version string of Void artifacts.
 *
 * The version string is constructed based on:
 * - A base semantic version (e.g., "2.3.72")
 * - Modifiers (e.g., lite, no_relocate) for non-default build configurations
 *
 * Example outputs:
 * - `2.3.72` (release build)
 * - `2.3.72+lite` (preview build without PE shading)
 * - `2.3.72+lite-no_relocate`
 *
 * @see BuildConfig for controlling the release/modifier behavior
 */
object VersionUtil {

    /**
     * Computes the full version string for the build.
     *
     * @param baseVersion The base semantic version (e.g., "2.3.72")
     * @return Full version string including commit hash, branch, and modifiers if applicable
     */
    fun computeVersion(baseVersion: String): String {
        if (BuildConfig.release) {
            return baseVersion
        }

        val modifiers = buildList {
            if (!BuildConfig.shadePE) add("lite")
            if (!BuildConfig.relocate) add("no_relocate")
        }.joinToString("-").takeIf { it.isNotEmpty() }

        return buildString {
            append(baseVersion)
            modifiers?.let { append("+$it") }
        }
    }

    /**
     * Returns a static build marker when Git metadata is unavailable.
     */
    fun getGitCommitHash(full: Boolean = false): String {
        return if (full) "nogit" else "nogit"
    }

    /**
     * Returns a static build marker when Git metadata is unavailable.
     */
    fun getGitBranch(raw: Boolean = false): String? {
        return if (raw) "nogit" else null
    }

    fun getGitUser(): String {
        return "Void"
    }

}
