package com.zkwokleung.backchannel.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The slice of GitHub's releases API this app reads.
 *
 * Modelled on real payloads rather than the docs: every field is optional except the ones the
 * feature cannot work without, because the API adds keys over time and `ignoreUnknownKeys`
 * only covers additions, not a field that quietly starts arriving as null.
 *
 * See https://docs.github.com/rest/releases/releases#get-the-latest-release — the repo is
 * public, so this is fetched unauthenticated with no token anywhere in the app.
 */
@Serializable
data class GitHubRelease(
    /** `vX.Y.Z`, matching the tag `release.yml` builds from. */
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    /** Release notes, Markdown. Shown as plain text in the confirm dialog. */
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
) {
    /** The tag without its `v`, which is exactly what `release.yml` sets as `versionName`. */
    val versionName: String get() = tagName.trim().removePrefix("v").removePrefix("V")
}

@Serializable
data class GitHubAsset(
    /** `backchannel-<version>-<abi>.apk`, or `SHA256SUMS.txt`. */
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String? = null,
    /** `"uploaded"` once the asset is actually downloadable. */
    val state: String? = null,
    /**
     * `sha256:<hex>`, added by GitHub in 2025. Observed null on real releases, so it is used
     * only when present — [parseChecksums] over `SHA256SUMS.txt` remains the source of truth.
     */
    val digest: String? = null,
)
