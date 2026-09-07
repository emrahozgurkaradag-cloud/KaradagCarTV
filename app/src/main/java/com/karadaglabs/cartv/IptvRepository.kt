package com.karadaglabs.cartv

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object IptvRepository {

    private const val TAG = "IptvRepository"
    const val PREFS_NAME = "karadag_car_tv_cache"
    const val CHANNELS_KEY = "iptv_channels"
    const val LAST_UPDATE_KEY = "iptv_last_update"
    const val LAST_CHANNEL_ID_KEY = "projection_last_channel_id"

    fun loadCached(context: Context): List<IptvChannel> {
        return try {
            val json = AppSession.prefs(context)
                .getString(CHANNELS_KEY, null)

            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                IptvChannels.parse(JSONArray(json))
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to read IPTV cache", exception)
            emptyList()
        }
    }

    fun lastUpdate(context: Context): String {
        return AppSession.prefs(context)
            .getString(LAST_UPDATE_KEY, "")
            .orEmpty()
    }

    fun lastSelectedChannelId(context: Context): Int? {
        val prefs = AppSession.prefs(context)

        if (!prefs.contains(LAST_CHANNEL_ID_KEY)) {
            return null
        }

        val value = prefs.getInt(LAST_CHANNEL_ID_KEY, -1)
        return value.takeIf { it >= 0 }
    }

    fun saveLastSelectedChannelId(
        context: Context,
        channelId: Int
    ) {
        AppSession.prefs(context)
            .edit()
            .putInt(LAST_CHANNEL_ID_KEY, channelId)
            .apply()
    }

    fun save(
        context: Context,
        channels: List<IptvChannel>
    ) {
        val updateTime = SimpleDateFormat(
            "dd.MM.yyyy HH:mm",
            Locale.forLanguageTag("tr-TR")
        ).format(Date())

        AppSession.prefs(context)
            .edit()
            .putString(CHANNELS_KEY, IptvChannels.toJson(channels).toString())
            .putString(LAST_UPDATE_KEY, updateTime)
            .apply()
    }

    fun fetchFromApi(context: Context): List<IptvChannel> {
        val licenseKey = AppSession.licenseKey(context)

        if (licenseKey.isBlank()) {
            throw IllegalStateException(
                "Cihaz aktive edilmemiş: license_key boş"
            )
        }

        val connection = URL("${AppSession.API_BASE_URL}/api/iptv")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            val body = JSONObject().apply {
                put("device_uuid", AppSession.deviceUuid(context))
                put("license_key", licenseKey)
            }

            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: ""
            }

            if (responseCode !in 200..299) {
                throw IllegalStateException(
                    try {
                        JSONObject(responseText).optString(
                            "message",
                            "IPTV API hatası: $responseCode"
                        )
                    } catch (_: Exception) {
                        "IPTV API hatası: $responseCode"
                    }
                )
            }

            val response = JSONObject(responseText)

            if (!response.optBoolean("licensed", false)) {
                throw IllegalStateException(
                    response.optString("message", "IPTV erişimi yok.")
                )
            }

            val channels = IptvChannels.parse(
                response.optJSONArray("channels")
            )

            if (channels.isEmpty()) {
                throw IllegalStateException("IPTV kanal listesi boş.")
            }

            return channels
        } finally {
            connection.disconnect()
        }
    }

    fun loadFromApiWithCacheFallback(
        context: Context,
        maxAttempts: Int = 4
    ): List<IptvChannel> {
        for (attempt in 1..maxAttempts) {
            try {
                val channels = fetchFromApi(context)

                if (channels.isNotEmpty()) {
                    save(context, channels)
                    return channels
                }
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "IPTV API başarısız (attempt $attempt)",
                    exception
                )
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(attempt * 2_500L)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        return loadCached(context)
    }
}
