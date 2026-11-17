package gg.lebe.mp02keyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import gg.lebe.mp02keyboard.ime.Mp02Finish
import gg.lebe.mp02keyboard.ime.Mp02Preferences
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            Mp02SettingsScreen()
        }
    }
}

private data class ImeStatus(val enabled: Boolean, val active: Boolean)

private fun readImeStatus(context: Context): ImeStatus {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        ?: return ImeStatus(enabled = false, active = false)
    val pkg = context.packageName
    val enabled = imm.enabledInputMethodList.any { it.packageName == pkg }
    val currentId = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
    val active = currentId.startsWith("$pkg/")
    return ImeStatus(enabled = enabled, active = active)
}

@Composable
private fun Mp02SettingsScreen() {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { Mp02Preferences(context) }

    var imeStatus by remember { mutableStateOf(readImeStatus(context)) }
    var emberUnlocked by remember { mutableStateOf(prefs.emberUnlocked) }
    var finish by remember { mutableStateOf(prefs.finish) }
    var heightScale by remember { mutableFloatStateOf(prefs.heightScale) }
    var bottomChinMm by remember { mutableFloatStateOf(prefs.bottomChinMm) }
    var haptics by remember { mutableStateOf(prefs.hapticsEnabled) }
    var backlight by remember { mutableStateOf(prefs.backlightEnabled) }
    var autoCap by remember { mutableStateOf(prefs.autoCapEnabled) }
    var doubleSpace by remember { mutableStateOf(prefs.doubleSpacePeriod) }
    var trackpadSpace by remember { mutableStateOf(prefs.spacebarTrackpad) }
    var longPressAlt by remember { mutableStateOf(prefs.longPressForAlt) }
    var sandboxText by remember { mutableStateOf("") }
    var secretTapCount by remember { mutableIntStateOf(0) }
    var lastSecretTapMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                imeStatus = readImeStatus(context)
                emberUnlocked = prefs.emberUnlocked
                finish = prefs.finish
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val bg = Color(0xFF141415)
    val card = Color(0xFF1C1C1E)
    val border = Color(0xFF2B2B2E)
    val ink = Color(0xFFEAE9E3)
    val muted = Color(0xFF9A9A9E)
    val accentRed = Color(0xFFFF2E00)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "MP02 Keyboard",
            color = ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                val now = System.currentTimeMillis()
                val nextCount = if (now - lastSecretTapMs <= 1500L) secretTapCount + 1 else 1
                lastSecretTapMs = now
                if (nextCount >= 5) {
                    secretTapCount = 0
                    if (!emberUnlocked) {
                        prefs.emberUnlocked = true
                        emberUnlocked = true
                    }
                    finish = Mp02Finish.EMBER
                    prefs.finish = Mp02Finish.EMBER
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, "Ember finish unlocked", Toast.LENGTH_SHORT).show()
                } else {
                    secretTapCount = nextCount
                }
            }
        )

        if (!imeStatus.enabled || !imeStatus.active) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!imeStatus.enabled) {
                    ActionPill(
                        label = "Enable Keyboard",
                        accent = accentRed,
                        ink = ink,
                        modifier = Modifier.weight(1f)
                    ) {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    }
                }
                if (imeStatus.enabled && !imeStatus.active) {
                    ActionPill(
                        label = "Select MP02 Keyboard",
                        accent = accentRed,
                        ink = ink,
                        modifier = Modifier.weight(1f)
                    ) {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showInputMethodPicker()
                    }
                }
            }
        }

        // Typing Sandbox
        OutlinedTextField(
            value = sandboxText,
            onValueChange = { sandboxText = it },
            placeholder = { Text("Tap to test typing…", color = muted, fontSize = 14.sp) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ink,
                unfocusedTextColor = ink,
                focusedContainerColor = card,
                unfocusedContainerColor = card,
                focusedBorderColor = Color(0xFF4A4A50),
                unfocusedBorderColor = border,
                cursorColor = accentRed
            )
        )

        // Finish & Dimensions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(card)
                .border(1.dp, border, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Finish", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val visibleFinishes = remember(emberUnlocked) {
                    listOfNotNull(
                        Mp02Finish.ONYX,
                        Mp02Finish.PEARL,
                        Mp02Finish.EMBER.takeIf { emberUnlocked },
                        Mp02Finish.AUTO
                    )
                }
                visibleFinishes.forEach { option ->
                    val selected = finish == option
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color(0xFF2B2B2F) else Color(0xFF151517))
                            .border(1.dp, if (selected) accentRed else border, RoundedCornerShape(10.dp))
                            .clickable {
                                finish = option
                                prefs.finish = option
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = option.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (selected) ink else muted,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vertical Scale", color = ink, fontSize = 14.sp)
                Text(
                    text = String.format(Locale.US, "%.2fx", heightScale),
                    color = if (heightScale == 1.0f) muted else accentRed,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable {
                        heightScale = 1.0f
                        prefs.heightScale = 1.0f
                    }
                )
            }
            Slider(
                value = heightScale,
                onValueChange = {
                    heightScale = it
                    prefs.heightScale = it
                },
                valueRange = 0.90f..1.20f,
                colors = SliderDefaults.colors(
                    thumbColor = ink,
                    activeTrackColor = accentRed,
                    inactiveTrackColor = border
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bottom Chin", color = ink, fontSize = 14.sp)
                Text(
                    text = String.format(Locale.US, "%.1f mm", bottomChinMm),
                    color = muted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable {
                        bottomChinMm = 2.2f
                        prefs.bottomChinMm = 2.2f
                    }
                )
            }
            Slider(
                value = bottomChinMm,
                onValueChange = {
                    bottomChinMm = it
                    prefs.bottomChinMm = it
                },
                valueRange = 0.8f..5.5f,
                colors = SliderDefaults.colors(
                    thumbColor = ink,
                    activeTrackColor = accentRed,
                    inactiveTrackColor = border
                )
            )
        }

        // Behavior Toggles
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(card)
                .border(1.dp, border, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingToggle("Key Backlight", backlight, ink, muted, accentRed) {
                backlight = it; prefs.backlightEnabled = it
            }
            SettingToggle("Haptic Click", haptics, ink, muted, accentRed) {
                haptics = it; prefs.hapticsEnabled = it
            }
            SettingToggle("Auto-Capitalize", autoCap, ink, muted, accentRed) {
                autoCap = it; prefs.autoCapEnabled = it
            }
            SettingToggle("Double-Space Period", doubleSpace, ink, muted, accentRed) {
                doubleSpace = it; prefs.doubleSpacePeriod = it
            }
            SettingToggle("Spacebar Cursor Scrub", trackpadSpace, ink, muted, accentRed) {
                trackpadSpace = it; prefs.spacebarTrackpad = it
            }
            SettingToggle("Hold Key for Alt Symbol", longPressAlt, ink, muted, accentRed) {
                longPressAlt = it; prefs.longPressForAlt = it
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    accent: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(text = label, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingToggle(
    title: String,
    checked: Boolean,
    ink: Color,
    muted: Color,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, color = ink, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ink,
                checkedTrackColor = accent,
                uncheckedThumbColor = muted,
                uncheckedTrackColor = Color(0xFF28282B)
            )
        )
    }
}
