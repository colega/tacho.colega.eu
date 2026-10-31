package eu.colega.tacho.app.ui.theme

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

/** Brand palette: road blue + amber highlight, tuned for driver cab use. */
private val RoadBlue = Color(0xFF0B3D91)
private val Amber = Color(0xFFEFB000)
private val DrivingGreen = Color(0xFF2E7D32)
private val RestBlue = Color(0xFF0288D1)

private val LightColors = lightColorScheme(
    primary = RoadBlue,
    secondary = Color(0xFF6A5B00),
    tertiary = DrivingGreen,
    background = Color(0xFFF8FAFD),
    surface = Color(0xFFF8FAFD),
    surfaceVariant = Color(0xFFE0E2EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    secondary = Amber,
    tertiary = Color(0xFF80D184),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
    surfaceVariant = Color(0xFF3F4751),
)

@Composable
fun TachoColegaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
