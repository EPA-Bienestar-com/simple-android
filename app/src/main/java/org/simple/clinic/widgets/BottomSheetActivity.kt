package org.simple.clinic.widgets

import android.content.Context
import android.os.Bundle
import android.support.v4.view.animation.FastOutSlowInInterpolator
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import kotterknife.bindView
import org.simple.clinic.BuildConfig
import org.simple.clinic.R

/**
 * We're using Activities as fake bottom sheets instead of BottomSheetDialog because we want
 * our layout to be entirely anchored on top of the keyboard. With BottomSheetDialog, the
 * keyboard otherwise aligns itself just below text fields, overlapping everything else
 * present below them.
 *
 * TODO: BottomSheet behavior.
 */
abstract class BottomSheetActivity : AppCompatActivity() {

  private val backgroundView by bindView<View>(R.id.bottomsheet_background)
  private val contentContainer by bindView<ViewGroup>(R.id.bottomsheet_content_container)

  override fun onCreate(savedInstanceState: Bundle?) {
    overridePendingTransition(0, 0)
    super.onCreate(savedInstanceState)
    @Suppress("ConstantConditionIf")
    if (BuildConfig.DISABLE_SCREENSHOT) {
      window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }
    super.setContentView(R.layout.bottom_sheet)

    contentContainer.setOnClickListener {
      // Swallow clicks to avoid dismissing the sheet accidentally.
    }

    backgroundView.setOnClickListener {
      onBackgroundClick()
    }

    backgroundView.alpha = 0f
    backgroundView.animate()
        .alpha(1f)
        .setDuration(200)
        .setInterpolator(FastOutSlowInInterpolator())
        .start()

    contentContainer.executeOnNextMeasure {
      contentContainer.translationY = contentContainer.height.toFloat()

      contentContainer.animate()
          .translationY(0f)
          .setDuration(250)
          .setInterpolator(FastOutSlowInInterpolator())
          .start()
    }
  }

  override fun attachBaseContext(baseContext: Context) {
    super.attachBaseContext(ViewPumpContextWrapper.wrap(baseContext))
  }

  override fun finish() {
    contentContainer.animate()
        .translationY(contentContainer.height.toFloat())
        .setDuration(250)
        .setInterpolator(FastOutSlowInInterpolator())
        .start()

    backgroundView.animate()
        .alpha(0f)
        .setDuration(100)
        .setInterpolator(FastOutSlowInInterpolator())
        .withEndAction {
          super.finish()
          overridePendingTransition(0, 0)
        }
        .start()
  }

  override fun setContentView(layoutResId: Int) {
    LayoutInflater.from(this).inflate(layoutResId, contentContainer)
  }

  override fun setContentView(view: View) {
    contentContainer.addView(view)
  }

  override fun setContentView(view: View, params: ViewGroup.LayoutParams?) {
    contentContainer.addView(view, params)
  }

  open fun onBackgroundClick() {
    onBackPressed()
  }

  override fun onBackPressed() {
    // Routing to finish() just so that the exit animation can be played.
    finish()
  }
}
