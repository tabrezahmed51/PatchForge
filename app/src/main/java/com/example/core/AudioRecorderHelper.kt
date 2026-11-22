package com.example.core

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

private const val REC_TAG = "AudioRecorderHelper"

class AudioRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): Result<File> {
        return try {
            stopAndRelease()
            val file = File(context.cacheDir, "patchforge_voice_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(64_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            Result.success(file)
        } catch (e: Exception) {
            Log.e(REC_TAG, "Failed to start audio recording", e)
            stopAndRelease()
            Result.failure(e)
        }
    }

    fun stopAndReadBytes(): Result<ByteArray> {
        val file = outputFile
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            if (file != null && file.exists() && file.length() > 0L) {
                val bytes = file.readBytes()
                file.delete()
                outputFile = null
                Result.success(bytes)
            } else {
                Result.failure(IllegalStateException("Recorded audio clip was empty."))
            }
        } catch (e: Exception) {
            Log.e(REC_TAG, "Failed to stop audio recording", e)
            stopAndRelease()
            Result.failure(e)
        }
    }

    fun stopAndRelease() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
