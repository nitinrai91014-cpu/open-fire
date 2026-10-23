package com.blindtechnexus.openfire.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.blindtechnexus.openfire.model.Combat
import com.blindtechnexus.openfire.model.SoundTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance, reliable Audio Player Mixer architecture.
 *
 * Architecture:
 * 1. SoundPool engine for ultra-low latency short SFX (gunshots, reloads, hits, clicks, gloo wall).
 * 2. Managed MediaPlayer Channel Pool for streamed, long audio clips (win, lose, parachute, airplane, music, walk).
 *    This completely prevents SoundPool native buffer starvation, freezing, and premature cutoffs.
 * 3. 3D Positional Spatial Audio with realistic distance attenuation and azimuth stereo panning.
 * 4. Granular sound management: any sound or screen audio can be stopped instantly at any time.
 */
class AudioManager(private val context: Context) {
    companion object {
        private const val TAG = "OpenFireAudio"
        val LONG_TRACK_KEYS = setOf(
            "win",
            "lose",
            "parachute",
            "airplane",
            "airplaneEngine",
            "music",
            "walk",
            "playerWalk",
            "enemyWalk",
            // The missile loop must be stoppable the instant it impacts,
            // and the explosion is a multi-second tail: both stream.
            "missile",
            "explosion"
        )

        /** Pending play requests older than this are dropped (stale intent). */
        private const val PENDING_PLAY_TTL_MS = 5_000L

        /** Max queued play requests waiting for their sound to finish loading. */
        private const val MAX_PENDING_PLAYS = 32

        /** Max load attempts (asset retry + synthetic fallback) per sound key. */
        private const val MAX_LOAD_ATTEMPTS = 3
    }

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(32)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // SoundPool sound map and active streams
    private val soundMap = ConcurrentHashMap<String, Int>()
    private val loadedSounds = ConcurrentHashMap.newKeySet<Int>()
    private val activeSfxStreams = ConcurrentHashMap<String, MutableSet<Int>>()

    // Reliable playback pipeline: map every sample id back to its key, count
    // load attempts for retries, and queue play requests that arrive while a
    // sound is still loading so no sound is ever silently dropped.
    private val sampleToKey = ConcurrentHashMap<Int, String>()
    private val loadAttempts = ConcurrentHashMap<String, Int>()
    private val pendingLock = Any()
    private val pendingPlays = ArrayDeque<PendingPlay>()

    private data class PendingPlay(
        val key: String,
        val volume: Float,
        val rate: Float,
        val posX: Float,
        val posY: Float,
        val posZ: Float,
        val loop: Boolean,
        val enqueuedAt: Long
    )

    // Managed MediaPlayers for long / streaming audio
    private val managedPlayers = ConcurrentHashMap<String, MediaPlayer>()
    private val playerLock = Any()

    /**
     * User settings. These are the single source of truth and are NEVER
     * overwritten by a screen-scaled playback value — that was the cause of
     * the music volume reverting to the previous lower level.
     */
    var sfxVolume: Float = 1.0f
        set(value) {
            field = VolumeMixer.clamp(value)
        }

    var musicVolume: Float = 0.5f
        set(value) {
            field = VolumeMixer.clamp(value)
            applyMusicVolume()
        }

    /**
     * Walking footsteps play at the same level as the speech (TTS) volume,
     * so approaching and receding movement is always as clear as the voice
     * the player chose. Only the 3D distance gain is applied on top.
     */
    var walkVolume: Float = 1.0f
        set(value) {
            field = VolumeMixer.clamp(value)
        }

    var soundTheme: SoundTheme = SoundTheme.TACTICAL
    var hapticsEnabled: Boolean = true

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Canonical sound keys mapped to their asset files.
     *
     * Every entry points at a file that really exists in `assets/sounds`, and
     * every key is reused consistently across the whole codebase:
     *
     *  - a weapon hit plays the weapon's own hit sound (knife-hit, sword-hit
     *    or gun-hit) mixed with the sound of the fighter that was hit
     *    (enemy-hit or player-hit);
     *  - a knife or a sword that misses plays knife-miss or sword-miss;
     *  - the glue wall plays glue-sound when it is raised, glue-hit when it
     *    is hit and glue-wall-crack when it breaks;
     *  - the cover plays cover when it is raised, cover-hit when it is hit
     *    and cover-crack when it breaks;
     *  - the airplane menu mixes airplane with airplane-engine.
     */
    private val soundAliases = mapOf(
        "click" to listOf("click.mp3"),
        "gunReload" to listOf("gun-reload.mp3"),
        "reload" to listOf("gun-reload.mp3"),
        // Firearms.
        "assault" to listOf("assault-sound.mp3"),
        "pistol" to listOf("pistol-sound.mp3"),
        "shortGun" to listOf("short-gun.mp3"),
        "sniperRifle" to listOf("sniper-rifle.mp3"),
        "fire" to listOf("assault-sound.mp3"),
        "shotgun" to listOf("short-gun.mp3"),
        // Melee weapons.
        "knifeHit" to listOf("knife-hit.mp3"),
        "knifeMiss" to listOf("knife-miss.mp3"),
        "knifeSwitch" to listOf("knife-switch.mp3"),
        "knifeSwish" to listOf("knife-switch.mp3"),
        "swordHit" to listOf("sword-hit.mp3"),
        "swordMiss" to listOf("sword-miss.mp3"),
        // There is no sword-glide asset: the sword swing uses the same whoosh
        // as the sword switch, exactly like the knife uses knife-switch.
        "swordGlide" to listOf("sword-switch.mp3"),
        "swordSwitch" to listOf("sword-switch.mp3"),
        // Barriers and defences.
        "glueSound" to listOf("glue-sound.mp3"),
        "glueHit" to listOf("glue-hit.mp3"),
        "glueWallCrack" to listOf("glue-wall-crack.mp3"),
        "cover" to listOf("cover.mp3"),
        "coverHit" to listOf("cover-hit.mp3"),
        "coverCrack" to listOf("cover-crack.mp3"),
        "gloowall" to listOf("glue-sound.mp3"),
        "medkit" to listOf("medkit.mp3"),
        // Impacts: the weapon sound plus the fighter that was hit.
        "gunHit" to listOf("gun-hit.mp3"),
        "enemyHit" to listOf("enemy-hit.mp3"),
        "playerHit" to listOf("player-hit.mp3"),
        "ammo" to listOf("ammo.mp3"),
        "spend" to listOf("spend-sound.mp3"),
        // Flight and results.
        "jump" to listOf("jump.mp3"),
        "land" to listOf("jump.mp3"),
        "airplane" to listOf("airplane.mp3"),
        "airplaneEngine" to listOf("airplane-engine.mp3"),
        "parachute" to listOf("parachute.mp3"),
        "win" to listOf("win.mp3"),
        "lose" to listOf("lose.mp3"),
        "music" to listOf("music.mp3"),
        "walk" to listOf("walk.mp3"),
        // Two independent players mix the same footstep asset: the local
        // player stays centred while Robert is attenuated by his 3D distance.
        "playerWalk" to listOf("walk.mp3"),
        "enemyWalk" to listOf("walk.mp3"),
        // Guided missile: the launch flight sound (looped while the target
        // flees) and the impact explosion, always mixed with enemy-hit or
        // player-hit for the destroyed fighter.
        "missile" to listOf("missile-sound.mp3"),
        "explosion" to listOf("explosion-sound.mp3")
    )

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            val key = sampleToKey[sampleId]
            if (status == 0) {
                loadedSounds.add(sampleId)
                if (key != null) {
                    flushPendingPlays(key)
                }
            } else if (key != null) {
                handleLoadFailure(key, sampleId)
            } else {
                loadedSounds.remove(sampleId)
            }
        }
        preloadShortSounds()
    }

    private fun preloadShortSounds() {
        CoroutineScope(Dispatchers.IO).launch {
            for ((key, files) in soundAliases) {
                // Preload only short SFX into SoundPool. Long audio tracks are streamed via MediaPlayer on-demand.
                if (key in LONG_TRACK_KEYS) continue
                loadShortSound(key, files, allowSynthetic = true)
            }
        }
    }

    /**
     * Loads one short SFX into the SoundPool from the first asset file that
     * opens, optionally falling back to a procedurally generated WAV so the
     * key is always playable.
     */
    private fun loadShortSound(key: String, files: List<String>, allowSynthetic: Boolean) {
        for (fileName in files) {
            try {
                val afd = context.assets.openFd("sounds/$fileName")
                val soundId = soundPool.load(afd, 1)
                if (soundId != 0) {
                    soundMap[key] = soundId
                    sampleToKey[soundId] = key
                    return
                }
            } catch (_: Exception) {}
        }
        if (allowSynthetic) {
            try {
                val synthFile = generateSyntheticSoundFile(key)
                if (synthFile != null && synthFile.exists()) {
                    val soundId = soundPool.load(synthFile.absolutePath, 1)
                    if (soundId != 0) {
                        soundMap[key] = soundId
                        sampleToKey[soundId] = key
                        return
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating fallback for $key", e)
            }
        }
    }

    /**
     * A sample failed to decode/load inside SoundPool. Retry once from the
     * asset files, then once from the synthetic generator, then give up —
     * this guarantees sounds like reload/money-spend keep playing even if a
     * single decode hiccups, instead of failing silently forever.
     */
    private fun handleLoadFailure(key: String, failedSampleId: Int) {
        loadedSounds.remove(failedSampleId)
        soundMap.remove(key, failedSampleId)
        val attempts = loadAttempts.merge(key, 1) { old, one -> old + one } ?: 1
        if (attempts >= MAX_LOAD_ATTEMPTS) {
            Log.e(TAG, "Giving up loading sound '$key' after $attempts attempts")
            return
        }
        val files = soundAliases[key] ?: emptyList()
        CoroutineScope(Dispatchers.IO).launch {
            if (attempts <= 1) {
                loadShortSound(key, files, allowSynthetic = false)
            } else {
                loadShortSound(key, files, allowSynthetic = true)
            }
        }
    }

    /**
     * Kicks off an on-demand load when a sound is requested before the
     * preload pass has mapped it (or after a failed load was cleared).
     */
    private fun maybeLoadOnDemand(key: String) {
        if (key in LONG_TRACK_KEYS) return
        if (soundMap.containsKey(key)) return
        val files = soundAliases[key] ?: return
        val attempts = loadAttempts[key] ?: 0
        if (attempts >= MAX_LOAD_ATTEMPTS) return
        CoroutineScope(Dispatchers.IO).launch {
            if (!soundMap.containsKey(key)) {
                loadShortSound(key, files, allowSynthetic = true)
            }
        }
    }

    private fun queuePendingPlay(pending: PendingPlay) {
        synchronized(pendingLock) {
            val now = System.currentTimeMillis()
            pendingPlays.removeAll { now - it.enqueuedAt > PENDING_PLAY_TTL_MS }
            if (pendingPlays.size >= MAX_PENDING_PLAYS) {
                pendingPlays.removeFirstOrNull()
            }
            pendingPlays.addLast(pending)
        }
    }

    /** Replays (as a simultaneous mix) every queued request for [key]. */
    private fun flushPendingPlays(key: String) {
        val now = System.currentTimeMillis()
        val ready = synchronized(pendingLock) {
            val matched = pendingPlays.filter { it.key == key }
            pendingPlays.removeAll(matched.toSet())
            matched
        }
        for (pending in ready) {
            if (now - pending.enqueuedAt > PENDING_PLAY_TTL_MS) continue
            if (pending.loop) {
                playLoop(pending.key, pending.volume, pending.rate, pending.posX, pending.posY, pending.posZ)
            } else {
                playSound(pending.key, pending.volume, pending.rate, pending.posX, pending.posY, pending.posZ)
            }
        }
    }

    private fun dropPendingPlays(key: String) {
        synchronized(pendingLock) {
            pendingPlays.removeAll { it.key == key }
        }
    }

    private fun isReady(key: String): Boolean {
        val soundId = soundMap[key] ?: return false
        return soundId in loadedSounds
    }

    private fun getAssetFd(key: String): AssetFileDescriptor? {
        val files = soundAliases[key] ?: return null
        for (fileName in files) {
            try {
                return context.assets.openFd("sounds/$fileName")
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * 3D spatial panning & distance attenuation.
     * x: -100 (far left) to +100 (far right)
     * z: distance away along line of sight (devil approaches from 100 to 0)
     *
     * The player and Robert fight head-on: any sound with no real sideways
     * offset is centred exactly (full volume in both ears, like the speech
     * voice), so running straight forward or backward never smears the
     * image between the channels. Only a genuine sideways offset pans,
     * and even then gently.
     */
    fun calculate3dVolume(
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        baseVol: Float = 1.0f
    ): Pair<Float, Float> {
        val dist = sqrt(x * x + y * y + z * z)
        // Smooth inverse distance attenuation
        val distGain = 1.0f / (1.0f + 0.022f * max(0f, dist - 5f))
        val clampedDistGain = distGain.coerceIn(0.10f, 1.0f)

        val (leftGain, rightGain) = VolumeMixer.panGains(VolumeMixer.panFor(x, dist))

        val totalVol = VolumeMixer.sfx(sfxVolume, baseVol, clampedDistGain)
        val left = (totalVol * leftGain).coerceIn(0f, 1f)
        val right = (totalVol * rightGain).coerceIn(0f, 1f)
        return Pair(left, right)
    }

    /**
     * Plays a sound effect with optional 3D spatial coordinates.
     * Routes short sounds through SoundPool and long sounds through managed MediaPlayer.
     *
     * If the underlying sample has not finished loading yet, the request is
     * queued and played automatically the moment the sound is ready, so a
     * requested effect is never silently dropped.
     */
    fun playSound(
        key: String,
        volume: Float = 1.0f,
        rate: Float = 1.0f,
        posX: Float = 0f,
        posY: Float = 0f,
        posZ: Float = 0f
    ): Int {
        if (key in LONG_TRACK_KEYS) {
            playManagedTrack(key, volume, posX, posY, posZ, loop = false)
            return 1
        }

        if (!isReady(key)) {
            queuePendingPlay(PendingPlay(key, volume, rate, posX, posY, posZ, loop = false, enqueuedAt = System.currentTimeMillis()))
            maybeLoadOnDemand(key)
            return -1
        }

        val soundId = soundMap[key] ?: return -1
        val (left, right) = calculate3dVolume(posX, posY, posZ, volume)
        val pitch = when (soundTheme) {
            SoundTheme.CYBER -> rate * 1.10f
            SoundTheme.STEALTH -> rate * 0.92f
            else -> rate
        }.coerceIn(0.5f, 2.0f)

        val streamId = soundPool.play(soundId, left, right, 1, 0, pitch)
        if (streamId != 0) {
            activeSfxStreams.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(streamId)
        } else {
            // The pool could not hand out a stream right now — retry on the next load tick.
            queuePendingPlay(PendingPlay(key, volume, rate, posX, posY, posZ, loop = false, enqueuedAt = System.currentTimeMillis()))
            return -1
        }
        triggerHapticForSound(key)
        return streamId
    }

    /**
     * Plays several sound effects at the same time so they are mixed together
     * (e.g. the coin "money spent" sound together with the reload sound).
     */
    fun playMixed(keys: List<String>, volume: Float = 1.0f, posX: Float = 0f, posY: Float = 0f, posZ: Float = 0f) {
        for (key in keys) {
            playSound(key, volume = volume, posX = posX, posY = posY, posZ = posZ)
        }
    }

    /**
     * Plays a looping sound effect.
     */
    fun playLoop(
        key: String,
        volume: Float = 1.0f,
        rate: Float = 1.0f,
        posX: Float = 0f,
        posY: Float = 0f,
        posZ: Float = 0f
    ) {
        if (key in LONG_TRACK_KEYS) {
            playManagedTrack(key, volume, posX, posY, posZ, loop = true)
            return
        }

        if (!isReady(key)) {
            queuePendingPlay(PendingPlay(key, volume, rate, posX, posY, posZ, loop = true, enqueuedAt = System.currentTimeMillis()))
            maybeLoadOnDemand(key)
            return
        }

        val soundId = soundMap[key] ?: return
        val (left, right) = calculate3dVolume(posX, posY, posZ, volume)
        val pitch = rate.coerceIn(0.5f, 2.0f)
        val streamId = soundPool.play(soundId, left, right, 1, -1, pitch)
        if (streamId != 0) {
            activeSfxStreams.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(streamId)
        }
    }

    /**
     * Live footstep mix state: when each track last refreshed its volume
     * (every 0.5 s, or every 0.25 s while running fast) and the gains it
     * currently plays at, so approach / retreat swells stay smooth instead
     * of stepping harshly on every movement tick.
     */
    private val walkLastApplyAt = ConcurrentHashMap<String, Long>()
    private val walkCurrentGains = ConcurrentHashMap<String, Pair<Float, Float>>()

    /**
     * Updates only Robert's positional walking loop. His footsteps sit at
     * the speech level, attenuated once by his real distance: louder as he
     * approaches, quieter as he retreats, centred while he runs straight
     * and panned only by a genuine sideways offset. The playback rate
     * follows his movement speed in metres per second.
     */
    fun updateEnemyWalk3dAudio(
        distance: Float,
        lateralOffset: Float = 0f,
        speedMps: Float = 0f,
        fleeing: Boolean = false
    ) {
        val safeDistance = distance.coerceIn(0f, Combat.MAX_RANGE)
        // Legacy callers without a speed still get a sensible rate.
        val effectiveSpeed = when {
            speedMps > 0f -> speedMps
            fleeing -> 12f
            else -> 5f
        }
        val base = walkVolume * VolumeMixer.walkDistanceGain(safeDistance, Combat.MAX_RANGE)
        val (panLeft, panRight) = VolumeMixer.panGains(
            VolumeMixer.panFor(lateralOffset, safeDistance)
        )
        updateWalkingTrack(
            key = "enemyWalk",
            left = (base * panLeft).coerceIn(0f, 1f),
            right = (base * panRight).coerceIn(0f, 1f),
            speedMps = effectiveSpeed
        )
    }

    /**
     * The local player's own footsteps stay centred at the speech level
     * regardless of Robert's distance, exactly as heard while running
     * yourself. Only the playback rate follows the running speed (which
     * itself follows how strongly the screen is pressed).
     */
    fun updatePlayerWalkAudio(speedMps: Float = 6.5f) {
        updateWalkingTrack(
            key = "playerWalk",
            left = walkVolume,
            right = walkVolume,
            speedMps = speedMps
        )
    }

    private fun updateWalkingTrack(key: String, left: Float, right: Float, speedMps: Float) {
        synchronized(playerLock) {
            var player = managedPlayers[key]
            if (player == null || !player.isPlaying) {
                walkLastApplyAt.remove(key)
                walkCurrentGains.remove(key)
                playManagedTrack(key, volume = 1.0f, posX = 0f, posY = 0f, posZ = 0f, loop = true)
                player = managedPlayers[key] ?: return
            }
            // Refresh the live volume every 0.5 s (0.25 s at a fast run);
            // between refreshes the player simply keeps its current level.
            val now = System.currentTimeMillis()
            val lastApplied = walkLastApplyAt[key]
            if (lastApplied != null && now - lastApplied < VolumeMixer.walkUpdateIntervalMs(speedMps)) {
                return
            }
            // Glide toward the target gains so distance changes sound like
            // continuous approach / retreat, never like volume steps.
            val current = walkCurrentGains[key]
            val smoothed = if (current == null) {
                Pair(left, right)
            } else {
                Pair(
                    current.first + (left - current.first) * 0.6f,
                    current.second + (right - current.second) * 0.6f
                )
            }
            try {
                player.setVolume(smoothed.first.coerceIn(0f, 1f), smoothed.second.coerceIn(0f, 1f))
            } catch (_: Exception) {}
            applyWalkRate(player, speedMps)
            walkCurrentGains[key] = smoothed
            walkLastApplyAt[key] = now
        }
    }

    /** Stops both independent footstep channels (and the legacy channel). */
    fun stopFootsteps() {
        stopSound("walk")
        stopSound("playerWalk")
        stopSound("enemyWalk")
    }

    /** Faster movement plays the steps faster; slow movement slows them. */
    private fun applyWalkRate(player: MediaPlayer, speedMps: Float) {
        try {
            player.playbackParams = PlaybackParams()
                .setSpeed(VolumeMixer.walkRate(speedMps))
        } catch (_: Exception) {}
    }

    /**
     * Streams longer audio tracks via isolated, managed MediaPlayers.
     */
    private fun playManagedTrack(
        key: String,
        volume: Float,
        posX: Float,
        posY: Float,
        posZ: Float,
        loop: Boolean
    ) {
        stopManagedTrack(key)

        val afd = getAssetFd(key)
        // Music is not spatialised and does not use the SFX slider: the caller
        // passes the already mixed absolute volume (setting * background gain).
        val (left, right) = if (key == "music") {
            val v = VolumeMixer.clamp(volume)
            Pair(v, v)
        } else {
            calculate3dVolume(posX, posY, posZ, volume)
        }

        try {
            val player = MediaPlayer()
            if (afd != null) {
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } else {
                val synth = generateSyntheticSoundFile(key) ?: return
                player.setDataSource(synth.absolutePath)
            }

            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.isLooping = loop
            player.setVolume(left, right)
            player.setOnCompletionListener {
                if (!loop) {
                    stopManagedTrack(key)
                }
            }
            player.prepare()
            player.start()
            synchronized(playerLock) {
                managedPlayers[key] = player
            }
            // Footstep volume / rate are owned by updateWalkingTrack, which
            // applies them immediately after starting a track.
            triggerHapticForSound(key)
        } catch (e: Exception) {
            Log.e(TAG, "Failed playing managed track $key", e)
        }
    }

    /**
     * Immediately stops playback of a specific sound.
     */
    fun stopSound(key: String) {
        dropPendingPlays(key)
        walkLastApplyAt.remove(key)
        walkCurrentGains.remove(key)
        stopManagedTrack(key)
        val streams = activeSfxStreams.remove(key)
        if (streams != null) {
            for (streamId in streams) {
                try {
                    soundPool.stop(streamId)
                } catch (_: Exception) {}
            }
        }
    }

    fun stopLoop(key: String) {
        stopSound(key)
    }

    private fun stopManagedTrack(key: String) {
        synchronized(playerLock) {
            val player = managedPlayers.remove(key) ?: return
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Stops all active screen-transition and long sounds (victory, defeat, parachute, airplane, walk).
     * This fixes the issue where victory, defeat, or parachute sounds continue playing after navigating away.
     */
    fun stopScreenAudio() {
        stopSound("win")
        stopSound("lose")
        stopSound("parachute")
        stopSound("airplane")
        stopSound("airplaneEngine")
        stopFootsteps()
        stopSound("missile")
        stopSound("explosion")
        stopSound("jump")
        stopSound("land")
    }

    /**
     * Stops all loops and battle sounds without stopping background music.
     */
    fun stopAllLoops() {
        stopScreenAudio()
        for ((key, streams) in activeSfxStreams) {
            if (key == "music") continue
            for (streamId in streams) {
                try {
                    soundPool.stop(streamId)
                } catch (_: Exception) {}
            }
        }
        activeSfxStreams.clear()
    }

    /**
     * Stops all sounds completely including music.
     */
    fun stopAllSounds() {
        stopAllLoops()
        stopMusic()
    }

    /**
     * Starts (or keeps) the looping background music at the volume derived
     * from the user's music setting. Calling this again never changes the
     * stored setting, so switching screens can no longer lower the volume.
     */
    fun startMusic() {
        val current = managedPlayers["music"]
        if (current != null && current.isPlaying) {
            applyMusicVolume()
            return
        }
        playManagedTrack(
            "music",
            volume = VolumeMixer.music(musicVolume),
            posX = 0f,
            posY = 0f,
            posZ = 0f,
            loop = true
        )
    }

    fun stopMusic() {
        stopManagedTrack("music")
    }

    // The property setter above is the single entry point for changing the
    // music setting: it clamps the value and immediately applies it. A
    // separate setMusicVolume() function would clash with the generated JVM
    // setter for the musicVolume property.

    /** Re-applies the stored music setting to the live player. */
    private fun applyMusicVolume() {
        val player = managedPlayers["music"] ?: return
        val v = VolumeMixer.music(musicVolume)
        try {
            player.setVolume(v, v)
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
                    "assault", "shortGun", "pistol", "sniperRifle", "fire", "shotgun" -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    "gunReload", "reload" -> {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 40, 50), -1))
                    }
                    "playerHit" -> {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 40, 90), -1))
                    }
                    "gunHit", "enemyHit", "knifeHit", "swordHit", "glueHit", "coverHit" -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(30, 180))
                    }
                    "glueWallCrack", "coverCrack" -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(80, 220))
                    }
                    "glueSound", "cover", "gloowall" -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(60, 200))
                    }
                    "medkit" -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(35, 120))
                    }
                    "jump", "parachute", "land" -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(25, 100))
                    }
                    "missile" -> {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 40, 60, 40, 90), -1))
                    }
                    "explosion" -> {
                        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 140, 60, 200, 60, 260), -1))
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
                    "assault", "shortGun", "pistol", "playerHit" -> vibrator.vibrate(60)
                    "gunHit", "glueSound", "cover", "gunReload" -> vibrator.vibrate(35)
                }
            }
        } catch (_: Exception) {}
    }

    private fun generateSyntheticSoundFile(key: String): File? {
        val cacheDir = File(context.cacheDir, "synth_sounds")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val file = File(cacheDir, "${key}_${soundTheme.name.lowercase()}.wav")
        if (file.exists() && file.length() > 0) return file

        val sampleRate = 22050
        val pcm = when (key) {
            "assault", "fire" -> generateGunshotWave(sampleRate, 0.16f, 320f, 90f)
            "shortGun", "shotgun" -> generateGunshotWave(sampleRate, 0.28f, 180f, 60f)
            "pistol" -> generateGunshotWave(sampleRate, 0.12f, 440f, 140f)
            "sniperRifle" -> generateGunshotWave(sampleRate, 0.34f, 260f, 70f)
            "gunReload", "reload" -> generateReloadWave(sampleRate, 0.45f)
            "knifeHit", "knifeMiss", "swordHit", "swordMiss" -> generateHitWave(sampleRate, 0.10f)
            "knifeSwish", "knifeSwitch", "swordGlide", "swordSwitch" ->
                generateChirpWave(sampleRate, 0.16f, 900f, 320f)
            "playerHit" -> generateDamageWave(sampleRate, 0.25f)
            "gunHit", "enemyHit", "coverHit", "glueHit" -> generateHitWave(sampleRate, 0.10f)
            "glueWallCrack", "coverCrack" -> generateNoiseThudWave(sampleRate, 0.30f, 160f)
            "click" -> generateBeepWave(sampleRate, 0.035f, 1200f)
            "jump" -> generateChirpWave(sampleRate, 0.20f, 220f, 660f)
            "land" -> generateNoiseThudWave(sampleRate, 0.25f, 85f)
            "parachute" -> generateWindWave(sampleRate, 0.5f)
            "airplane" -> generateEngineLoopWave(sampleRate, 1.2f, 95f)
            "airplaneEngine" -> generateEngineLoopWave(sampleRate, 1.6f, 72f)
            "walk", "playerWalk", "enemyWalk" -> generateFootstepWave(sampleRate, 0.14f)
            "medkit" -> generateChimeWave(sampleRate, 0.35f, doubleArrayOf(523.25, 659.25, 783.99))
            "glueSound", "gloowall" -> generateWallClankWave(sampleRate, 0.3f)
            "cover" -> generateCoverWave(sampleRate, 0.5f)
            "ammo" -> generateBeepWave(sampleRate, 0.08f, 900f)
            "spend" -> generateCoinWave(sampleRate, 0.18f)
            "missile" -> generateChirpWave(sampleRate, 1.2f, 300f, 1400f)
            "explosion" -> generateNoiseThudWave(sampleRate, 0.9f, 70f)
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

    private fun generateReloadWave(sr: Int, duration: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        for (i in 0 until samples) {
            val progress = i.toFloat() / samples
            val env = sin(progress * Math.PI.toFloat())
            val noise = (Math.random() * 2.0 - 1.0).toFloat()
            val tone = sin(2.0 * Math.PI * (500.0 + 300.0 * progress) * i / sr).toFloat()
            val mixed = (0.6f * noise + 0.4f * tone) * env
            data[i] = (mixed * 26000).toInt().coerceIn(-32767, 32767).toShort()
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

    /**
     * Cover / Gloo Wall deployment: a low slam, a rising energy whoosh and a
     * metallic shimmer that locks the wall in place.
     */
    private fun generateCoverWave(sr: Int, duration: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        val thunkLen = (sr * 0.18f).toInt()
        val whooshStart = (sr * 0.03f).toInt()
        val whooshEnd = (sr * 0.40f).toInt()
        val shimmerStart = (sr * 0.12f).toInt()
        for (i in 0 until samples) {
            val t = i.toFloat() / sr
            var v = 0f

            if (i < thunkLen) {
                val env = kotlin.math.exp(-t * 26f)
                val freq = 145f - 120f * (i.toFloat() / thunkLen)
                v += 0.85f * sin(2.0 * Math.PI * freq * t).toFloat() * env
            }

            if (i in whooshStart until whooshEnd) {
                val p = (i - whooshStart).toFloat() / (whooshEnd - whooshStart)
                val env = sin(p * Math.PI.toFloat()) * (0.35f + 0.65f * p)
                val freq = 320f + 1150f * p
                val noise = (Math.random() * 2.0 - 1.0).toFloat()
                v += 0.34f * env * (0.55f * sin(2.0 * Math.PI * freq * t).toFloat() + 0.27f * noise)
            }

            if (i >= shimmerStart) {
                val p = (i - shimmerStart).toFloat() / (samples - shimmerStart)
                val env = kotlin.math.exp(-p * 4.2f)
                v += 0.30f * env * (
                    0.5f * sin(2.0 * Math.PI * 988.0 * t).toFloat() +
                        0.35f * sin(2.0 * Math.PI * 1318.5 * t).toFloat() +
                        0.25f * sin(2.0 * Math.PI * 1568.0 * t).toFloat()
                    )
            }

            data[i] = (kotlin.math.tanh(v * 1.15f) * 28000f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return data
    }

    private fun generateCoinWave(sr: Int, duration: Float): ShortArray {
        val samples = (sr * duration).toInt()
        val data = ShortArray(samples)
        val f1 = 987.77
        val f2 = 1318.51
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
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
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
        header[32] = (channels * 2).toByte()
        header[33] = 0
        header[34] = 16
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
        stopAllSounds()
        soundPool.release()
    }
}
