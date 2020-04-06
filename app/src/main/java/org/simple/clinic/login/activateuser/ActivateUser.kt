package org.simple.clinic.login.activateuser

import org.simple.clinic.login.LoginApi
import org.simple.clinic.login.LoginOtpSmsListener
import org.simple.clinic.login.activateuser.ActivateUser.Result.*
import org.simple.clinic.user.LoggedInUserPayload
import org.simple.clinic.util.ErrorResolver
import org.simple.clinic.util.ResolvedError.NetworkRelated
import java.util.UUID
import javax.inject.Inject

class ActivateUser @Inject constructor(
    private val loginApi: LoginApi,
    private val loginOtpSmsListener: LoginOtpSmsListener
) {

  fun activate(userUuid: UUID, pin: String): Result {
    return try {
      loginOtpSmsListener.listenForLoginOtp()
      makeUserActivateCall(userUuid, pin)
    } catch (e: Throwable) {
      when (ErrorResolver.resolve(e)) {
        is NetworkRelated -> NetworkError
        else -> OtherError(e)
      }
    }
  }

  private fun makeUserActivateCall(userUuid: UUID, pin: String): Result {
    val response = loginApi.activate(ActivateUserRequest.create(userUuid, pin)).execute()
    return when (response.code()) {
      200 -> Success(response.body()!!.user)
      401 -> IncorrectPin
      else -> ServerError(response.code())
    }
  }

  sealed class Result {

    data class Success(val userPayload: LoggedInUserPayload) : Result()

    object NetworkError : Result()

    object IncorrectPin : Result()

    data class ServerError(val responseCode: Int) : Result()

    data class OtherError(val cause: Throwable) : Result()
  }
}
