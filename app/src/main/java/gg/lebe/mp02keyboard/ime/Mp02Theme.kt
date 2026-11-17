package gg.lebe.mp02keyboard.ime

enum class Mp02Finish {
    ONYX,
    PEARL,
    EMBER,
    AUTO
}

data class Mp02Palette(
    val isLight: Boolean,
    val deckTopColor: Int,
    val deckMidColor: Int,
    val deckBottomColor: Int,
    val topChamferDark: Int,
    val topChamferLight: Int,
    val keyWellOuter: Int,
    val keyFaceTop: Int,
    val keyFaceMid: Int,
    val keyFaceBottom: Int,
    val keyPressedTop: Int,
    val keyPressedBottom: Int,
    val keyInnerHighlight: Int,
    val keyInnerBottomLip: Int,
    val keyDropShadow: Int,
    val primaryLegend: Int,
    val secondaryLegend: Int,
    val primaryLegendPressed: Int,
    val secondaryLegendPressed: Int,
    val dimLegend: Int,
    val homingPipBase: Int,
    val homingPipHighlight: Int,
    val homingPipShadow: Int,
    val backlightGlow: Int,
    val accentRed: Int,
    val overlayCardBg: Int,
    val overlayCardBorder: Int
)

object Mp02Themes {
    val Onyx = Mp02Palette(
        isLight = false,
        deckTopColor = 0xFF141415.toInt(),
        deckMidColor = 0xFF19191B.toInt(),
        deckBottomColor = 0xFF161618.toInt(),
        topChamferDark = 0xFF080809.toInt(),
        topChamferLight = 0xFF323236.toInt(),
        keyWellOuter = 0xFF09090B.toInt(),
        keyFaceTop = 0xFF2A2A2D.toInt(),
        keyFaceMid = 0xFF222225.toInt(),
        keyFaceBottom = 0xFF1B1B1D.toInt(),
        keyPressedTop = 0xFF171719.toInt(),
        keyPressedBottom = 0xFF131315.toInt(),
        keyInnerHighlight = 0xFF3E3E43.toInt(),
        keyInnerBottomLip = 0xFF101012.toInt(),
        keyDropShadow = 0xB0000000.toInt(),
        primaryLegend = 0xFFDFDFDC.toInt(),
        secondaryLegend = 0xFFBFBFBC.toInt(),
        primaryLegendPressed = 0xFFFFFFFF.toInt(),
        secondaryLegendPressed = 0xFFF4F4F0.toInt(),
        dimLegend = 0xFF6A6A6C.toInt(),
        homingPipBase = 0xFF47474B.toInt(),
        homingPipHighlight = 0xFFBDBDC2.toInt(),
        homingPipShadow = 0xFF0D0D0F.toInt(),
        backlightGlow = 0x48FFFFFF,
        accentRed = 0xFFFF2E00.toInt(),
        overlayCardBg = 0xFF232326.toInt(),
        overlayCardBorder = 0xFFFF2E00.toInt()
    )

    val Pearl = Mp02Palette(
        isLight = true,
        deckTopColor = 0xFFB8B6B8.toInt(),
        deckMidColor = 0xFFAEACAE.toInt(),
        deckBottomColor = 0xFFB4B2B4.toInt(),
        topChamferDark = 0xFF666466.toInt(),
        topChamferLight = 0xFFE6E4E5.toInt(),
        keyWellOuter = 0xFF444143.toInt(),
        keyFaceTop = 0xFFD5D3D5.toInt(),
        keyFaceMid = 0xFFC7C5C7.toInt(),
        keyFaceBottom = 0xFFBCBABD.toInt(),
        keyPressedTop = 0xFFB3B1B3.toInt(),
        keyPressedBottom = 0xFFA7A5A8.toInt(),
        keyInnerHighlight = 0xFFF1EFF0.toInt(),
        keyInnerBottomLip = 0xFF908E90.toInt(),
        keyDropShadow = 0x55000000,
        primaryLegend = 0xFF4C494A.toInt(),
        secondaryLegend = 0xFF5C595A.toInt(),
        primaryLegendPressed = 0xFF2A2829.toInt(),
        secondaryLegendPressed = 0xFF383637.toInt(),
        dimLegend = 0xFF878486.toInt(),
        homingPipBase = 0xFF9C9A9C.toInt(),
        homingPipHighlight = 0xFFFFFFFF.toInt(),
        homingPipShadow = 0xFF5C5A5C.toInt(),
        backlightGlow = 0x66FFFFFF,
        accentRed = 0xFFFF2E00.toInt(),
        overlayCardBg = 0xFFCAC8CA.toInt(),
        overlayCardBorder = 0xFFFF2E00.toInt()
    )

    val Ember = Mp02Palette(
        isLight = false,
        deckTopColor = 0xFFB82104.toInt(),
        deckMidColor = 0xFFC32607.toInt(),
        deckBottomColor = 0xFFAF1D03.toInt(),
        topChamferDark = 0xFF3D0700.toInt(),
        topChamferLight = 0xFFEA5832.toInt(),
        keyWellOuter = 0xFF2B0702.toInt(),
        keyFaceTop = 0xFF29292C.toInt(),
        keyFaceMid = 0xFF202023.toInt(),
        keyFaceBottom = 0xFF171719.toInt(),
        keyPressedTop = 0xFF141416.toInt(),
        keyPressedBottom = 0xFF101012.toInt(),
        keyInnerHighlight = 0xFF3F3F44.toInt(),
        keyInnerBottomLip = 0xFF0D0D0F.toInt(),
        keyDropShadow = 0xA82E0600.toInt(),
        primaryLegend = 0xFFEAEAE6.toInt(),
        secondaryLegend = 0xFFC8C8C4.toInt(),
        primaryLegendPressed = 0xFFFFFFFF.toInt(),
        secondaryLegendPressed = 0xFFF4F4F0.toInt(),
        dimLegend = 0xFF6E6E72.toInt(),
        homingPipBase = 0xFF47474B.toInt(),
        homingPipHighlight = 0xFFC0C0C5.toInt(),
        homingPipShadow = 0xFF0C0C0E.toInt(),
        backlightGlow = 0x4CFFFFFF,
        accentRed = 0xFFFF3B0E.toInt(),
        overlayCardBg = 0xFF242427.toInt(),
        overlayCardBorder = 0xFFFFFFFF.toInt()
    )

    fun resolve(finish: Mp02Finish, systemDark: Boolean): Mp02Palette {
        return when (finish) {
            Mp02Finish.ONYX -> Onyx
            Mp02Finish.PEARL -> Pearl
            Mp02Finish.EMBER -> Ember
            Mp02Finish.AUTO -> if (systemDark) Onyx else Pearl
        }
    }
}
