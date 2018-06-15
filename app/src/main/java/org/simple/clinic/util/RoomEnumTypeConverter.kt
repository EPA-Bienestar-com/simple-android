package org.simple.clinic.util

import android.arch.persistence.room.TypeConverter

abstract class RoomEnumTypeConverter<T : Enum<T>>(private val enumClass: Class<T>) {

  @TypeConverter
  fun toEnum(serialized: String?): T? {
    if (serialized.isNullOrEmpty()) {
      return null
    }
    return java.lang.Enum.valueOf(asEnumClass<T>(enumClass), serialized)
  }

  @TypeConverter
  fun fromEnum(value: T?): String? {
    return value?.name
  }

  /* This weirdness https://discuss.kotlinlang.org/t/confusing-type-error/506/8 */
  @Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
  private inline fun <T : Enum<T>> asEnumClass(clazz: Class<*>): Class<T> = clazz as Class<T>
}
