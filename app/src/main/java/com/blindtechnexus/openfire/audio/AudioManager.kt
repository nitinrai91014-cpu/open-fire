package com.blindtechnexus.openfire.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager as AndroidAudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.blindtechnexus.openfire.model.SoundTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class AudioManager(private val context: Context) {
    companion object {
        private const val TAG = "OpenFireAudio"
    }

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundMap = mutableMapOf<String, Int>()
    private val loadedSounds = mutableSetOf<Int>()
    private val activeStreams = mutableMapOf<String, Int>()

    private var mediaPlayer: MediaPlayer? = null
    private var isMusicPlaying = false

    var sfxVolume: Float = 1.0f
    var musicVolume: Float = 0.5f
    var soundTheme: SoundTheme = SoundTheme.TACTICAL
    var hapticsEnabled: Boolean = true

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val soundAliases = mapOf(
        "click" to listOf("click.mp3", "90kqun.mp3"),
        "airplane" to listOf("airplane.mp3", "w3abgf.mp3"),
        "music" to listOf("background.mp3", "0gk0a5.mp3"),
        "damage" to listOf("damage.mp3", "0zikl2.mp3"),
        "jump" to listOf("jump.mp3", "yz2t5x.mp3"),
        "gloowall" to listOf("gloowall.mp3", "49h9c0.mp3"),
        "medkit" to listOf("medkit.mp3", "oqvwpr.mp3"),
        "parachute" to listOf("parachute.mp3", "7yxd6u.mp3"),
        "shotgun" to listOf("short-gun.mp3", "shotgun.mp3", "3vmxkx.mp3"),
        "fire" to listOf("fire.mp3", "gwaweo.mp3"),
        "win" to listOf("win.mp3", "vlw1o1.mp3"),
        "land" to listOf("land.mp3", "yz2t5x.mp3"),
        "ammo" to listOf("ammo.mp3", "onm1jd.mp3"),
        "lose" to listOf("lose.mp3", "6zjd1d.mp3"),
        "pistol" to listOf("pistel-sound.mp3", "pistol-sound.mp3"),
        "hit" to listOf("hit.mp3"),
        "enemyHit" to listOf("enemy-hit.mp3"),
        "walk" to listOf("walking-sound.mp3", "walking-sound.wav"),
        "spend" to listOf("spend-sound.mp3")
    )

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSounds.add(sampleId)
            }
        }
        preloadAllSounds()
    }

    private fun preloadAllSounds() {
        CoroutineScope(Dispatchers.IO).launch {
            for ((key, files) in soundAliases) {
                var loaded = false
                // Try from asset files first
                for (fileName in files) {
                    try {
                        val afd = context.assets.openFd("sounds/$fileName")
                        val soundId = soundPool.load(afd, 1)
                        soundMap[key] = soundId
                        loaded = true
                        break
                    } catch (_: Exception) {
                        // File not in assets yet
                    }
                }
                // If not found in assets, generate synthetic procedural audio buffer and load it
                if (!loaded) {
                    try {
                        val synthFile = generateSyntheticSoundFile(key)
                        if (synthFile != null && synthFile.exists()) {
                            val soundId = soundPool.load(synthFile.absolutePath, 1)
                            soundMap[key] = soundId
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error generating fallback for $key", e)
                    }
                }
            }
        }
    }

    /**
     * 3D spatial panning & attenuation.
     * x: left (-100 to 0) to right (0 to 100), devil distance is along z (0 to 100).
     */
    fun calculate3dVolume(
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        baseVol: Float = 1.0f
    ): Pair<Float, Float> {
        val dist = sqrt(x * x + y * y + z * z)
        // Inverse distance attenuation
        val distGain = 1.0f / (1.0f + 0.025f * max(0f, dist - 5f))
        val clampedDistGain = distGain.coerceIn(0.12f, 1.0f)

        // Azimuth panning: -1.0 is full left, 0 is center, +1.0 is full right
        val pan = if (dist > 0.01f) (x / max(1f, dist)).coerceIn(-1.0f, 1.0f) else 0f
        val angle = (pan + 1.0f) * (Math.PI.toFloat() / 4.0f) // 0 to PI/2
        val leftGain = cos(angle)
        val rightGain = sin(angle)

        val totalVol = baseVol * sfxVolume * clampedDistGain
        val left = (totalVol * leftGain).coerceIn(0f, 1f)
        val right = (totalVol * rightGain).coerceIn(0f, 1f)
        return Pair(left, right)
    }

    fun playSound(
        key: String,
        volume: Float = 1.0f,
        rate: Float = 1.0f,
        posX: Float = 0f,
        posY: Float = 0f,
        posZ: Float = 0f
    ): Int {
        val soundId = soundMap[key] ?: return -1
        val (left, right) = calculate3dVolume(posX, posY, posZ, volume)
        val pitch = when (soundTheme) {
            SoundTheme.CYBER -> rate * 1.15f
            SoundTheme.STEALTH -> rate * 0.9f
            else -> rate
        }.coerceIn(0.5f, 2.0f)

        val streamId = soundPool.play(soundId, left, right, 1, 0, pitch)
        triggerHapticForSound(key)
        return streamId
    }

    fun playLoop(
        key: String,
        volume: Float = 1.0f,
        rate: Float = 1.0f,
        posX: Float = 0f,
        posY: Float = 0f,
        posZ: Float = 0f
    ) {
        val soundId = soundMap[key] ?: return
        val currentStream = activeStreams[key]
        val (left, right) = calculate3dVolume(posX, posY, posZ, volume)
        val pitch = rate.coerceIn(0.25f, 2.5f)

        if (currentStream != null && currentStream != 0) {
            soundPool.setVolume(currentStream, left, right)
            soundPool.setRate(currentStream, pitch)
        } else {
            val newStream = soundPool.play(soundId, left, right, 1, -1, pitch)
            if (newStream != 0) {
                activeStreams[key] = newStream
            }
        }
    }

    fun stopLoop(key: String) {
        val streamId = activeStreams.remove(key) ?: return
        try {
            soundPool.stop(streamId)
        } catch (_: Exception) {}
    }

    fun stopAllLoops() {
        for ((_, streamId) in activeStreams) {
            try {
                soundPool.stop(streamId)
            } catch (_: Exception) {}
        }
        activeStreams.clear()
        stopMusic()
    }

    fun startMusic(vol: Float = musicVolume) {
        if (isMusicPlaying) {
            setMusicVol(vol)
            return
        }
        try {
            var loaded = false
            for (filename in listOf("background.mp3", "0gk0a5.mp3")) {
                try {
                    val afd = context.assets.openFd("sounds/$filename")
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        isLooping = true
                        setVolume(vol, vol)
                        prepare()
                        start()
                    }
                    loaded = true
                    isMusicPlaying = true
                    break
                } catch (_: Exception) {}
            }
            if (!loaded) {
                // Synthesize subtle background ambient loop if asset not found
                playLoop("airplane", volume = vol * 0.15f, rate = 0.6f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing music", e)
        }
    }

    fun stopMusic() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
            isMusicPlaying = false
        } catch (_: Exception) {}
    }

    fun setMusicVol(vol: Float) {
        musicVolume = vol.coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(musicVolume, musicVolume)
        } catch (_: Exception) {}
    }

    fun updateTheme(theme: SoundTheme) {
        soundTheme = theme
    }

    private fun triggerHapticForSound(key: String) {
        if (!hapticsEnabled || vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (key) {
                    "fire", "shotgun", "pistol" -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    "damage" -> {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 40, 90), -1))
                    }
                    "hit", "enemyHit" -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(30, 180))
                    }
                    "gloowall" -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(60, 200))
                    }
                    "medkit" -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(35, 120))
                    }
                    "jump", "parachute", "land" -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(25, 100))
                    }
                    "win" -> {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 120, 60, 180), -1))
                    }
                    "lose" -> {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 200), -1))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                when (key) {
                    "fire", "shotgun", "damage" -> vibrator.vibrate(60)
                    "hit", "gloowall" -> vibrator.vibrate(35)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Synthesizes crisp, high quality PCM sound effects and writes to cache as WAV
     * when external assets are not yet present.
     */
    private fun generateSyntheticSoundFile(key: String): File? {
        val cacheDir = File(context.cacheDir, "synth_sounds")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val file = File(cacheDir, "${key}_${soundTheme.name.lowercase()}.wav")
        if (file.exists() && file.length() > 0) return file

        val sampleRate = 22050
        val pcm = when (key) {
            "fire" -> generateGunshotWave(sampleRate, 0.16f, 320f, 90f)
            "shotgun" -> generateGunshotWave(sampleRate, 0.28f, 180f, 60f)
            "pistol" -> generateGunshotWave(sampleRate, 0.12f, 440f, 140f)
            "damage" -> generateDamageWave(sampleRate, 0.25f)
            "hit", "enemyHit" -> generateHitWave(sampleRate, 0.10f)
            "click" -> generateBeepWave(sampleRate, 0.035f, 1200f)
            "jump" -> generateChirpWave(sampleRate, 0.20f, 220f, 660f)
            "land" -> generateNoiseThudWave(sampleRate, 0.25f, 85f)
            "parachute" -> generateWindWave(sampleRate, 0.5f)
            "airplane" -> generateEngineLoopWave(sampleRate, 1.2f, 95f)
            "walk" -> generateFootstepWave(sampleRate, 0.14f)
            "medkit" -> generateChimeWave(sampleRate, 0.35f, doubleArrayOf(523.25, 659.25, 783.99))
            "gloowall" -> generateWallClankWave(sampleRate, 0.3f)
            "ammo" -> generateBeepWave(sampleRate, 0.08f, 900f)
            "spend" -> generateCoinWave(sampleRate, 0.18f)
            "win" -> generateFanfareWave(sampleRate, 0.7f, true)
            "lose" -> generateFanfareWave(sampleRate, 0.7f, false)
            else -> generateBeepWave(sampleRate, 0.05f, 800f)
        }

        try {
            writeWavFile(file, pcm, sampleRate)
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing synthetic WAV for $key", e)
            return null
        }
    }

    private fun generateGunshotWave(sr: Int, duration: Float, startFreq: Float, endFreq: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        for (i in 0 until samples) {
            val progress = i.toFloat() / samples
            val decay = (1.0f - progress) * (1.0f - progress)
            val noise = (Math.random() * 2.0 - 1.0).toFloat()
            val freq = startFreq + (endFreq - startFreq) * progress
            val tone = sin(2.0 * Math.PI * freq * i / sr).toFloat()
            val mixed = (0.7f * noise + 0.3f * tone) * decay
            data[i] = (mixed * 30000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateFootstepWave(sr: Int, duration: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        for (i in 0 until samples) {
            val progress = i.toFloat() / samples
            val env = sin(progress * Math.PI.toFloat()) * (1f - progress)
            val noise = (Math.random() * 2.0 - 1.0).toFloat()
            val tone = sin(2.0 * Math.PI * 75.0 * i / sr).toFloat()
            val mixed = (0.4f * noise + 0.6f * tone) * env
            data[i] = (mixed * 24000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateDamageWave(sr: Int, duration: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        for (i in 0 until samples) {
            val progress = i.toFloat() / samples
            val env = (1.0f - progress)
            val tone = sin(2.0 * Math.PI * (160.0 - 90.0 * progress) * i / sr).toFloat()
            val noise = (Math.random() * 2.0 - 1.0).toFloat()
            val mixed = (tone * 0.7f + noise * 0.3f) * env
            data[i] = (mixed * 31000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateHitWave(sr: Int, duration: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        for (i in 0 until samples) {
            val progress = i.toFloat() / samples
            val env = (1.0f - progress) * (1.0f - progress)
            val tone = sin(2.0 * Math.PI * 650.0 * i / sr).toFloat()
            val noise = (Math.random() * 2.0 - 1.0).toFloat()
            data[i] = ((tone * 0.5f + noise * 0.5f) * env * 28000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateBeepWave(sr: Int, duration: Float, freq: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        for (i in 0 until samples) {
            val progress = i.toFloat() / samples
            val env = sin(progress * Math.PI.toFloat())
            val tone = sin(2.0 * Math.PI * freq * i / sr).toFloat()
            data[i] = (tone * env * 22000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateChirpWave(sr: Int, duration: Float, f1: Float, f2: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        for (i in 0 until samples) {
            val p = i.toFloat() / samples
            val freq = f1 + (f2 - f1) * p
            val env = sin(p * Math.PI.toFloat())
            val tone = sin(2.0 * Math.PI * freq * i / sr).toFloat()
            data[i] = (tone * env * 25000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateNoiseThudWave(sr: Int, duration: Float, freq: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        for (i in 0 until samples) {
            val p = i.toFloat() / samples
            val env = (1f - p) * (1f - p)
            val tone = sin(2.0 * Math.PI * freq * i / sr).toFloat()
            val noise = (Math.random() * 2.0 - 1.0).toFloat()
            data[i] = ((tone * 0.6f + noise * 0.4f) * env * 28000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateWindWave(sr: Int, duration: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        var last = 0f
        for (i in 0 until samples) {
            val p = i.toFloat() / samples
            val env = sin(p * Math.PI.toFloat())
            val white = (Math.random() * 2.0 - 1.0).toFloat()
            // simple lowpass
            last = last + 0.12f * (white - last)
            data[i] = (last * env * 24000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateEngineLoopWave(sr: Int, duration: Float, freq: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        for (i in 0 until samples) {
            val tone1 = sin(2.0 * Math.PI * freq * i / sr).toFloat()
            val tone2 = sin(2.0 * Math.PI * (freq * 1.5) * i / sr).toFloat()
            val noise = (Math.random() * 2.0 - 1.0).toFloat() * 0.25f
            data[i] = ((tone1 * 0.5f + tone2 * 0.3f + noise) * 20000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateChimeWave(sr: Int, duration: Float, freqs: DoubleArray): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        for (i in 0 until samples) {
            val p = i.toFloat() / samples
            val env = (1.0f - p)
            var sum = 0.0
            for (f in freqs) {
                sum += sin(2.0 * Math.PI * f * i / sr)
            }
            val norm = (sum / freqs.size).toFloat()
            data[i] = (norm * env * 26000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateWallClankWave(sr: Int, duration: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        for (i in 0 until samples) {
            val p = i.toFloat() / samples
            val env = (1f - p)
            val tone = sin(2.0 * Math.PI * 340.0 * i / sr).toFloat()
            val metallic = sin(2.0 * Math.PI * 1120.0 * i / sr).toFloat() * 0.4f
            val noise = (Math.random() * 2.0 - 1.0).toFloat() * 0.3f
            data[i] = ((tone + metallic + noise) * env * 26000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateCoinWave(sr: Int, duration: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        val f1 = 987.77 // B5
        val f2 = 1318.51 // E6
        val half = samples / 2
        for (i in 0 until samples) {
            val f = if (i < half) f1 else f2
            val p = (i % half).toFloat() / half
            val env = (1f - p)
            val tone = sin(2.0 * Math.PI * f * i / sr).toFloat()
            data[i] = (tone * env * 25000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateFanfareWave(sr: Int, duration: Float, isWin: Boolean): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        val chords = if (isWin) {
            doubleArrayOf(440.0, 554.37, 659.25, 880.0)
        } else {
            doubleArrayOf(392.0, 349.23, 311.13, 261.63)
        }
        val step = samples / chords.size
        for (i in 0 until samples) {
            val idx = (i / step).coerceIn(0, chords.size - 1)
            val f = chords[idx]
            val p = (i % step).toFloat() / step
            val env = 1.0f - p * 0.5f
            val tone = sin(2.0 * Math.PI * f * i / sr).toFloat()
            data[i] = (tone * env * 25000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun writeWavFile(file: File, pcm: ShortArray, sampleRate: Int) {
        val totalAudioLen = (pcm.size * 2).toLong()
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = (sampleRate * channels * 2).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 16 for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM format
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val byteBuffer = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) {
            byteBuffer.putShort(s)
        }

        FileOutputStream(file).use { fos ->
            fos.write(header)
            fos.write(byteBuffer.array())
        }
    }

    fun release() {
        stopAllLoops()
        soundPool.release()
    }
}
