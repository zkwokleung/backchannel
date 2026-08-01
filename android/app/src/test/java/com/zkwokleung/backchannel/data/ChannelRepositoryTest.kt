package com.zkwokleung.backchannel.data

import com.zkwokleung.backchannel.data.db.ChannelDao
import com.zkwokleung.backchannel.data.db.ChannelEntity
import com.zkwokleung.backchannel.data.db.VideoDao
import com.zkwokleung.backchannel.data.db.VideoEntity
import com.zkwokleung.backchannel.engine.ChannelMeta
import com.zkwokleung.backchannel.engine.ChannelSource
import com.zkwokleung.backchannel.engine.VideoMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private class FakeChannelSource(
    var meta: ChannelMeta = ChannelMeta("UC1", "@chan", "Chan", null),
    var uploads: List<VideoMeta> = emptyList(),
) : ChannelSource {
    override suspend fun resolveChannel(handleOrUrl: String) = meta
    override suspend fun listChannelVideos(channelYoutubeId: String, limit: Int) = uploads
}

private class FakeChannelDao : ChannelDao {
    val rows = linkedMapOf<String, ChannelEntity>()
    override suspend fun upsert(channel: ChannelEntity) { rows[channel.youtubeId] = channel }
    override fun observeAll(): Flow<List<ChannelEntity>> = flowOf(rows.values.toList())
    override suspend fun getById(youtubeId: String) = rows[youtubeId]
    override fun observeById(youtubeId: String): Flow<ChannelEntity?> = flowOf(rows[youtubeId])
    override suspend fun delete(youtubeId: String) { rows.remove(youtubeId) }
}

private class FakeVideoDao : VideoDao {
    val rows = mutableListOf<VideoEntity>()
    override suspend fun insertAll(videos: List<VideoEntity>) { rows.addAll(videos) }
    override suspend fun deleteForChannel(channelYoutubeId: String) {
        rows.removeAll { it.channelYoutubeId == channelYoutubeId }
    }
    override fun observeForChannel(channelYoutubeId: String): Flow<List<VideoEntity>> =
        flowOf(rows.filter { it.channelYoutubeId == channelYoutubeId })
    override suspend fun getById(youtubeId: String) = rows.firstOrNull { it.youtubeId == youtubeId }
    override suspend fun countForChannel(channelYoutubeId: String) =
        rows.count { it.channelYoutubeId == channelYoutubeId }
}

class ChannelRepositoryTest {

    private val source = FakeChannelSource()
    private val channelDao = FakeChannelDao()
    private val videoDao = FakeVideoDao()
    private val repository = ChannelRepository(source, channelDao, videoDao)

    private fun upload(id: String) = VideoMeta(id, "Title $id", 100, null, null)

    @Test
    fun `an empty extraction keeps the existing cache instead of wiping it`() = runBlocking {
        source.uploads = listOf(upload("a"), upload("b"))
        repository.addChannel("@chan")
        assertEquals(2, videoDao.countForChannel("UC1"))

        // Stale yt-dlp / YouTube change: parses fine, yields nothing, raises no error.
        source.uploads = emptyList()
        assertThrows(EmptyRefreshException::class.java) {
            runBlocking { repository.refreshVideos("UC1") }
        }

        assertEquals(2, videoDao.countForChannel("UC1"))
    }

    @Test
    fun `a successful refresh still replaces the cache`() = runBlocking {
        source.uploads = listOf(upload("a"), upload("b"))
        repository.addChannel("@chan")

        source.uploads = listOf(upload("c"))
        assertEquals(1, repository.refreshVideos("UC1"))
        assertEquals(listOf("c"), videoDao.rows.map { it.youtubeId })
    }

    @Test
    fun `a channel with genuinely no uploads is allowed`() = runBlocking {
        source.uploads = emptyList()
        repository.addChannel("@chan")
        assertEquals(0, videoDao.countForChannel("UC1"))
    }

    @Test
    fun `adding an already-saved channel is rejected and leaves it untouched`() = runBlocking {
        source.uploads = listOf(upload("a"))
        val first = repository.addChannel("@chan")

        // Same channel entered a second time, e.g. by its UC… URL rather than the handle.
        assertThrows(ChannelAlreadySavedException::class.java) {
            runBlocking { repository.addChannel("https://www.youtube.com/channel/UC1") }
        }

        assertEquals(first.addedAt, channelDao.rows.getValue("UC1").addedAt)
        assertEquals(1, videoDao.countForChannel("UC1"))
    }

    @Test
    fun `uploads are cached newest-first via sortIndex`() = runBlocking {
        source.uploads = listOf(upload("newest"), upload("older"))
        repository.addChannel("@chan")
        assertEquals(listOf(0, 1), videoDao.rows.map { it.sortIndex })
    }
}
