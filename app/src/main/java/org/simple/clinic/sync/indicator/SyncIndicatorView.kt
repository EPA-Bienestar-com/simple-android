package org.simple.clinic.sync.indicator

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.Observable
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.bindUiToController
import org.simple.clinic.main.TheActivity
import org.simple.clinic.sync.indicator.SyncIndicatorState.ConnectToSync
import org.simple.clinic.sync.indicator.SyncIndicatorState.SyncPending
import org.simple.clinic.sync.indicator.SyncIndicatorState.Synced
import org.simple.clinic.sync.indicator.SyncIndicatorState.Syncing
import org.simple.clinic.sync.indicator.dialog.SyncIndicatorFailureDialog
import org.simple.clinic.util.ResolvedError
import org.simple.clinic.util.ResolvedError.NetworkRelated
import org.simple.clinic.util.ResolvedError.ServerError
import org.simple.clinic.util.ResolvedError.Unauthenticated
import org.simple.clinic.util.ResolvedError.Unexpected
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.setCompoundDrawableStart
import javax.inject.Inject

class SyncIndicatorView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

  private val syncStatusTextView by bindView<TextView>(R.id.sync_indicator_status_text)
  private val syncIndicatorLayout by bindView<LinearLayout>(R.id.sync_indicator_root_layout)

  @Inject
  lateinit var controller: SyncIndicatorViewController

  @Inject
  lateinit var activity: AppCompatActivity

  @SuppressLint("CheckResult")
  override fun onFinishInflate() {
    super.onFinishInflate()

    LayoutInflater.from(context).inflate(R.layout.sync_indicator, this, true)

    TheActivity.component.inject(this)

    bindUiToController(
        ui = this,
        events = Observable.merge(screenCreates(), viewClicks()),
        controller = controller,
        screenDestroys = RxView.detaches(this).map { ScreenDestroyed() }
    )
  }

  private fun screenCreates() = Observable.just(SyncIndicatorViewCreated)

  private fun viewClicks() = RxView.clicks(syncIndicatorLayout).map { SyncIndicatorViewClicked }

  fun updateState(syncState: SyncIndicatorState) {
    val transition = AutoTransition().setInterpolator(FastOutSlowInInterpolator())
    TransitionManager.beginDelayedTransition(this, transition)

    syncIndicatorLayout.isEnabled = true

    when (syncState) {
      ConnectToSync -> {
        syncStatusTextView.text = context.getString(R.string.syncindicator_status_failed)
        syncStatusTextView.setCompoundDrawableStart(R.drawable.ic_round_warning_16px)
      }
      is Synced -> {
        val durationToMinsAgo = syncState.durationSince.toMinutes().toInt()
        syncStatusTextView.text = if (durationToMinsAgo == 0) {
          context.getString(R.string.syncindicator_status_synced_just_now)
        } else {
          context.getString(R.string.syncindicator_status_synced, durationToMinsAgo)
        }
        syncStatusTextView.setCompoundDrawableStart(R.drawable.ic_cloud_done_16dp)
      }
      SyncPending -> {
        syncStatusTextView.text = context.getString(R.string.syncindicator_status_pending)
        syncStatusTextView.setCompoundDrawableStart(R.drawable.ic_cloud_upload_16dp)
      }
      Syncing -> {
        syncStatusTextView.text = context.getString(R.string.syncindicator_status_syncing)
        syncStatusTextView.setCompoundDrawableStart(R.drawable.ic_round_sync_16px)
        syncIndicatorLayout.isEnabled = false
      }
    }
  }

  fun showErrorDialog(errorType: ResolvedError) {
    val message = when (errorType) {
      is NetworkRelated -> context.getString(R.string.syncindicator_dialog_error_network)
      // TODO(vs): 2019-10-31 Add a separate error message for server errors
      is Unexpected, is Unauthenticated, is ServerError -> context.getString(R.string.syncindicator_dialog_error_server)
    }
    SyncIndicatorFailureDialog.show(fragmentManager = activity.supportFragmentManager, message = message)
  }
}
