package org.simple.clinic.widgets

import android.content.Context
import androidx.appcompat.widget.AppCompatCheckBox
import android.util.AttributeSet

class CheckboxWithSuppressibleListener(context: Context, attrs: AttributeSet) : AppCompatCheckBox(context, attrs) {

  private var listener: OnCheckedChangeListener? = null

  override fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
    super.setOnCheckedChangeListener(listener)
    this.listener = listener
  }

  fun runWithoutListener(block: () -> Unit) {
    val copy = listener
    setOnCheckedChangeListener(null)

    block()
    setOnCheckedChangeListener(copy)
  }
}
