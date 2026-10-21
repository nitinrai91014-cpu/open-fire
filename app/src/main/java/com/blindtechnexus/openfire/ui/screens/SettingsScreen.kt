package com.blindtechnexus.openfire.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindtechnexus.openfire.model.ControlMode
import com.blindtechnexus.openfire.model.GameSettings
import com.blindtechnexus.openfire.model.Screen
import com.blindtechnexus.openfire.model.SoundTheme
import com.blindtechnexus.openfire.ui.components.ButtonType
import com.blindtechnexus.openfire.ui.components.GameButton
import com.blindtechnexus.openfire.ui.components.GameCard
import com.blindtechnexus.openfire.ui.theme.BtnBlue
import com.blindtechnexus.openfire.ui.theme.BtnPrimary
import com.blindtechnexus.openfire.ui.theme.CardBorder
import com.blindtechnexus.openfire.ui.theme.DarkBg
import com.blindtechnexus.openfire.ui.theme.TextAccent
import com.blindtechnexus.openfire.ui.theme.TextMuted
import com.blindtechnexus.openfire.ui.theme.WarnBright
import com.blindtechnexus.openfire.viewmodel.GameViewModel
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: GameViewModel) {
    val currentSettings by viewModel.settings.collectAsState()
    val engines by viewModel.ttsManager.availableEngines.collectAsState()
    val voices by viewModel.ttsManager.availableVoices.collectAsState()
    val scrollState = rememberScrollState()

    var nameState by remember { mutableStateOf(currentSettings.name) }
    var langState by remember { mutableStateOf(currentSettings.lang) }
    var modeState by remember { mutableStateOf(currentSettings.mode) }
    var engineState by remember { mutableStateOf(currentSettings.ttsEnginePackage) }
    var voiceState by remember { mutableStateOf(currentSettings.voiceName) }
    var rateState by remember { mutableFloatStateOf(currentSettings.ttsRate) }
    var ttsVolState by remember { mutableFloatStateOf(currentSettings.ttsVolume) }
    var musicVolState by remember { mutableFloatStateOf(currentSettings.musicVolume) }
    var sfxVolState by remember { mutableFloatStateOf(currentSettings.sfxVolume) }

    var pMaxHpState by remember { mutableIntStateOf(currentSettings.playerMaxHp) }
    var pDmgMultState by remember { mutableFloatStateOf(currentSettings.playerDamageMult) }
    var dMaxHpState by remember { mutableIntStateOf(currentSettings.devilMaxHp) }
    var dDmgMultState by remember { mutableFloatStateOf(currentSettings.devilDamageMult) }

    var soundThemeState by remember { mutableStateOf(currentSettings.soundTheme) }
    var hapticsState by remember { mutableStateOf(currentSettings.hapticsEnabled) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        GameCard {
            Text(
                text = viewModel.t("settings"),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Profile & Language
        GameCard {
            Text(
                text = viewModel.t("name"),
                fontSize = 14.sp,
                color = TextAccent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = nameState,
                onValueChange = { nameState = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_name_input"),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = DarkBg,
                    unfocusedContainerColor = DarkBg,
                    focusedBorderColor = CardBorder,
                    unfocusedBorderColor = CardBorder
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = viewModel.t("language"),
                fontSize = 14.sp,
                color = TextAccent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            CustomDropdown(
                selectedValue = when (langState) {
                    "hi" -> "हिन्दी (Hindi)"
                    "ne" -> "नेपाली (Nepali)"
                    else -> "English"
                },
                options = listOf(
                    "en" to "English",
                    "hi" to "हिन्दी (Hindi)",
                    "ne" to "नेपाली (Nepali)"
                ),
                onSelect = {
                    langState = it
                    viewModel.setLanguage(it)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = viewModel.t("controlMode"),
                fontSize = 14.sp,
                color = TextAccent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            CustomDropdown(
                selectedValue = when (modeState) {
                    ControlMode.BUTTONS -> viewModel.t("buttons")
                    ControlMode.GESTURES -> viewModel.t("gestures")
                    ControlMode.BOTH -> viewModel.t("both")
                },
                options = listOf(
                    ControlMode.BUTTONS to viewModel.t("buttons"),
                    ControlMode.GESTURES to viewModel.t("gestures"),
                    ControlMode.BOTH to viewModel.t("both")
                ),
                onSelect = { modeState = it }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // System TTS Engines and Voices
        GameCard {
            Text(
                text = viewModel.t("ttsVoice"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // TTS Engines
            Text(
                text = viewModel.t("ttsEngine"),
                fontSize = 14.sp,
                color = TextAccent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            val engineOptions = mutableListOf("" to viewModel.t("defaultEngine"))
            engines.forEach { engineOptions.add(it.name to "${it.label} (${it.name})") }
            val selectedEngineLabel = engineOptions.find { it.first == engineState }?.second ?: viewModel.t("defaultEngine")
            CustomDropdown(
                selectedValue = selectedEngineLabel,
                options = engineOptions,
                onSelect = {
                    engineState = it
                    viewModel.ttsManager.initTts(it.ifEmpty { null })
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // TTS Voices
            Text(
                text = viewModel.t("ttsVoice"),
                fontSize = 14.sp,
                color = TextAccent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            val voiceOptions = mutableListOf("" to viewModel.t("autoVoice"))
            voices.forEach { voiceOptions.add(it.name to it.displayName) }
            val selectedVoiceLabel = voiceOptions.find { it.first == voiceState }?.second ?: viewModel.t("autoVoice")
            CustomDropdown(
                selectedValue = selectedVoiceLabel,
                options = voiceOptions,
                onSelect = {
                    voiceState = it
                    viewModel.ttsManager.currentVoiceName = it
                    viewModel.ttsManager.applyVoiceSettings()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Speech Rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = viewModel.t("ttsRate"),
                    fontSize = 14.sp,
                    color = TextAccent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format(Locale.US, "%.1fx", rateState),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarnBright
                )
            }
            Slider(
                value = rateState,
                onValueChange = { rateState = it },
                valueRange = 0.5f..2.0f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = BtnPrimary,
                    activeTrackColor = BtnPrimary,
                    inactiveTrackColor = Color(0xFF101820)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
            GameButton(
                text = viewModel.t("preview"),
                onClick = {
                    viewModel.playSound("click")
                    viewModel.ttsManager.previewVoice(viewModel.t("previewSample"), rateState, voiceState)
                },
                testTag = "preview_tts_button"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Independent Audio Volume Mixer
        GameCard {
            Text(
                text = viewModel.t("audioMixer"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Speech TTS Volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = viewModel.t("ttsVolume"), fontSize = 14.sp, color = TextAccent, fontWeight = FontWeight.Bold)
                Text(text = "${(ttsVolState * 100).toInt()}%", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = ttsVolState,
                onValueChange = {
                    ttsVolState = it
                    viewModel.ttsManager.speechVolume = it
                },
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(thumbColor = BtnPrimary, activeTrackColor = BtnPrimary)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Music Volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = viewModel.t("musicVolume"), fontSize = 14.sp, color = TextAccent, fontWeight = FontWeight.Bold)
                Text(text = "${(musicVolState * 100).toInt()}%", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = musicVolState,
                onValueChange = {
                    musicVolState = it
                    viewModel.audioManager.setMusicVol(it)
                },
                valueRange = 0.0f..1.0f,
                colors = SliderDefaults.colors(thumbColor = WarnBright, activeTrackColor = WarnBright)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // SFX & 3D Spatial Audio Volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = viewModel.t("sfxVolume"), fontSize = 14.sp, color = TextAccent, fontWeight = FontWeight.Bold)
                Text(text = "${(sfxVolState * 100).toInt()}%", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = sfxVolState,
                onValueChange = {
                    sfxVolState = it
                    viewModel.audioManager.sfxVolume = it
                },
                valueRange = 0.0f..1.0f,
                colors = SliderDefaults.colors(thumbColor = BtnBlue, activeTrackColor = BtnBlue)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Combat Strength & Balance Customization
        GameCard {
            Text(
                text = viewModel.t("strengthSettings"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Player Max HP
            Text(text = "${viewModel.t("playerHealthSetting")}: $pMaxHpState HP", fontSize = 14.sp, color = TextAccent, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            CustomDropdown(
                selectedValue = "$pMaxHpState HP",
                options = listOf(150 to "150 HP", 200 to "200 HP (Default)", 250 to "250 HP", 300 to "300 HP (Tank)"),
                onSelect = { pMaxHpState = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Player Damage Multiplier
            Text(text = "${viewModel.t("playerDamageSetting")}: ${String.format(Locale.US, "%.2fx", pDmgMultState)}", fontSize = 14.sp, color = TextAccent, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            CustomDropdown(
                selectedValue = "${pDmgMultState}x",
                options = listOf(0.8f to "0.80x (Challenging)", 1.0f to "1.00x (Standard)", 1.25f to "1.25x (Boosted)", 1.5f to "1.50x (Heavy)"),
                onSelect = { pDmgMultState = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Devil Max HP
            Text(text = "${viewModel.t("devilHealthSetting")}: $dMaxHpState HP", fontSize = 14.sp, color = TextAccent, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            CustomDropdown(
                selectedValue = "$dMaxHpState HP",
                options = listOf(150 to "150 HP (Quick)", 200 to "200 HP (Standard)", 260 to "260 HP (Tough)", 320 to "320 HP (Boss)"),
                onSelect = { dMaxHpState = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Devil Damage Multiplier
            Text(text = "${viewModel.t("devilDamageSetting")}: ${String.format(Locale.US, "%.2fx", dDmgMultState)}", fontSize = 14.sp, color = TextAccent, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            CustomDropdown(
                selectedValue = "${dDmgMultState}x",
                options = listOf(0.7f to "0.70x (Easy)", 1.0f to "1.00x (Normal)", 1.3f to "1.30x (Fierce)", 1.6f to "1.60x (Nightmare)"),
                onSelect = { dDmgMultState = it }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Sound Themes & Haptics
        GameCard {
            Text(
                text = viewModel.t("soundTheme"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(10.dp))
            CustomDropdown(
                selectedValue = soundThemeState.title,
                options = listOf(
                    SoundTheme.TACTICAL to SoundTheme.TACTICAL.title,
                    SoundTheme.CYBER to SoundTheme.CYBER.title,
                    SoundTheme.STEALTH to SoundTheme.STEALTH.title
                ),
                onSelect = {
                    soundThemeState = it
                    viewModel.audioManager.updateTheme(it)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = viewModel.t("haptics"), fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(text = "Haptic pulses for gunshots and damage", fontSize = 13.sp, color = TextMuted)
                }
                Switch(
                    checked = hapticsState,
                    onCheckedChange = { hapticsState = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = BtnPrimary, checkedTrackColor = Color(0xFF002B18))
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GameButton(
            text = viewModel.t("saveBack"),
            onClick = {
                val updated = currentSettings.copy(
                    name = if (nameState.trim().isNotEmpty()) nameState.trim() else currentSettings.name,
                    lang = langState,
                    mode = modeState,
                    ttsEnginePackage = engineState,
                    voiceName = voiceState,
                    ttsRate = rateState,
                    ttsVolume = ttsVolState,
                    musicVolume = musicVolState,
                    sfxVolume = sfxVolState,
                    playerMaxHp = pMaxHpState,
                    playerDamageMult = pDmgMultState,
                    devilMaxHp = dMaxHpState,
                    devilDamageMult = dDmgMultState,
                    soundTheme = soundThemeState,
                    hapticsEnabled = hapticsState
                )
                viewModel.updateSettings(updated)
                viewModel.playSound("click")
                viewModel.navigateTo(Screen.MENU)
            },
            type = ButtonType.PRIMARY,
            testTag = "save_settings_button"
        )
        Spacer(modifier = Modifier.height(12.dp))
        GameButton(
            text = viewModel.t("backToMenu"),
            onClick = { viewModel.navigateTo(Screen.MENU) },
            type = ButtonType.DANGER,
            testTag = "settings_back_button"
        )
    }
}

@Composable
fun <T> CustomDropdown(
    selectedValue: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, CardBorder, RoundedCornerShape(10.dp))
            .background(DarkBg, RoundedCornerShape(10.dp))
            .clickable { expanded = true }
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedValue,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Expand options",
                tint = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFF132238))
                .border(1.dp, CardBorder)
        ) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(text = label, color = Color.White, fontSize = 15.sp) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    }
                )
            }
        }
    }
}
