package com.nmamit.attendance.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, host-pinned HTTPS CookieJar for `studentportal.universitysolutions.in`.
 * Holds `PHPSESSID` and portal session cookies purely in volatile memory and never
 * forwards cookies to third-party domains or non-HTTPS URLs.
 */
class PortalCookieJar : CookieJar {

    companion object {
        const val PORTAL_HOST = "studentportal.universitysolutions.in"
    }

    private val cookieStore = ConcurrentHashMap<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.isHttps || !url.host.equals(PORTAL_HOST, ignoreCase = true)) {
            return
        }
        val now = System.currentTimeMillis()
        for (cookie in cookies) {
            if (cookie.expiresAt < now || cookie.value.equals("deleted", ignoreCase = true)) {
                cookieStore.remove(cookie.name)
            } else {
                cookieStore[cookie.name] = cookie
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.isHttps || !url.host.equals(PORTAL_HOST, ignoreCase = true)) {
            return emptyList()
        }
        val now = System.currentTimeMillis()
        val valid = ArrayList<Cookie>(cookieStore.size)
        val iterator = cookieStore.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cookie = entry.value
            if (cookie.expiresAt < now) {
                iterator.remove()
            } else if (cookie.matches(url)) {
                valid.add(cookie)
            }
        }
        return valid
    }

    fun hasSessionCookie(): Boolean {
        val now = System.currentTimeMillis()
        return cookieStore.values.any { it.expiresAt >= now && it.value.isNotBlank() }
    }

    fun clear() {
        cookieStore.clear()
    }
}
