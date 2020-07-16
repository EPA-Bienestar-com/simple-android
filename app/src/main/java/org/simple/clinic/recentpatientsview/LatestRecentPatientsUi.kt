package org.simple.clinic.recentpatientsview

interface LatestRecentPatientsUi : LatestRecentPatientsUiActions {
  fun updateRecentPatients(recentPatients: List<RecentPatientItemType>)
  fun showOrHideRecentPatients(isVisible: Boolean)
}
