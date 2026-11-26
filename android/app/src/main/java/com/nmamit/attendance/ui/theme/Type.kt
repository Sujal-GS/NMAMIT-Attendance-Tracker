package com.nmamit.attendance.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.nmamit.attendance.R

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val plusJakartaSansGoogleFont = GoogleFont("Plus Jakarta Sans")
private val jetBrainsMonoGoogleFont = GoogleFont("JetBrains Mono")

val PlusJakartaSansFamily = FontFamily(
    Font(googleFont = plusJakartaSansGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = plusJakartaSansGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = plusJakartaSansGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = plusJakartaSansGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = plusJakartaSansGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = jetBrainsMonoGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = jetBrainsMonoGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = jetBrainsMonoGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = jetBrainsMonoGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = jetBrainsMonoGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold)
)

val AppBodyFont = PlusJakartaSansFamily
val AppMonoFont = JetBrainsMonoFamily

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.Black, fontSize = 52.sp),
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)
