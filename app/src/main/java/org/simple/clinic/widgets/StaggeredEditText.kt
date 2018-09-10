package org.simple.clinic.widgets

import android.content.Context
import android.util.AttributeSet
import com.alimuzaffar.lib.pin.PinEntryEditText
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.Observable

/**
 * This class exists because a lot of libraries do not extend EditText.
 * If we ever decide to swap libraries, the usages shouldn't be affected.
 */
class StaggeredEditText(context: Context, attrs: AttributeSet) : PinEntryEditText(context, attrs) {

  init {
    // Library recommends doing this.
    isCursorVisible = false
    setTextIsSelectable(false)
  }

  fun textChanges(): Observable<CharSequence> {
    return RxTextView.textChanges(this)
  }
}
