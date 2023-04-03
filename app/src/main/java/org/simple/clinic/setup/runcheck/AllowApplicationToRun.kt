package org.simple.clinic.setup.runcheck

import android.app.Application
import android.os.AsyncTask
import androidx.annotation.WorkerThread
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import com.scottyab.rootbeer.RootBeer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.simple.clinic.BuildConfig
import javax.inject.Inject
import kotlin.math.floor


class AllowApplicationToRun @Inject constructor(
    private val application: Application
) {

  private val rootBeer = RootBeer(application)

  @WorkerThread
  fun check(): AllowedToRun {
    test()
    val isDeviceRooted = rootBeer.isRooted
    val cannotRunOnRootedDevices = !BuildConfig.ALLOW_ROOTED_DEVICE

    return when {
      isDeviceRooted && cannotRunOnRootedDevices -> Disallowed(Disallowed.Reason.Rooted)
      else -> Allowed
    }
  }

  fun test() {
    val integrityManager =
        IntegrityManagerFactory.create(application)

    // Request the integrity token by providing a nonce.
    val integrityTokenResponse =
        integrityManager.requestIntegrityToken(
            IntegrityTokenRequest.builder()
                .setNonce(generateNonce())
                .setCloudProjectNumber(1080901647110)
                .build())
    integrityTokenResponse.addOnSuccessListener { integrityTokenResponse1: IntegrityTokenResponse ->
      val integrityToken = integrityTokenResponse1.token()
      GetTokenResponse().execute(integrityToken)
    }

    integrityTokenResponse.addOnFailureListener { e: Exception? ->
      val test = e
    }
  }

  private fun generateNonce(): String {
    val length = 50
    var nonce = ""
    val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    for (i in 0 until length) {
      nonce += allowed[floor(Math.random() * allowed.length).toInt()].toString()
    }
    return nonce
  }

  private class GetTokenResponse : AsyncTask<String?, Int?, Array<String?>?>() {
    private var hasError = false

    override fun doInBackground(vararg params: String?): Array<String?> {
      val client = OkHttpClient.Builder().build()
      val token = params[0]
      val request = Request.Builder()
          .get()
          .url("https://play-integrity-server-siddh1004.vercel.app//api/check?token=$token")
          .build()
      val response = client.newCall(request).execute()
      if (!response.isSuccessful) {
        hasError = true
        return arrayOf("Api request error", "Error code: " + response.code)
      }
      val responseBody = response.body
      if (responseBody == null) {
        hasError = true
        return arrayOf("Api request error", "Empty response")
      }
      val json = JSONObject(responseBody.string())
      if (json.has("error")) {
        hasError = true
        return arrayOf("Api request error", json.getString("error"))
      }
      if (!json.has("deviceIntegrity")) {
        hasError = true
        return arrayOf("Api request error", "Response does not contain deviceIntegrity")
      }
      val jsonResponse = json.toString(4)
      return arrayOf(json.getJSONObject("deviceIntegrity").toString())
    }

    override fun onPostExecute(result: Array<String?>?) {
      if (hasError) {
      } else {
      }
    }
  }
}
