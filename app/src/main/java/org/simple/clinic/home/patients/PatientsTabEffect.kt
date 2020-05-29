package org.simple.clinic.home.patients

sealed class PatientsTabEffect

object OpenEnterOtpScreen : PatientsTabEffect()

object OpenPatientSearchScreen : PatientsTabEffect()

object RefreshUserDetails : PatientsTabEffect()

object LoadUser : PatientsTabEffect()

object LoadInfoForShowingApprovalStatus : PatientsTabEffect()

object ShowUserAwaitingApproval : PatientsTabEffect()

data class SetDismissedApprovalStatus(val dismissedStatus: Boolean) : PatientsTabEffect()

object ShowUserWasApproved: PatientsTabEffect()

object ShowUserPendingSmsVerification: PatientsTabEffect()

object HideUserAccountStatus: PatientsTabEffect()

object OpenScanBpPassportScreen: PatientsTabEffect()

object LoadNumberOfPatientsRegistered: PatientsTabEffect()

object OpenTrainingVideo: PatientsTabEffect()

object LoadInfoForShowingAppUpdateMessage: PatientsTabEffect()

object TouchAppUpdateShownAtTime: PatientsTabEffect()

object ShowAppUpdateAvailable: PatientsTabEffect()
