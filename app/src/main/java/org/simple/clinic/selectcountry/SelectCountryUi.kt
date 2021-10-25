package org.simple.clinic.selectcountry

import org.simple.clinic.appconfig.Country

interface SelectCountryUi {
  fun showProgress()
  fun displaySupportedCountries()
  fun displayNetworkErrorMessage()
  fun displayServerErrorMessage()
  fun displayGenericErrorMessage()
}
