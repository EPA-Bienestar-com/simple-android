package org.simple.clinic.analytics

import android.content.Context
import com.heapanalytics.android.Heap
import org.simple.clinic.BuildConfig

class HeapReporter(context: Context, debug: Boolean = false) : Reporter {

  init {
    Heap.init(context.applicationContext, BuildConfig.HEAP_ID, debug)
  }

  override fun setUserIdentity(id: String) {
    Heap.identify(id)
  }

  override fun createEvent(event: String, props: Map<String, Any>) {
    Heap.track(event, props.mapValues { (_, value) -> value.toString() })
  }

  override fun setProperty(key: String, value: Any) {
    Heap.addUserProperties(mapOf(key to value.toString()))
  }
}
