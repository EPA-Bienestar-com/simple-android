package org.simple.clinic.analytics

import timber.log.Timber

interface Reporter {

  fun setUserIdentity(id: String)

  fun createEvent(event: String, props: Map<String, Any>)

  fun setProperty(key: String, value: Any)

  /**
   * Safely report events so that the app does not crash if any of the
   * reporters fail.
   **/
  fun safely(message: String = "", block: Reporter.() -> Unit) {
    try {
      block()
    } catch (e: Exception) {
      Timber.e(e, if (message.isBlank()) "Could not report event!" else message)
    }
  }
}
