package org.simple.clinic.home.patients

import android.app.Dialog
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import android.view.View
import org.simple.clinic.R

class LoggedOutOnOtherDeviceDialog : AppCompatDialogFragment() {

  companion object {
    fun show(fragmentManager: FragmentManager) {
      (fragmentManager.findFragmentByTag("logged_out_on_other_device_alert") as LoggedOutOnOtherDeviceDialog?)?.dismiss()

      val fragment = LoggedOutOnOtherDeviceDialog()
      // Cancellable on the dialog builder is ignored. We have to use this.
      fragment.isCancelable = false
      fragment.show(fragmentManager, "logged_out_on_other_device_alert")
    }
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    return AlertDialog.Builder(context!!)
        .setTitle(R.string.patients_loggedoutalert_title)
        .setMessage(R.string.patients_loggedoutalert_message)
        .setPositiveButton(R.string.patients_loggedoutalert_dismiss) { _, _ ->
          val view = dialog.ownerActivity.findViewById<View>(android.R.id.content)
          Snackbar.make(view, R.string.patients_you_are_now_logged_in, Snackbar.LENGTH_LONG).show()
        }
        .create()
  }
}
