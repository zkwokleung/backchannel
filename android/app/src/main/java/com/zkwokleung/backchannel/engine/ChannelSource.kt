package com.zkwokleung.backchannel.engine

/**
 * The slice of the extraction engine the channel repository needs. Exists so repository logic
 * (which decides what to do with an empty result) can be tested without a device.
 */
interface ChannelSource {
    suspend fun resolveChannel(handleOrUrl: String): ChannelMeta

    suspend fun listChannelVideos(
        channelYoutubeId: String,
        limit: Int = YtdlpEngine.DEFAULT_LIST_LIMIT,
    ): List<VideoMeta>
}
