package org.simple.clinic.summary.bloodpressures

import org.simple.clinic.bp.BloodPressureMeasurement

interface NewBloodPressureSummaryViewUi {
  fun showNoBloodPressuresView()
  fun showBloodPressures(bloodPressures: List<BloodPressureMeasurement>)
  fun showSeeAllButton()
  fun hideSeeAllButton()
}
