package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class SeptisChatColors(
    val chatBackground: Color,
    val outgoingBubble: Color,
    val onOutgoingBubble: Color,
    val incomingBubble: Color,
    val onIncomingBubble: Color,
    val readReceiptBlue: Color,
    val onlineGreen: Color
)

val LocalSeptisChatColors = staticCompositionLocalOf {
    SeptisChatColors(
        chatBackground = ChatBgLight,
        outgoingBubble = OutgoingBubbleLight,
        onOutgoingBubble = OnOutgoingBubbleLight,
        incomingBubble = IncomingBubbleLight,
        onIncomingBubble = OnIncomingBubbleLight,
        readReceiptBlue = ReadReceiptBlue,
        onlineGreen = StatusActiveGreen
    )
}

private val SeptisDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = OnEmeraldDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    secondary = GoldSecondaryDark,
    onSecondary = OnGoldDark,
    secondaryContainer = GoldContainerDark,
    onSecondaryContainer = OnGoldContainerDark,
    tertiary = ForestTertiaryDark,
    onTertiary = OnForestTertiaryDark,
    tertiaryContainer = ForestContainerDark,
    onTertiaryContainer = OnForestContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val SeptisLightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = OnEmeraldLight,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = OnEmeraldContainerLight,
    secondary = GoldSecondaryLight,
    onSecondary = OnGoldLight,
    secondaryContainer = GoldContainerLight,
    onSecondaryContainer = OnGoldContainerLight,
    tertiary = ForestTertiaryLight,
    onTertiary = OnForestTertiaryLight,
    tertiaryContainer = ForestContainerLight,
    onTertiaryContainer = OnForestContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

val SeptisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SeptisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SeptisDarkColorScheme else SeptisLightColorScheme
    val chatColors = if (darkTheme) {
        SeptisChatColors(
            chatBackground = ChatBgDark,
            outgoingBubble = OutgoingBubbleDark,
            onOutgoingBubble = OnOutgoingBubbleDark,
            incomingBubble = IncomingBubbleDark,
            onIncomingBubble = OnIncomingBubbleDark,
            readReceiptBlue = ReadReceiptBlue,
            onlineGreen = StatusActiveGreen
        )
    } else {
        SeptisChatColors(
            chatBackground = ChatBgLight,
            outgoingBubble = OutgoingBubbleLight,
            onOutgoingBubble = OnOutgoingBubbleLight,
            incomingBubble = IncomingBubbleLight,
            onIncomingBubble = OnIncomingBubbleLight,
            readReceiptBlue = ReadReceiptBlue,
            onlineGreen = StatusActiveGreen
        )
    }

    CompositionLocalProvider(LocalSeptisChatColors provides chatColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SeptisTypography,
            shapes = SeptisShapes,
            content = content
        )
    }
}
