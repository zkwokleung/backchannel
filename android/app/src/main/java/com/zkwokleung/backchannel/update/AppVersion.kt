package com.zkwokleung.backchannel.update

/**
 * Version, asset and checksum logic — no Android, no network, no coroutines.
 *
 * Everything decidable from strings alone lives here on purpose. The project has JUnit 4 and
 * nothing else (no Robolectric, no MockWebServer), so the parts actually worth testing are kept
 * clear of the runtime that would make them untestable.
 */

/** The checksum manifest `release.yml` uploads alongside the APKs. */
const val CHECKSUMS_ASSET_NAME = "SHA256SUMS.txt"

/** How a candidate version relates to the installed one. */
enum class VersionOrder { NEWER, SAME, OLDER, UNKNOWN }

/**
 * Semantic-version comparison of [candidate] against [installed].
 *
 * `versionCode` cannot be used for this: `release.yml` sets it to the CI run number, which is
 * meaningless outside CI. The tag's `versionName` is the only ordered identifier both sides share.
 *
 * A leading `v` is tolerated on either side, and pre-release suffixes follow semver precedence
 * (`1.0.0-beta` is older than `1.0.0`). Anything unparseable yields [VersionOrder.UNKNOWN] rather
 * than a guess, so a malformed tag can never trigger a download.
 */
fun compareVersions(candidate: String, installed: String): VersionOrder {
    val a = SemVer.parse(candidate) ?: return VersionOrder.UNKNOWN
    val b = SemVer.parse(installed) ?: return VersionOrder.UNKNOWN
    val order = a.compareTo(b)
    return when {
        order > 0 -> VersionOrder.NEWER
        order < 0 -> VersionOrder.OLDER
        else -> VersionOrder.SAME
    }
}

/**
 * Picks the APK asset for this device, preferring an exact ABI split and falling back to the
 * universal build.
 *
 * [supportedAbis] is `Build.SUPPORTED_ABIS`, which is already ordered best-first. Returns null
 * when the release carries nothing installable — a real state after a partially failed upload,
 * not an impossibility.
 */
fun pickAsset(assets: List<GitHubAsset>, supportedAbis: List<String>): GitHubAsset? {
    val installable = assets.filter { it.isInstallableApk }
    for (abi in supportedAbis) {
        installable.firstOrNull { it.matchesAbi(abi) }?.let { return it }
    }
    // A 32-bit-only device matches no split; the universal APK carries every ABI.
    return installable.firstOrNull { it.matchesAbi(UNIVERSAL_ABI) }
}

/**
 * Parses `sha256sum` output into filename → lowercase hash.
 *
 * Lines are `<64 hex>  <filename>`; binary-mode output prefixes the name with `*`. A line that
 * isn't a hash followed by a name is skipped rather than failing the whole file, so a stray
 * blank line or header never costs the user an update.
 */
fun parseChecksums(text: String): Map<String, String> = buildMap {
    for (line in text.lineSequence()) {
        val parts = line.trim().split(WHITESPACE, limit = 2)
        if (parts.size != 2) continue
        val hash = parts[0].lowercase()
        if (!SHA256_HEX.matches(hash)) continue
        val name = parts[1].removePrefix("*").trim()
        if (name.isNotEmpty()) put(name, hash)
    }
}

private const val UNIVERSAL_ABI = "universal"
private val WHITESPACE = Regex("\\s+")
private val SHA256_HEX = Regex("[0-9a-f]{64}")

private val GitHubAsset.isInstallableApk: Boolean
    get() = name.endsWith(".apk", ignoreCase = true) &&
        // `state` is absent on some payloads; only a state that is explicitly not "uploaded"
        // means the asset isn't downloadable yet.
        (state == null || state.equals("uploaded", ignoreCase = true))

/** Matches the `backchannel-<version>-<abi>.apk` names staged by `release.yml`. */
private fun GitHubAsset.matchesAbi(abi: String): Boolean =
    name.endsWith("-$abi.apk", ignoreCase = true)

private class SemVer(private val core: List<Int>, private val pre: List<String>) :
    Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        repeat(maxOf(core.size, other.core.size)) { i ->
            val diff = core.getOrElse(i) { 0 }.compareTo(other.core.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        // Same core: a pre-release ranks below the release it precedes.
        if (pre.isEmpty() && other.pre.isEmpty()) return 0
        if (pre.isEmpty()) return 1
        if (other.pre.isEmpty()) return -1
        repeat(maxOf(pre.size, other.pre.size)) { i ->
            // Ran out of identifiers first: fewer identifiers ranks lower.
            val mine = pre.getOrNull(i) ?: return -1
            val theirs = other.pre.getOrNull(i) ?: return 1
            val diff = compareIdentifiers(mine, theirs)
            if (diff != 0) return diff
        }
        return 0
    }

    /** Semver rule: numeric identifiers compare numerically and rank below alphanumeric ones. */
    private fun compareIdentifiers(a: String, b: String): Int {
        val an = a.toIntOrNull()
        val bn = b.toIntOrNull()
        return when {
            an != null && bn != null -> an.compareTo(bn)
            an != null -> -1
            bn != null -> 1
            else -> a.compareTo(b)
        }
    }

    companion object {
        fun parse(raw: String): SemVer? {
            val text = raw.trim().removePrefix("v").removePrefix("V")
            if (text.isEmpty()) return null
            // Build metadata (`+sha`) is ignored for precedence, per semver.
            val withoutBuild = text.substringBefore('+')
            val core = withoutBuild.substringBefore('-')
            val parts = core.split('.').map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }
            val pre = withoutBuild.substringAfter('-', "").split('.').filter { it.isNotEmpty() }
            return SemVer(parts, pre)
        }
    }
}
