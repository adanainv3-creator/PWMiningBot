package com.cloy

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.io.*
import java.net.*
import java.util.concurrent.*

/**
 * SocialFirst Auth (auth.sclfrst.com)
 * JWT issuer from PCAP: https://auth.sclfrst.com
 * TitleID: 11EF5C
 * Token expires in ~30min → auto-refresh every 25min
 */
class AuthManager(private val prefs: SharedPreferences) {

    companion object {
        const val TAG = "AuthMgr"
        const val TITLE_ID = "11EF5C"
        val ENDPOINTS = listOf(
            "https://auth.sclfrst.com/v1/users/login",
            "https://auth.sclfrst.com/v1/auth/email",
            "https://auth.sclfrst.com/v1/login",
            "https://api.sclfrst.com/v1/users/login"
        )
        const val REFRESH_MS = 25 * 60 * 1000L
    }

    interface AuthListener {
        fun onSuccess(coID: String, jwt: String, nick: String)
        fun onFailed(error: String)
        fun onRefreshed(jwt: String)
    }

    private val scheduler = ScheduledThreadPoolExecutor(1)
    private var refreshTask: ScheduledFuture<*>? = null
    private var currentJwt = prefs.getString("jwt", "") ?: ""
    private var email = prefs.getString("email", "") ?: ""
    private var password = prefs.getString("password", "") ?: ""

    fun saveCredentials(email: String, password: String) {
        this.email = email; this.password = password
        prefs.edit().putString("email", email).putString("password", password).apply()
    }

    fun saveJwt(jwt: String) {
        this.currentJwt = jwt
        prefs.edit().putString("jwt", jwt).apply()
    }

    fun hasSavedJwt() = currentJwt.isNotEmpty()
    fun hasSavedCredentials() = email.isNotEmpty() && password.isNotEmpty()
    fun getSavedJwt() = currentJwt
    fun getSavedCoID() = prefs.getString("coID", "") ?: ""
    fun getSavedNick() = prefs.getString("nick", "") ?: ""

    fun login(listener: AuthListener) = scheduler.execute { doLogin(listener) }

    fun stop() {
        refreshTask?.cancel(false)
        scheduler.shutdownNow()
    }

    private fun doLogin(listener: AuthListener) {
        if (email.isEmpty() || password.isEmpty()) {
            if (currentJwt.isNotEmpty()) {
                listener.onSuccess(getSavedCoID(), currentJwt, getSavedNick())
                return
            }
            listener.onFailed("No credentials saved")
            return
        }

        for (endpoint in ENDPOINTS) {
            try {
                Log.d(TAG, "Trying $endpoint")
                val body = JSONObject().apply {
                    put("email", email); put("password", password); put("titleId", TITLE_ID)
                }
                val resp = httpPost(endpoint, body)
                val jwt  = findJwt(resp) ?: continue
                if (!jwt.startsWith("eyJ")) continue

                val coID = findField(resp, "userId", "id", "sub", "playerId") ?: ""
                val nick = findField(resp, "nickname", "displayName", "username", "name")
                    ?: email.substringBefore("@")

                currentJwt = jwt
                prefs.edit().putString("jwt", jwt).putString("coID", coID).putString("nick", nick).apply()
                Log.d(TAG, "✅ Login OK: $nick")
                listener.onSuccess(coID, jwt, nick)
                scheduleRefresh(listener)
                return
            } catch (e: Exception) { Log.w(TAG, "$endpoint failed: ${e.message}") }
        }

        // All endpoints failed — use saved JWT as fallback
        if (currentJwt.isNotEmpty()) {
            Log.w(TAG, "All auth endpoints failed, using cached JWT")
            listener.onSuccess(getSavedCoID(), currentJwt, getSavedNick())
        } else {
            listener.onFailed("Auth failed — enter JWT manually in settings")
        }
    }

    private fun scheduleRefresh(listener: AuthListener) {
        refreshTask?.cancel(false)
        refreshTask = scheduler.scheduleAtFixedRate({
            doLogin(object : AuthListener {
                override fun onSuccess(coID: String, jwt: String, nick: String) = listener.onRefreshed(jwt)
                override fun onFailed(e: String) = Log.e(TAG, "Refresh failed: $e")
                override fun onRefreshed(jwt: String) {}
            })
        }, REFRESH_MS, REFRESH_MS, TimeUnit.MILLISECONDS)
    }

    private fun findJwt(obj: JSONObject): String? {
        listOf("token","jwt","accessToken","idToken","entityToken","PiYM","access_token").forEach { k ->
            val v = obj.optString(k, "")
            if (v.startsWith("eyJ")) return v
        }
        listOf("data","result","user","auth","payload").forEach { k ->
            if (obj.has(k)) runCatching { return findJwt(obj.getJSONObject(k)) }
        }
        return null
    }

    private fun findField(obj: JSONObject, vararg keys: String): String? {
        keys.forEach { k -> obj.optString(k, "").takeIf { it.isNotEmpty() && it != "null" }?.let { return it } }
        return null
    }

    private fun httpPost(urlStr: String, body: JSONObject): JSONObject {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "UnityPlayer/2022.3.20f1")
            doOutput = true; connectTimeout = 12000; readTimeout = 12000
        }
        conn.outputStream.write(body.toString().toByteArray())
        val code = conn.responseCode
        val stream = if (code < 400) conn.inputStream else conn.errorStream
        val resp = stream?.bufferedReader()?.readText() ?: "{}"
        Log.d(TAG, "POST $urlStr → $code: ${resp.take(150)}")
        return JSONObject(resp)
    }
}
