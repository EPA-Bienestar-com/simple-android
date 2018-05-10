package org.resolvetosavelives.red.newentry.mobile

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.RelativeLayout
import kotterknife.bindView
import org.resolvetosavelives.red.R
import org.resolvetosavelives.red.TheActivity
import org.resolvetosavelives.red.newentry.personal.PatientPersonalDetailsEntryScreen

class PatientMobileEntryScreen(context: Context, attrs: AttributeSet) : RelativeLayout(context, attrs) {

  companion object {
    val KEY = PatientMobileEntryScreenKey()
  }

  private val newPatientButton: Button by bindView(R.id.home_new_patient)

  override fun onFinishInflate() {
    super.onFinishInflate()

    newPatientButton.setOnClickListener({
      TheActivity.screenRouter().push(PatientPersonalDetailsEntryScreen.KEY)
    })
  }
}
