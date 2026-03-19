package com.photoframe.viewmodel

import com.photoframe.data.AppPrefs
import com.photoframe.data.Photo
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class MainViewModelTest {

    private val prefs = mockk<AppPrefs>(relaxed = true)
    private lateinit var viewModel: MainViewModel

    private fun makePhoto(id: Long, name: String = "test") = Photo(
        id = id, url = "http://img/$id.jpg",
        takenAt = null, uploadedAt = "2026-03-19",
        latitude = null, longitude = null, locationAddress = null,
        cameraMake = null, cameraModel = null,
        uploaderName = name
    )

    @BeforeEach
    fun setup() {
        every { prefs.playMode } returns "sequential"
        every { prefs.showPhotoInfo } returns true
        every { prefs.slideDurationSec } returns 15
        every { prefs.transitionEffect } returns "fade"
        viewModel = MainViewModel(prefs)
    }

    @Test
    fun `onNewPhotos adds photos in sequential mode`() {
        val photos = listOf(makePhoto(1), makePhoto(2))
        viewModel.onNewPhotos(photos)
        assertEquals(2, viewModel.uiState.value.photos.size)
        assertEquals(1L, viewModel.uiState.value.photos[0].id)
        assertEquals(2L, viewModel.uiState.value.photos[1].id)
    }

    @Test
    fun `onNewPhotos deduplicates by id`() {
        viewModel.onNewPhotos(listOf(makePhoto(1), makePhoto(2)))
        viewModel.onNewPhotos(listOf(makePhoto(2), makePhoto(3)))
        assertEquals(3, viewModel.uiState.value.photos.size)
    }

    @Test
    fun `onNewPhotos shuffles in random mode`() {
        every { prefs.playMode } returns "random"
        viewModel = MainViewModel(prefs)
        val photos = (1L..100L).map { makePhoto(it) }
        viewModel.onNewPhotos(photos)
        // 100张照片 shuffle 后不太可能与原序一致
        val ids = viewModel.uiState.value.photos.map { it.id }
        assertNotEquals((1L..100L).toList(), ids,
            "100 photos should be shuffled (extremely unlikely to remain in order)")
    }

    @Test
    fun `onNewPhotos ignores empty new list`() {
        viewModel.onNewPhotos(listOf(makePhoto(1)))
        viewModel.onNewPhotos(emptyList())
        assertEquals(1, viewModel.uiState.value.photos.size)
    }

    @Test
    fun `onNewPhotos ignores all-duplicate list`() {
        viewModel.onNewPhotos(listOf(makePhoto(1)))
        viewModel.onNewPhotos(listOf(makePhoto(1)))
        assertEquals(1, viewModel.uiState.value.photos.size)
    }

    @Test
    fun `nextPageIndex cycles through photos`() {
        viewModel.onNewPhotos(listOf(makePhoto(1), makePhoto(2), makePhoto(3)))
        assertEquals(1, viewModel.nextPageIndex())
        assertEquals(2, viewModel.nextPageIndex())
        assertEquals(0, viewModel.nextPageIndex()) // wrap around
    }

    @Test
    fun `nextPageIndex returns 0 when no photos`() {
        assertEquals(0, viewModel.nextPageIndex())
    }

    @Test
    fun `loadPreferences updates ui state from prefs`() {
        every { prefs.slideDurationSec } returns 30
        every { prefs.transitionEffect } returns "zoom"
        every { prefs.showPhotoInfo } returns false
        viewModel.loadPreferences()
        assertEquals(30_000L, viewModel.uiState.value.slideDurationMs)
        assertEquals("zoom", viewModel.uiState.value.transitionEffect)
        assertFalse(viewModel.uiState.value.showPhotoInfo)
    }

    @Test
    fun `setNightMode updates state`() {
        viewModel.setNightMode(true)
        assertTrue(viewModel.uiState.value.isNightMode)
        viewModel.setNightMode(false)
        assertFalse(viewModel.uiState.value.isNightMode)
    }

    @Test
    fun `setCurrentIndex updates state`() {
        viewModel.onNewPhotos(listOf(makePhoto(1), makePhoto(2), makePhoto(3)))
        viewModel.setCurrentIndex(2)
        assertEquals(2, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `onNewPhotos preserves order in sequential mode after multiple batches`() {
        viewModel.onNewPhotos(listOf(makePhoto(1), makePhoto(2)))
        viewModel.onNewPhotos(listOf(makePhoto(3), makePhoto(4)))
        val ids = viewModel.uiState.value.photos.map { it.id }
        assertEquals(listOf(1L, 2L, 3L, 4L), ids)
    }
}
