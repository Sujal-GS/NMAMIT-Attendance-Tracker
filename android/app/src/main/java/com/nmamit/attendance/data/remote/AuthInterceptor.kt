package com.nmamit.attendance.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Injects browser-compatible XHR headers strictly for requests targeting
 * `https://studentportal.universitysolutions.in`.
 */
class AuthInterceptor : Interceptor {

    companion object {
        private const val PORTAL_ORIGIN = "https://studentportal.universitysolutions.in"
        private const val PORTAL_REFERER = "https://studentportal.universitysolutions.in/index.html"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!original.url.isHttps || !original.url.host.equals(PortalCookieJar.PORTAL_HOST, ignoreCase = true)) {
            throw SecurityException("Outbound request blocked: host restricted to ${PortalCookieJar.PORTAL_HOST} over HTTPS.")
        }

        val request = original.newBuilder()
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("User-Agent", USER_AGENT)
            .header("Origin", PORTAL_ORIGIN)
            .header("Referer", PORTAL_REFERER)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        return chain.proceed(request)
    }
}
