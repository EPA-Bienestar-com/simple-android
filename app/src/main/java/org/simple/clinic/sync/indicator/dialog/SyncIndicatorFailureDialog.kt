package org.simple.clinic.sync.indicator.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.FragmentManager
import org.simple.clinic.R

class SyncIndicatorFailureDialog : AppCompatDialogFragment() {

  companion object {
    private const val KEY_MESSAGE = "failure_message"
    private const val FRAGMENT_TAG = "sync_indicator_failure_dialog"

    fun show(fragmentManager: FragmentManager, message: String) {
      val existingFragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG)

      if (existingFragment != null) {
        fragmentManager
            .beginTransaction()
            .remove(existingFragment)
            .commitNowAllowingStateLoss()
      }

      val fragment = SyncIndicatorFailureDialog().apply {
        arguments = Bundle(1).apply {
          putString(KEY_MESSAGE, message)
        }
      }

      fragmentManager
          .beginTransaction()
          .add(fragment, FRAGMENT_TAG)
          .commitNowAllowingStateLoss()
    }
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val message = arguments!!.getString(KEY_MESSAGE)

    return AlertDialog.Builder(requireContext(), R.style.Clinic_V2_DialogStyle)
        .setMessage(message)
        .setPositiveButton(R.string.syncindicator_dialog_button_text, null)
        .create()
  }
}
