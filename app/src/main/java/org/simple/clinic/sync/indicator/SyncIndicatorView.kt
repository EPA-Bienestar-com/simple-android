package org.simple.clinic.sync.indicator

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding3.view.clicks
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import kotlinx.android.synthetic.main.sync_indicator.view.*
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.bindUiToController
import org.simple.clinic.main.TheActivity
import org.simple.clinic.mobius.MobiusDelegate
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
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.setCompoundDrawableStart
import javax.inject.Inject

class SyncIndicatorView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs), SyncIndicatorUi {

  @Inject
  lateinit var controller: SyncIndicatorViewController

  @Inject
  lateinit var activity: AppCompatActivity

  @Inject
  lateinit var effectHandlerFactory: SyncIndicatorEffectHandler.Factory

  private val events by unsafeLazy {
    Observable
        .merge(screenCreates(), viewClicks())
        .compose(ReportAnalyticsEvents())
        .share()
  }

  private val uiRenderer = SyncIndicatorUiRenderer(this)

  private val delegate: MobiusDelegate<SyncIndicatorModel, SyncIndicatorEvent, SyncIndicatorEffect> by unsafeLazy {
    MobiusDelegate.forView(
        events = events.ofType(),
        defaultModel = SyncIndicatorModel.create(),
        update = SyncIndicatorUpdate(),
        effectHandler = effectHandlerFactory.create(this).build(),
        init = SyncIndicatorInit(),
        modelUpdateListener = uiRenderer::render
    )
  }

  @SuppressLint("CheckResult")
  override fun onFinishInflate() {
    super.onFinishInflate()

    LayoutInflater.from(context).inflate(R.layout.sync_indicator, this, true)

    TheActivity.component.inject(this)

    bindUiToController(
        ui = this,
        events = events,
        controller = controller,
        screenDestroys = RxView.detaches(this).map { ScreenDestroyed() }
    )
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    delegate.start()
  }

  override fun onDetachedFromWindow() {
    delegate.stop()
    super.onDetachedFromWindow()
  }

  override fun onSaveInstanceState(): Parcelable? {
    return delegate.onSaveInstanceState(super.onSaveInstanceState())
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    super.onRestoreInstanceState(delegate.onRestoreInstanceState(state))
  }

  private fun screenCreates() = Observable.just(ScreenCreated())

  private fun viewClicks() = rootLayout.clicks().map { SyncIndicatorViewClicked }

  @SuppressLint("StringFormatMatches")
  override fun updateState(syncState: SyncIndicatorState) {
    val transition = AutoTransition().setInterpolator(FastOutSlowInInterpolator())
    TransitionManager.beginDelayedTransition(this, transition)

    rootLayout.isEnabled = true

    when (syncState) {
      ConnectToSync -> {
        statusTextView.text = context.getString(R.string.syncindicator_status_failed)
        statusTextView.setCompoundDrawableStart(R.drawable.ic_round_warning_16px)
      }
      is Synced -> {
        val durationToMinsAgo = syncState.durationSince.toMinutes().toInt()
        statusTextView.text = if (durationToMinsAgo == 0) {
          context.getString(R.string.syncindicator_status_synced_just_now)
        } else {
          context.getString(R.string.syncindicator_status_synced_min_ago, "$durationToMinsAgo")
        }
        statusTextView.setCompoundDrawableStart(R.drawable.ic_cloud_done_16dp)
      }
      SyncPending -> {
        statusTextView.text = context.getString(R.string.syncindicator_status_pending)
        statusTextView.setCompoundDrawableStart(R.drawable.ic_cloud_upload_16dp)
      }
      Syncing -> {
        statusTextView.text = context.getString(R.string.syncindicator_status_syncing)
        statusTextView.setCompoundDrawableStart(R.drawable.ic_round_sync_16px)
        rootLayout.isEnabled = false
      }
    }
  }

  override fun showErrorDialog(errorType: ResolvedError) {
    val message = when (errorType) {
      is NetworkRelated -> context.getString(R.string.syncindicator_dialog_error_network)
      // TODO(vs): 2019-10-31 Add a separate error message for server errors
      is Unexpected, is Unauthenticated, is ServerError -> context.getString(R.string.syncindicator_dialog_error_server)
    }
    SyncIndicatorFailureDialog.show(fragmentManager = activity.supportFragmentManager, message = message)
  }
}
