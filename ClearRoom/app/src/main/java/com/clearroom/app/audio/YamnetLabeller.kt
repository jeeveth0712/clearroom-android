package com.clearroom.app.audio

import android.content.Context
import com.clearroom.app.daylog.SoundLabel
import com.clearroom.app.daylog.SoundLabeller
import com.clearroom.app.daylog.SoundLabels
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions

/**
 * Sound labels from Google's YAMNet model, run on the phone by MediaPipe Tasks Audio (FR-9). The
 * model ships inside the app (assets/yamnet.tflite), so nothing goes online. MediaPipe resamples
 * the clip to the 16 kHz YAMNet expects. One labeller per log; [close] frees its native memory.
 */
class YamnetLabeller private constructor(private val classifier: AudioClassifier) : SoundLabeller {

    override fun label(clip: AudioClip): SoundLabel? = try {
        val format = AudioData.AudioDataFormat.builder().setNumOfChannels(1).setSampleRate(clip.sampleRate.toFloat()).build()
        val data = AudioData.create(format, clip.samples.size).apply { load(clip.samples) }
        val top = classifier.classify(data).classificationResults().firstOrNull()
            ?.classifications()?.firstOrNull()
            ?.categories()?.firstOrNull()
        top?.let { SoundLabels.fromYamnet(it.categoryName()) }
    } catch (e: RuntimeException) {
        null // one unlabelled second is better than a stopped log
    }

    override fun close() = classifier.close()

    companion object {
        const val MODEL_ASSET = "yamnet.tflite"

        /** Labels below this score are left out: a guess is worse than no label. */
        private const val SCORE_THRESHOLD = 0.3f

        /** Null when the model or MediaPipe's native code can't load; the log then keeps levels only (doc fallback). */
        fun create(context: Context): YamnetLabeller? = try {
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setRunningMode(RunningMode.AUDIO_CLIPS)
                .setMaxResults(1)
                .setScoreThreshold(SCORE_THRESHOLD)
                .build()
            YamnetLabeller(AudioClassifier.createFromOptions(context, options))
        } catch (e: RuntimeException) {
            null
        } catch (e: LinkageError) {
            null // the native library is missing for this CPU
        }
    }
}
