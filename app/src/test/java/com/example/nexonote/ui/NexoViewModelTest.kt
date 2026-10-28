package com.example.nexonote.ui

import com.example.nexonote.domain.model.AccentColor
import com.example.nexonote.domain.model.Entry
import com.example.nexonote.domain.model.EntryType
import com.example.nexonote.domain.model.ThemeMode
import com.example.nexonote.domain.repository.EntryRepository
import com.example.nexonote.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NexoViewModelTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun accentAndNameUpdatesAreExposedToTheTheme() = runTest {
        val model = NexoViewModel(FakeEntries(), FakeSettings())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect { } }
        runCurrent()
        assertFalse(model.uiState.value.loading)

        model.setAccentColor(AccentColor.TEAL)
        model.setDisplayName("Ana")
        runCurrent()
        assertEquals(AccentColor.TEAL, model.uiState.value.accentColor)
        assertEquals("Ana", model.uiState.value.displayName)
        assertFalse(model.uiState.value.saving)
        assertNull(model.uiState.value.error)
    }

    @Test fun failedWriteSurfacesErrorAndRetryClearsIt() = runTest {
        val settings = FakeSettings().apply { failWrites = true }
        val model = NexoViewModel(FakeEntries(), settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect { } }
        runCurrent()

        model.setAccentColor(AccentColor.GREEN)
        runCurrent()
        assertFalse(model.uiState.value.saving)
        assertTrue(model.uiState.value.error != null)
        assertEquals(AccentColor.PURPLE, model.uiState.value.accentColor)

        settings.failWrites = false
        model.setAccentColor(AccentColor.GREEN)
        runCurrent()
        assertNull(model.uiState.value.error)
        assertEquals(AccentColor.GREEN, model.uiState.value.accentColor)
    }

    private class FakeEntries : EntryRepository {
        override val entries = MutableStateFlow<List<Entry>>(emptyList())
        override suspend fun create(title: String, type: EntryType) = Unit
        override suspend fun setTaskCompleted(id: Long, completed: Boolean) = Unit
    }

    private class FakeSettings : SettingsRepository {
        override val displayName = MutableStateFlow("")
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override val accentColor = MutableStateFlow(AccentColor.PURPLE)
        var failWrites = false

        override suspend fun setDisplayName(name: String) {
            if (failWrites) throw IOException("Write failed")
            displayName.value = name.trim()
        }

        override suspend fun setThemeMode(mode: ThemeMode) {
            if (failWrites) throw IOException("Write failed")
            themeMode.value = mode
        }

        override suspend fun setAccentColor(color: AccentColor) {
            if (failWrites) throw IOException("Write failed")
            accentColor.value = color
        }
    }
}
