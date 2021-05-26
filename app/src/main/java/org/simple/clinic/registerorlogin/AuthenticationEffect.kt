package org.simple.clinic.registerorlogin

sealed class AuthenticationEffect

object OpenCountrySelectionScreen : AuthenticationEffect()

object OpenRegistrationPhoneScreen : AuthenticationEffect()
