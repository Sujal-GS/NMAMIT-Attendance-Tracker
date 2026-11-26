package com.nmamit.attendance.data.remote

import com.nmamit.attendance.BuildConfig
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

data class PortalClientBundle(
    val apiService: AttendanceApiService,
    val cookieJar: PortalCookieJar,
)

object NetworkClient {

    const val PORTAL_BASE_URL = "https://studentportal.universitysolutions.in/"

    /**
     * Orders IPv4 addresses ahead of IPv6 so dual-stack Android Wi-Fi / emulator NATs
     * do not stall for 15-21s on IPv6 SYN timeouts before falling back to IPv4.
     */
    private val ipv4FirstDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            val (ipv4, other) = addresses.partition { it is Inet4Address }
            val ordered = ArrayList<InetAddress>(addresses.size)
            ordered.addAll(ipv4)
            ordered.addAll(other)
            return ordered
        }
    }

    fun createPortalClient(): PortalClientBundle {
        val cookieJar = PortalCookieJar()

        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .dns(ipv4FirstDns)
            .addInterceptor(AuthInterceptor())
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Cookie")
                redactHeader("Set-Cookie")
                redactHeader("Authorization")
            }
            builder.addInterceptor(loggingInterceptor)
        }

        val okHttpClient = builder.build()

        val apiService = Retrofit.Builder()
            .baseUrl(PORTAL_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AttendanceApiService::class.java)

        return PortalClientBundle(apiService = apiService, cookieJar = cookieJar)
    }
}
