package org.simple.sharedTestCode.util.room

import org.simple.clinic.util.room.DatabaseTransactionRunner

object TestDatabaseTransactionRunner : DatabaseTransactionRunner {

  override fun <T> invoke(block: () -> T): T {
    return block()
  }
}
