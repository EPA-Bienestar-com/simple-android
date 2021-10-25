package org.simple.clinic.selectcountry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.material.composethemeadapter.MdcTheme
import org.simple.clinic.R
import org.simple.clinic.appconfig.Country
import org.simple.clinic.ui.theme.onSurface34

@Composable
fun CountriesListContainer(
    modifier: Modifier = Modifier,
    countries: List<Country>,
    chosenCountry: Country?,
    onCountrySelected: (Country) -> Unit
) {
  Column(modifier = modifier) {
    Text(
        text = stringResource(id = R.string.selectcountry_title),
        style = MaterialTheme.typography.h6,
        color = MaterialTheme.colors.onSurface,
        modifier = Modifier
            .padding(top = dimensionResource(id = R.dimen.spacing_24))
            .align(Alignment.CenterHorizontally)
    )

    CountriesList(
        modifier = Modifier.fillMaxWidth(),
        countries = countries,
        chosenCountry = chosenCountry,
        onCountrySelected = onCountrySelected
    )
  }
}

@Composable
private fun CountriesList(
    modifier: Modifier = Modifier,
    countries: List<Country>,
    chosenCountry: Country?,
    onCountrySelected: (Country) -> Unit
) {
  LazyColumn(
      modifier = modifier,
      contentPadding = PaddingValues(
          horizontal = dimensionResource(id = R.dimen.spacing_16),
          vertical = dimensionResource(id = R.dimen.spacing_24)
      )
  ) {
    itemsIndexed(countries) { index, country ->
      val isCountrySelected = country == chosenCountry

      CountryListItem(
          modifier = Modifier
              .fillMaxWidth()
              .clickable { onCountrySelected(country) },
          displayName = country.displayName,
          selected = isCountrySelected
      )

      if (index != countries.lastIndex) Divider()
    }
  }
}

@Composable
private fun CountryListItem(
    modifier: Modifier = Modifier,
    displayName: String,
    selected: Boolean
) {
  Row(
      modifier = modifier
          .then(Modifier
              .padding(
                  horizontal = dimensionResource(id = R.dimen.spacing_16),
                  vertical = dimensionResource(id = R.dimen.spacing_12)
              ))
  ) {
    RadioButton(
        selected = selected,
        colors = RadioButtonDefaults.colors(
            selectedColor = MaterialTheme.colors.primary,
            unselectedColor = MaterialTheme.colors.onSurface34
        ),
        onClick = null
    )
    Spacer(Modifier.width(dimensionResource(id = R.dimen.spacing_16)))
    Text(
        text = displayName,
        style = MaterialTheme.typography.body1,
        color = MaterialTheme.colors.onSurface
    )
  }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFFFFFFF
)
@Composable
private fun CountryListItemPreview() {
  MdcTheme {
    CountryListItem(
        modifier = Modifier.fillMaxWidth(),
        displayName = "India",
        selected = false
    )
  }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFFFFFFF
)
@Composable
private fun CountriesListContainerPreview() {
  val india = Country(
      isoCountryCode = Country.INDIA,
      displayName = "India",
      isdCode = "91",
      deployments = emptyList()
  )

  val bangladesh = Country(
      isoCountryCode = Country.BANGLADESH,
      displayName = "Bangladesh",
      isdCode = "880",
      deployments = emptyList()
  )

  var selectedCountry by remember { mutableStateOf(india) }

  MdcTheme {
    CountriesListContainer(
        countries = listOf(india, bangladesh),
        chosenCountry = selectedCountry
    ) { country ->
      selectedCountry = country
    }
  }
}
