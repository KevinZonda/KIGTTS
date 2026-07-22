package com.lhtstudio.kigtts.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import java.util.Locale

private val ThemeColorPresets = listOf(
    "#f44336", "#e91e63", "#9c27b0", "#673ab7", "#3f51b5",
    "#2196f3", "#03a9f4", "#00bcd4", "#009688", "#4caf50",
    "#8bc34a", "#cddc39", "#ffeb3b", "#ffc107", "#ff9800",
    "#ff5722", "#795548", "#9e9e9e", "#607d8b", "#038387"
)

@Composable
internal fun ThemeColorPickerDialog(
    title: String,
    initialColor: Color,
    colorLabel: String = "候选主题色",
    onEditPalette: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val context = LocalContext.current
    val initialHex = remember(initialColor) { colorToHexRgb(initialColor) }
    val initialHsl = remember(initialColor) { composeColorToHsl(initialColor) }
    var hexInput by rememberSaveable(initialHex) { mutableStateOf(initialHex) }
    var hue by rememberSaveable(initialHex) { mutableFloatStateOf(initialHsl[0]) }
    var saturation by rememberSaveable(initialHex) { mutableFloatStateOf(initialHsl[1]) }
    var lightness by rememberSaveable(initialHex) { mutableFloatStateOf(initialHsl[2]) }

    fun syncFromHex(hex: String) {
        val normalized = normalizeHexColorOrNull(hex) ?: return
        hexInput = normalized
        val hsl = composeColorToHsl(parseHexColor(normalized))
        hue = hsl[0]
        saturation = hsl[1]
        lightness = hsl[2]
    }

    Md2ScrollableDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        contentSpacing = 10.dp,
        content = {
            val preview = hslToComposeColor(hue, saturation, lightness)
            Text(
                text = colorLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemeColorPresets.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(parseHexColor(hex), CircleShape)
                            .clickable { syncFromHex(hex) }
                    )
                }
            }
            val hueGradient = Brush.horizontalGradient(
                listOf(
                    hslToComposeColor(0f, 1f, 0.5f),
                    hslToComposeColor(60f, 1f, 0.5f),
                    hslToComposeColor(120f, 1f, 0.5f),
                    hslToComposeColor(180f, 1f, 0.5f),
                    hslToComposeColor(240f, 1f, 0.5f),
                    hslToComposeColor(300f, 1f, 0.5f),
                    hslToComposeColor(360f, 1f, 0.5f)
                )
            )
            val saturationGradient = remember(hue, lightness) {
                Brush.horizontalGradient(
                    listOf(
                        hslToComposeColor(hue, 0f, lightness),
                        hslToComposeColor(hue, 1f, lightness)
                    )
                )
            }
            val lightnessGradient = remember(hue, saturation) {
                Brush.horizontalGradient(
                    listOf(
                        hslToComposeColor(hue, saturation, 0f),
                        hslToComposeColor(hue, saturation, 0.5f),
                        hslToComposeColor(hue, saturation, 1f)
                    )
                )
            }
            HslGradientSlider(
                label = "色相",
                value = hue,
                valueRange = 0f..360f,
                gradient = hueGradient,
                onValueChange = {
                    hue = it
                    hexInput = colorToHexRgb(hslToComposeColor(hue, saturation, lightness))
                }
            )
            HslGradientSlider(
                label = "饱和度",
                value = saturation,
                valueRange = 0f..1f,
                gradient = saturationGradient,
                onValueChange = {
                    saturation = it
                    hexInput = colorToHexRgb(hslToComposeColor(hue, saturation, lightness))
                }
            )
            HslGradientSlider(
                label = "亮度",
                value = lightness,
                valueRange = 0f..1f,
                gradient = lightnessGradient,
                onValueChange = {
                    lightness = it
                    hexInput = colorToHexRgb(hslToComposeColor(hue, saturation, lightness))
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(preview, RoundedCornerShape(UiTokens.Radius))
            )
            OutlinedTextField(
                value = hexInput,
                onValueChange = {
                    hexInput = it
                    normalizeHexColorOrNull(it)?.let(::syncFromHex)
                },
                singleLine = true,
                label = { Text("HEX（#RRGGBB）") },
                modifier = Modifier
                    .fillMaxWidth()
                    .kigttsTextToolbarAnchor(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                shape = Md2ControlShape,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = "拖动三条滑条设置色相、饱和度和亮度",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onEditPalette != null) {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onEditPalette)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MsIcon("palette", contentDescription = null)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                    Text(
                        text = "编辑调色板",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.body1
                    )
                    MsIcon("chevron_right", contentDescription = null)
                }
            }
        },
        confirmButton = {
            Md2TextButton(
                onClick = {
                    val normalized = normalizeHexColorOrNull(hexInput)
                    if (normalized == null) {
                        toast(context, "HEX 格式错误")
                    } else {
                        onColorSelected(parseHexColor(normalized))
                    }
                },
                contentColor = MaterialTheme.colorScheme.onSurface
            ) { Text("应用") }
        },
        dismissButton = {
            Md2TextButton(
                onClick = onDismissRequest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) { Text("取消") }
        }
    )
}

internal fun normalizeHexColorOrNull(raw: String): String? {
    val value = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
    return if (Regex("^#[0-9a-fA-F]{6}$").matches(value)) {
        value.lowercase(Locale.US)
    } else {
        null
    }
}

internal fun parseHexColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { UiTokens.Primary }

internal fun colorToHexRgb(color: Color): String =
    String.format(Locale.US, "#%06x", color.toArgb() and 0x00FFFFFF)

private fun composeColorToHsl(color: Color): FloatArray {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    return hsl
}

private fun hslToComposeColor(h: Float, s: Float, l: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    return Color(
        ColorUtils.HSLToColor(
            floatArrayOf(hue, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
        )
    )
}

@Composable
private fun HslGradientSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    gradient: Brush,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.Center)
                    .background(gradient, RectangleShape)
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
        }
    }
}
