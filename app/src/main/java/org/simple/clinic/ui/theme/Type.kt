package org.simple.clinic.ui.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography.h5Numeric
  get() = h5.copy(letterSpacing = 0.0625.sp)

val Typography.h6Numeric
  get() = h6.copy(letterSpacing = 0.05.sp)

val Typography.subtitle1Medium
  get() = subtitle1.copy(fontWeight = FontWeight.Medium)

val Typography.body0
  get() = TextStyle(
      fontWeight = FontWeight.Normal,
      fontSize = 18.sp,
      letterSpacing = 0.011.sp,
      lineHeight = 28.sp
  )

val Typography.body0Medium
  get() = body0.copy(
      letterSpacing = 0.0083.sp,
      fontWeight = FontWeight.Medium
  )

val Typography.body0Numeric
  get() = body0.copy(letterSpacing = 0.1111.sp)

val Typography.body1Numeric
  get() = body1.copy(letterSpacing = 0.0937.sp)

val Typography.body2Numeric
  get() = body2.copy(letterSpacing = 0.107.sp)

val Typography.body2Bold
  get() = body2.copy(fontWeight = FontWeight.Bold)

val Typography.buttonBig
  get() = button.copy(
      fontSize = 16.sp,
      letterSpacing = 0.0781.sp,
      lineHeight = 20.sp
  )

val Typography.tag
  get() = TextStyle(
      fontWeight = FontWeight.Medium,
      fontSize = 14.sp,
      letterSpacing = 0.0571.sp,
      lineHeight = 20.sp
  )
