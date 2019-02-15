package org.simple.clinic.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.simple.clinic.di.NetworkModule
import org.simple.clinic.util.Just
import org.threeten.bp.Instant

class LastSyncedStateTest {

  /**
   * If you're seeing this test fail, it means that you modified
   * [LastSyncedState] without thinking about migration.
   *
   * The SharedPreferences key for [LastSyncedState] is
   * versioned, so the migration can potentially happen when it's
   * being read from SharedPreferences.
   */

  @Test
  fun `fail when migration is required`() {
    val moshi = NetworkModule().moshi()
    val adapter = moshi.adapter(LastSyncedState::class.java)

    val expectedJson = """
      {
        "lastSyncProgress": "SUCCESS",
        "lastSyncSuccessTimestamp" : "1970-01-01T00:00:00Z"
      }
    """
    val deserialized = adapter.fromJson(expectedJson)
    assertThat(deserialized).isEqualTo(LastSyncedState(
        lastSyncProgress = Just(SyncProgress.SUCCESS),
        lastSyncSuccessTimestamp = Just(Instant.EPOCH)
    ))
  }
}
