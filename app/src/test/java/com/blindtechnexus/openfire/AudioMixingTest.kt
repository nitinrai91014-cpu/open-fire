package com.blindtechnexus.openfire

import android.content.Context
import android.media.SoundPool
import androidx.test.core.app.ApplicationProvider
import com.blindtechnexus.openfire.audio.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSoundPool
import java.util.concurrent.ConcurrentHashMap

/** Exercise repeated requests through the actual AudioManager/SoundPool path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioMixingTest {
    @Suppress("UNCHECKED_CAST")
    @Test
    fun repeatedIdenticalEffectsMixAndRequestsWaitDuringPause() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audio = AudioManager(context)
        try {
            val poolField = AudioManager::class.java.getDeclaredField("soundPool").apply { isAccessible = true }
            val pool = poolField.get(audio) as SoundPool
            val shadow = Shadow.extract<ShadowSoundPool>(pool)
            val path = context.cacheDir.resolve("mix-test.wav").absolutePath
            val id = pool.load(path, 1)
            val samples = AudioManager::class.java.getDeclaredField("soundMap").apply { isAccessible = true }
                .get(audio) as ConcurrentHashMap<String, Int>
            val ready = AudioManager::class.java.getDeclaredField("loadedSounds").apply { isAccessible = true }
                .get(audio) as MutableSet<Int>
            val sampleKey = "testSample"
            samples[sampleKey] = id
            ready.add(id)

            audio.playSound(sampleKey, volume = 0.7f)
            audio.playSound(sampleKey, volume = 0.7f)
            val first = shadow.getPathPlaybacks(path)
            assertEquals(2, first.size)
            assertEquals(first[0].leftVolume, first[1].leftVolume, 0.0001f)
            assertEquals(first[0].rightVolume, first[1].rightVolume, 0.0001f)

            audio.pauseAllSounds()
            audio.playSound(sampleKey, volume = 0.7f)
            assertEquals(2, shadow.getPathPlaybacks(path).size)
            audio.resumeAllSounds()
            assertEquals(3, shadow.getPathPlaybacks(path).size)
        } finally {
            audio.release()
        }
    }
}
