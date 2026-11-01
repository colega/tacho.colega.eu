package eu.colega.tacho.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Road-inspired palette: deep evergreen, open sky, and a restrained amber signal. */
private val RoadGreen = Color(0xFF24594F)
private val DrivingGreen = Color(0xFF4C7A52)

private val LightColors = lightColorScheme(
    primary = RoadGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E9E1),
    onPrimaryContainer = Color(0xFF163B34),
    secondary = Color(0xFF78520A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7E8C5),
    onSecondaryContainer = Color(0xFF382600),
    tertiary = DrivingGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCEBD8),
    onTertiaryContainer = Color(0xFF17371D),
    background = Color(0xFFF2F6F2),
    surface = Color(0xFFFCFDFC),
    surfaceDim = Color(0xFFDCE5DE),
    surfaceBright = Color(0xFFFCFDFC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCFDFC),
    surfaceContainer = Color(0xFFF5F8F5),
    surfaceContainerHigh = Color(0xFFEFF4F0),
    surfaceContainerHighest = Color(0xFFE6EDE8),
    onSurface = Color(0xFF172520),
    surfaceVariant = Color(0xFFE5ECE7),
    onSurfaceVariant = Color(0xFF46554E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5D2C2),
    onPrimary = Color(0xFF173B33),
    primaryContainer = Color(0xFF28574D),
    onPrimaryContainer = Color(0xFFD8F1E7),
    secondary = Color(0xFFEFC36C),
    onSecondary = Color(0xFF422D00),
    secondaryContainer = Color(0xFF5C4310),
    onSecondaryContainer = Color(0xFFFFE9B5),
    tertiary = Color(0xFFB3D6AE),
    onTertiary = Color(0xFF203D24),
    tertiaryContainer = Color(0xFF365A39),
    onTertiaryContainer = Color(0xFFD0EBCB),
    background = Color(0xFF111A16),
    surface = Color(0xFF18231E),
    surfaceDim = Color(0xFF111A16),
    surfaceBright = Color(0xFF343F39),
    surfaceContainerLowest = Color(0xFF0C1511),
    surfaceContainerLow = Color(0xFF18231E),
    surfaceContainer = Color(0xFF1C2822),
    surfaceContainerHigh = Color(0xFF222F29),
    surfaceContainerHighest = Color(0xFF293630),
    onSurface = Color(0xFFE0EAE4),
    surfaceVariant = Color(0xFF34443C),
    onSurfaceVariant = Color(0xFFC2D0C8),
)

private val TachoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TachoColegaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, shapes = TachoShapes, content = content)
}
