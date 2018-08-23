package org.simple.clinic.analytics

object Analytics {

  private var reporters: List<Reporter> = emptyList()

  fun addReporter(vararg reportersToAdd: Reporter) {
    reporters += reportersToAdd
  }

  fun clearReporters() {
    reporters = emptyList()
  }

  fun removeReporter(reporter: Reporter) {
    reporters -= reporter
  }

  fun reportInteraction(name: String) {
    reporters.forEach { it.safeReport("Error reporting interaction!") { createEvent(name, emptyMap()) } }
  }
}
