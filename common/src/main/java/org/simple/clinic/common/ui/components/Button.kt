package org.simple.clinic.common.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonElevation
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.simple.clinic.common.R
import org.simple.clinic.common.ui.components.ButtonSize.Big
import org.simple.clinic.common.ui.components.ButtonSize.Normal
import org.simple.clinic.common.ui.components.ButtonSize.Small
import org.simple.clinic.common.ui.theme.SimpleGreenTheme
import org.simple.clinic.common.ui.theme.SimpleInverseTheme
import org.simple.clinic.common.ui.theme.SimpleRedTheme
import org.simple.clinic.common.ui.theme.SimpleTheme

@Composable
fun Button(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  @DrawableRes iconRes: Int? = null,
  enabled: Boolean = true,
  buttonSize: ButtonSize = Normal
) {
  Button(
    text = text,
    onClick = onClick,
    modifier = modifier,
    iconRes = iconRes,
    enabled = enabled,
    buttonSize = buttonSize,
    colors = androidx.compose.material.ButtonDefaults.buttonColors()
  )
}

@Composable
fun TextButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  @DrawableRes iconRes: Int? = null,
  enabled: Boolean = true,
  buttonSize: ButtonSize = Normal
) {
  Button(
    text = text,
    onClick = onClick,
    modifier = modifier,
    iconRes = iconRes,
    enabled = enabled,
    buttonSize = buttonSize,
    colors = androidx.compose.material.ButtonDefaults.textButtonColors(),
    elevation = null
  )
}

@Composable
fun OutlinedButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  @DrawableRes iconRes: Int? = null,
  enabled: Boolean = true,
  buttonSize: ButtonSize = Normal
) {
  Button(
    text = text,
    onClick = onClick,
    modifier = modifier,
    iconRes = iconRes,
    enabled = enabled,
    buttonSize = buttonSize,
    colors =
      androidx.compose.material.ButtonDefaults.outlinedButtonColors(
        backgroundColor = SimpleTheme.colors.material.surface
      ),
    elevation = null,
    border = BorderStroke(width = 1.dp, color = SimpleTheme.colors.material.primary)
  )
}

@Composable
private fun Button(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  @DrawableRes iconRes: Int? = null,
  enabled: Boolean = true,
  buttonSize: ButtonSize = Normal,
  colors: ButtonColors = androidx.compose.material.ButtonDefaults.buttonColors(),
  elevation: ButtonElevation? = androidx.compose.material.ButtonDefaults.elevation(),
  border: BorderStroke? = null
) {
  val height =
    when (buttonSize) {
      Small -> ButtonDefaults.SmallHeight
      Normal -> ButtonDefaults.NormalHeight
      Big -> ButtonDefaults.BigHeight
    }

  val textStyle =
    when (buttonSize) {
      Normal,
      Big -> SimpleTheme.typography.buttonBig
      Small -> SimpleTheme.typography.material.button
    }

  androidx.compose.material.Button(
    onClick = onClick,
    shape = SimpleTheme.shapes.small,
    enabled = enabled,
    colors = colors,
    elevation = elevation,
    border = border,
    modifier = modifier.requiredHeight(height)
  ) {
    if (iconRes != null) {
      Icon(painter = painterResource(iconRes), contentDescription = null)
      Spacer(Modifier.requiredWidth(8.dp))
    }
    Text(text = text.uppercase(), style = textStyle)
  }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun ButtonPreview() {
  SimpleTheme {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Button(
        text = "click me!",
        iconRes = R.drawable.ic_location_20dp,
        onClick = {
          // Handle clicks
        },
        modifier = Modifier.padding(16.dp),
        buttonSize = Small
      )

      Button(
        text = "click me!",
        iconRes = R.drawable.ic_location_20dp,
        onClick = {
          // Handle clicks
        },
        modifier = Modifier.padding(16.dp)
      )

      Button(
        text = "click me!",
        iconRes = R.drawable.ic_location_20dp,
        onClick = {
          // Handle clicks
        },
        modifier = Modifier.padding(16.dp),
        buttonSize = Big
      )

      SimpleInverseTheme {
        Button(
          text = "click me!",
          iconRes = R.drawable.ic_location_20dp,
          onClick = {
            // Handle clicks
          },
          modifier = Modifier.padding(16.dp)
        )
      }
    }
  }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun OutlinedButtonPreview() {
  SimpleTheme {
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedButton(
        text = "click me!",
        iconRes = R.drawable.ic_location_20dp,
        onClick = {
          // Handle clicks
        },
        modifier = Modifier.padding(16.dp),
        buttonSize = Small
      )

      OutlinedButton(
        text = "click me!",
        iconRes = R.drawable.ic_location_20dp,
        onClick = {
          // Handle clicks
        },
        modifier = Modifier.padding(16.dp)
      )

      OutlinedButton(
        text = "click me!",
        iconRes = R.drawable.ic_location_20dp,
        onClick = {
          // Handle clicks
        },
        modifier = Modifier.padding(16.dp),
        buttonSize = Big
      )

      SimpleGreenTheme {
        OutlinedButton(
          text = "click me!",
          iconRes = R.drawable.ic_location_20dp,
          onClick = {
            // Handle clicks
          },
          modifier = Modifier.padding(16.dp)
        )
      }
    }
  }
}

@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun TextButtonPreview() {
  SimpleTheme {
    Row(verticalAlignment = Alignment.CenterVertically) {
      TextButton(
        text = "click me!",
        iconRes = R.drawable.ic_location_20dp,
        onClick = {
          // Handle clicks
        },
        modifier = Modifier.padding(16.dp),
        buttonSize = Small
      )

      TextButton(
        text = "click me!",
        iconRes = R.drawable.ic_location_20dp,
        onClick = {
          // Handle clicks
        },
        modifier = Modifier.padding(16.dp)
      )

      TextButton(
        text = "click me!",
        iconRes = R.drawable.ic_location_20dp,
        onClick = {
          // Handle clicks
        },
        modifier = Modifier.padding(16.dp),
        buttonSize = Big
      )

      SimpleRedTheme {
        TextButton(
          text = "click me!",
          iconRes = R.drawable.ic_location_20dp,
          onClick = {
            // Handle clicks
          },
          modifier = Modifier.padding(16.dp)
        )
      }
    }
  }
}

private object ButtonDefaults {
  val SmallHeight = 32.dp
  val NormalHeight = 48.dp
  val BigHeight = 56.dp
}

enum class ButtonSize {
  Small,
  Normal,
  Big
}
