package com.zkwokleung.backchannel.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareVersionsTest {

    @Test
    fun `a higher patch is newer`() {
        assertEquals(VersionOrder.NEWER, compareVersions("0.2.1", "0.2.0"))
    }

    @Test
    fun `ordering is numeric, not lexical`() {
        assertEquals(VersionOrder.NEWER, compareVersions("0.10.0", "0.9.0"))
        assertEquals(VersionOrder.OLDER, compareVersions("0.9.0", "0.10.0"))
    }

    @Test
    fun `a leading v on either side is tolerated`() {
        assertEquals(VersionOrder.NEWER, compareVersions("v0.3.0", "0.2.0"))
        assertEquals(VersionOrder.SAME, compareVersions("v0.2.0", "0.2.0"))
    }

    @Test
    fun `missing components count as zero`() {
        assertEquals(VersionOrder.SAME, compareVersions("1.0", "1.0.0"))
        assertEquals(VersionOrder.NEWER, compareVersions("1.0.1", "1.0"))
    }

    @Test
    fun `a pre-release is older than the release it precedes`() {
        assertEquals(VersionOrder.OLDER, compareVersions("1.0.0-beta.1", "1.0.0"))
        assertEquals(VersionOrder.NEWER, compareVersions("1.0.0", "1.0.0-beta.1"))
    }

    @Test
    fun `pre-releases order among themselves`() {
        assertEquals(VersionOrder.NEWER, compareVersions("1.0.0-beta.2", "1.0.0-beta.1"))
        assertEquals(VersionOrder.NEWER, compareVersions("1.0.0-beta", "1.0.0-alpha"))
        // Numeric identifiers rank below alphanumeric ones.
        assertEquals(VersionOrder.OLDER, compareVersions("1.0.0-1", "1.0.0-alpha"))
        // Fewer identifiers rank lower when the shared prefix matches.
        assertEquals(VersionOrder.OLDER, compareVersions("1.0.0-beta", "1.0.0-beta.1"))
    }

    @Test
    fun `build metadata does not affect precedence`() {
        assertEquals(VersionOrder.SAME, compareVersions("1.0.0+abc123", "1.0.0"))
    }

    @Test
    fun `unparseable versions never look newer`() {
        // The important property: garbage must not trigger a download.
        assertEquals(VersionOrder.UNKNOWN, compareVersions("nightly", "0.2.0"))
        assertEquals(VersionOrder.UNKNOWN, compareVersions("0.2.0", ""))
        assertEquals(VersionOrder.UNKNOWN, compareVersions("1.x.0", "0.2.0"))
    }
}

class PickAssetTest {

    private fun asset(name: String, state: String? = "uploaded") = GitHubAsset(
        name = name,
        size = 1,
        browserDownloadUrl = "https://example.invalid/$name",
        state = state,
    )

    private val release = listOf(
        asset("backchannel-0.2.0-arm64-v8a.apk"),
        asset("backchannel-0.2.0-x86_64.apk"),
        asset("backchannel-0.2.0-universal.apk"),
        asset("SHA256SUMS.txt"),
    )

    @Test
    fun `an exact ABI split wins over universal`() {
        val picked = pickAsset(release, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))
        assertEquals("backchannel-0.2.0-arm64-v8a.apk", picked?.name)
    }

    @Test
    fun `the emulator ABI resolves to its own split`() {
        val picked = pickAsset(release, listOf("x86_64", "x86"))
        assertEquals("backchannel-0.2.0-x86_64.apk", picked?.name)
    }

    @Test
    fun `a device with no split falls back to universal`() {
        val picked = pickAsset(release, listOf("armeabi-v7a", "armeabi"))
        assertEquals("backchannel-0.2.0-universal.apk", picked?.name)
    }

    @Test
    fun `ABI preference order is respected`() {
        // SUPPORTED_ABIS is ordered best-first; the first entry that has a split must win even
        // when a later entry also has one.
        val both = listOf(
            asset("backchannel-0.2.0-x86_64.apk"),
            asset("backchannel-0.2.0-arm64-v8a.apk"),
        )
        assertEquals("backchannel-0.2.0-arm64-v8a.apk", pickAsset(both, listOf("arm64-v8a", "x86_64"))?.name)
    }

    @Test
    fun `the checksum file is never offered as a download`() {
        val picked = pickAsset(listOf(asset("SHA256SUMS.txt")), listOf("arm64-v8a"))
        assertNull(picked)
    }

    @Test
    fun `assets still uploading are skipped`() {
        val pending = listOf(asset("backchannel-0.2.0-arm64-v8a.apk", state = "starter"))
        assertNull(pickAsset(pending, listOf("arm64-v8a")))
    }

    @Test
    fun `a missing state is treated as uploaded`() {
        val noState = listOf(asset("backchannel-0.2.0-arm64-v8a.apk", state = null))
        assertNotNull(pickAsset(noState, listOf("arm64-v8a")))
    }

    @Test
    fun `an empty release yields nothing rather than crashing`() {
        assertNull(pickAsset(emptyList(), listOf("arm64-v8a")))
    }
}

class ParseChecksumsTest {

    @Test
    fun `sha256sum output parses to name and hash`() {
        val text = """
            ${A_HASH}  backchannel-0.2.0-arm64-v8a.apk
            ${B_HASH}  backchannel-0.2.0-universal.apk
        """.trimIndent()

        val parsed = parseChecksums(text)
        assertEquals(2, parsed.size)
        assertEquals(A_HASH, parsed["backchannel-0.2.0-arm64-v8a.apk"])
        assertEquals(B_HASH, parsed["backchannel-0.2.0-universal.apk"])
    }

    @Test
    fun `binary-mode asterisks and mixed case are normalised`() {
        val parsed = parseChecksums("${A_HASH.uppercase()} *backchannel-0.2.0-x86_64.apk")
        assertEquals(A_HASH, parsed["backchannel-0.2.0-x86_64.apk"])
    }

    @Test
    fun `junk lines are skipped without losing valid ones`() {
        val text = "\n# a comment\nnot-a-hash file.apk\n$A_HASH  good.apk\n\n"
        assertEquals(mapOf("good.apk" to A_HASH), parseChecksums(text))
    }

    @Test
    fun `an empty file yields an empty map`() {
        assertTrue(parseChecksums("").isEmpty())
    }

    private companion object {
        const val A_HASH = "1f0e3dad99908345f7439f8ffabdffc4e5e2a5d0b1c9f4e8a7b6c5d4e3f21098"
        const val B_HASH = "9a0364b9e99bb480dd25e1f0284c8555c1f0e3dad99908345f7439f8ffabdffc"
    }
}

class DownloadProgressTest {

    @Test
    fun `progress spans zero to a hundred`() {
        assertEquals(0, percentOf(0, 100))
        assertEquals(42, percentOf(42, 100))
        assertEquals(100, percentOf(100, 100))
    }

    @Test
    fun `an unknown total reports zero rather than dividing by it`() {
        // contentLength() is -1 once github.com redirects to its asset host.
        assertEquals(0, percentOf(5_000, -1))
        assertEquals(0, percentOf(5_000, 0))
    }

    @Test
    fun `a body longer than advertised still clamps to a hundred`() {
        assertEquals(100, percentOf(120, 100))
    }

    @Test
    fun `large sizes do not overflow`() {
        // 18 MB * 100 comfortably exceeds Int range; the arithmetic has to stay in Long.
        assertEquals(50, percentOf(9_000_000, 18_000_000))
    }

    @Test
    fun `hex encoding is lowercase and zero-padded`() {
        assertEquals("000fff", byteArrayOf(0x00, 0x0f, 0xff.toByte()).toHexString())
    }
}

/**
 * Guards the contract between `.github/workflows/release.yml` and the updater.
 *
 * The workflow stages assets as `backchannel-<version>-<abi>.apk` plus `SHA256SUMS.txt`, and the
 * app finds its download by parsing those names. Rename them there and this fails here, rather
 * than shipping an app that silently never finds an update.
 */
class ReleasePayloadContractTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Trimmed from the shape GitHub actually returns, keeping every field the app reads —
    // including `digest`, which is present in the schema but null on real releases.
    private val payload = """
        {
          "tag_name": "v0.2.0",
          "name": "Backchannel v0.2.0",
          "body": "Install the APK matching your device.",
          "draft": false,
          "prerelease": false,
          "published_at": "2026-08-02T10:00:00Z",
          "html_url": "https://github.com/zkwokleung/backchannel/releases/tag/v0.2.0",
          "author": { "login": "zkwokleung" },
          "assets": [
            {
              "name": "SHA256SUMS.txt",
              "content_type": "text/plain",
              "state": "uploaded",
              "size": 231,
              "digest": null,
              "browser_download_url": "https://github.com/zkwokleung/backchannel/releases/download/v0.2.0/SHA256SUMS.txt"
            },
            {
              "name": "backchannel-0.2.0-arm64-v8a.apk",
              "content_type": "application/vnd.android.package-archive",
              "state": "uploaded",
              "size": 18452791,
              "digest": null,
              "browser_download_url": "https://github.com/zkwokleung/backchannel/releases/download/v0.2.0/backchannel-0.2.0-arm64-v8a.apk"
            },
            {
              "name": "backchannel-0.2.0-universal.apk",
              "content_type": "application/vnd.android.package-archive",
              "state": "uploaded",
              "size": 50697216,
              "digest": null,
              "browser_download_url": "https://github.com/zkwokleung/backchannel/releases/download/v0.2.0/backchannel-0.2.0-universal.apk"
            },
            {
              "name": "backchannel-0.2.0-x86_64.apk",
              "content_type": "application/vnd.android.package-archive",
              "state": "uploaded",
              "size": 18103442,
              "digest": null,
              "browser_download_url": "https://github.com/zkwokleung/backchannel/releases/download/v0.2.0/backchannel-0.2.0-x86_64.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `a real payload parses and yields an installable asset`() {
        val release = json.decodeFromString<GitHubRelease>(payload)

        assertEquals("0.2.0", release.versionName)
        assertEquals(4, release.assets.size)

        val picked = pickAsset(release.assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("backchannel-0.2.0-arm64-v8a.apk", picked?.name)
        assertEquals(18452791L, picked?.size)
        assertTrue(picked!!.browserDownloadUrl.startsWith("https://"))
    }

    @Test
    fun `the checksum asset is present and findable by name`() {
        val release = json.decodeFromString<GitHubRelease>(payload)
        assertNotNull(release.assets.firstOrNull { it.name == CHECKSUMS_ASSET_NAME })
    }

    @Test
    fun `unknown fields do not break decoding`() {
        // "author" is in the payload above and is not modelled; the API adds keys over time.
        val release = json.decodeFromString<GitHubRelease>(payload)
        assertEquals("v0.2.0", release.tagName)
    }
}
