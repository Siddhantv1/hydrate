package com.example.waterreminder.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.waterreminder.domain.Gender
import com.example.waterreminder.domain.HydrateState
import com.example.waterreminder.domain.VolumeUnit
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HydrateStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun clockOn(day: Int) = Clock.fixed(ZonedDateTime.of(2026, 11, day, 9, 0, 0, 0, zone).toInstant(), zone)
    // In-memory DataStore: the real file-backed one fails its 2nd write on Windows (File.renameTo can't replace),
    // and the store under test only depends on the DataStore<Preferences> contract.
    private fun dataStore(@Suppress("UNUSED_PARAMETER") scope: CoroutineScope): DataStore<Preferences> =
        object : DataStore<Preferences> {
            private val flow = MutableStateFlow<Preferences>(emptyPreferences())
            private val lock = Mutex()
            override val data: Flow<Preferences> = flow
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                lock.withLock { transform(flow.value).also { flow.value = it } }
        }

    @Test fun roundTripsEveryField() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val ds = dataStore(scope)
        val full = HydrateState(true, 31, Gender.FEMALE, VolumeUnit.OZ, false, 11, 420, 1260, 4, "2026-11-03",
            6, "2026-11-02", "2026-11-03", 1793700000000L, true, true, true, "2026-10-30")
        HydrateStore(ds, { clockOn(3) }).update { full }
        assertEquals(full, HydrateStore(ds, { clockOn(3) }).state.first())
        scope.cancel()
    }

    @Test fun updateAppliesRolloverFirst() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val s = HydrateStore(dataStore(scope), { clockOn(4) })
        s.update { HydrateState(onboarded = true, glassesToday = 5, date = "2026-11-03", goalMetDate = "2026-11-03", streak = 2) }
        val (before, after) = s.update { it }
        assertEquals("2026-11-04", before.date); assertEquals(0, after.glassesToday); assertEquals(2, after.streak)
        scope.cancel()
    }
}
