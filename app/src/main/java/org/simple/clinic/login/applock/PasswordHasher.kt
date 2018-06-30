package org.simple.clinic.login.applock

import io.reactivex.Single

interface PasswordHasher {

  enum class ComparisonResult {
    SAME,
    DIFFERENT
  }

  fun hash(password: String): Single<String>

  fun compare(hashed: String, password: String): Single<ComparisonResult>
}
