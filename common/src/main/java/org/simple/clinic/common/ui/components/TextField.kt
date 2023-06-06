package org.simple.clinic.common.ui.components

import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
  androidx.compose.material.TextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.requiredHeight(56.dp)
  )
}
