package org.simple.clinic.sync

interface DataPullResponse<T> {

  val payloads: List<T>

  val processedSinceTimestamp: String
}
