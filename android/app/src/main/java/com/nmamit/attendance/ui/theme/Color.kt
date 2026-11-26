package com.nmamit.attendance.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================================================
// iOS Liquid Glass Design System — Monochrome Pitch Black & White
// Matches public/css/style.css CSS custom properties 1:1
// ==========================================================================

val BgCanvas = Color(0xFF000000)            // --bg-canvas: #000000
val Foreground = Color(0xFFFFFFFF)          // --foreground: #ffffff
val ForegroundMuted = Color(0xFF8E8E93)     // --foreground-muted: #8e8e93
val ForegroundSubtle = Color(0xFF48484A)    // --foreground-subtle: #48484a

val GlassCardTint = Color(0xFF16161A)       // --glass-card base rgb(22, 22, 26)
val AuthCardTint = Color(0xFF0D0D11)        // .auth-card base rgb(13, 13, 17)
val GlassCardBg = Color(0xA616161A)         // rgba(22, 22, 26, 0.65)
val GlassCardSolid = Color(0xF50E0E14)      // Dropdown/popover rgba(14, 14, 20, 0.96)
val GlassSubCardBg = Color(0x0AFFFFFF)      // --glass-subcard: rgba(255,255,255,0.04)
val GlassInputBg = Color(0x0FFFFFFF)        // rgba(255,255,255,0.06)
val GlassHoverBg = Color(0x14FFFFFF)        // rgba(255,255,255,0.08)
val GlassBorder = Color(0x1CFFFFFF)         // --glass-border: rgba(255,255,255,0.11)
val GlassBorderSubtle = Color(0x10FFFFFF)   // --glass-border-subtle: rgba(255,255,255,0.06)
val GlassBorderSpecular = Color(0x3DFFFFFF) // Top rim highlight: rgba(255,255,255,0.24)
val GlassBorderLeft = Color(0x26FFFFFF)     // Left rim highlight: rgba(255,255,255,0.15)
val GlassInputTopRim = Color(0x47FFFFFF)    // Input top rim: rgba(255,255,255,0.28)

val TabTrack = Color(0x0FFFFFFF)            // --tab-track: rgba(255,255,255,0.06)
val TabActiveBg = Color(0x2EFFFFFF)         // --tab-active-bg: rgba(255,255,255,0.18)

// Semantic iOS Dark State Colors
val Emerald = Color(0xFF30D158)             // --emerald: #30d158
val EmeraldBright = Color(0xFF34D399)       // #34d399 (IA Final Score)
val EmeraldBg = Color(0x2630D158)           // rgba(48, 209, 88, 0.15)
val EmeraldBorder = Color(0x5930D158)       // rgba(48, 209, 88, 0.35)

val Amber = Color(0xFFFFD60A)               // --amber: #ffd60a
val AmberBg = Color(0x26FFD60A)             // rgba(255, 214, 10, 0.15)
val AmberBorder = Color(0x59FFD60A)         // rgba(255, 214, 10, 0.35)

val Rose = Color(0xFFFF453A)                // --rose: #ff453a
val RoseSoft = Color(0xFFFB7185)            // #fb7185 (Absent dates pill)
val RoseBg = Color(0x26FF453A)              // rgba(255, 69, 58, 0.15)
val RoseBorder = Color(0x59FF453A)          // rgba(255, 69, 58, 0.35)

val FireOrange = Color(0xFFFF6900)          // Insight fire card
val CyanAccent = Color(0xFF00E5FF)          // --primary cyan for IA hero & jump button
val IndigoAccent = Color(0xFF6366F1)
val GoldAccent = Color(0xFFFFD60A)
val LinkedInBlue = Color(0xFF0A66C2)

// Heatmap 5-level palette (matches .heatmap-cell.level-0..4)
val HeatmapLevel0 = Color(0x0DFFFFFF)       // rgba(255,255,255,0.05)
val HeatmapLevel1 = Color(0xFFFF453A)       // #ff453a
val HeatmapLevel2 = Color(0xFFFFD60A)       // #ffd60a
val HeatmapLevel3 = Color(0x9930D158)       // rgba(48, 209, 88, 0.6)
val HeatmapLevel4 = Color(0xFF30D158)       // #30d158

// Legacy aliases kept for compatibility
val CyanPrimary = Foreground
val DarkBackground = BgCanvas
val DarkSurface = GlassCardSolid
val DarkSurfaceCard = GlassCardBg
val DarkSurfaceBorder = GlassBorder
val TextPrimary = Foreground
val TextSecondary = ForegroundMuted
val TextMuted = ForegroundSubtle
val GreenSuccess = Emerald
val AmberWarning = Amber
val RedDanger = Rose
