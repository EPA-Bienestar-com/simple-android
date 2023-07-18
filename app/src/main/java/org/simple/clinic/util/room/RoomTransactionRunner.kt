package org.simple.clinic.util.room

import org.simple.clinic.AppDatabase
import org.simple.clinic.di.AppScope
import javax.inject.Inject

interface DatabaseTransactionRunner {
  operator fun <T> invoke(block: () -> T): T
}

@AppScope
class RoomTransactionRunner @Inject constructor(
    private val database: AppDatabase
) : DatabaseTransactionRunner {

  override fun <T> invoke(block: () -> T): T {
    return database.runInTransaction(block)
  }
}
