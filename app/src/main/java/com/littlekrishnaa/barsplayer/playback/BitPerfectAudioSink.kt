package com.littlekrishnaa.barsplayer.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioTrack
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class BitPerfectAudioSink(
    private val context: Context,
    private val audioManager: AudioManager
) : AudioSink by DefaultAudioSink.Builder(context).build() {

    private val _outputInfo = MutableStateFlow(AudioOutputInfo())
    val outputInfo: StateFlow<AudioOutputInfo> = _outputInfo.asStateFlow()

    fun updateAudioTrackConfiguration(audioTrack: AudioTrack?, format: Format?) {
        if (audioTrack == null) return

        val sampleRate = format?.sampleRate ?: audioTrack.sampleRate
        val pcmEncoding = format?.pcmEncoding ?: C.ENCODING_PCM_16BIT
        val channelCount = format?.channelCount ?: audioTrack.channelCount

        val bitDepth = when (pcmEncoding) {
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
            else -> 16
        }

        var isBitPerfectActive = false
        var deviceName = "Internal Speaker / Headset"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            isBitPerfectActive = applyBitPerfectIfSupported(audioTrack)
        }

        val routedDevice = audioTrack.routedDevice
        if (routedDevice != null) {
            deviceName = "${routedDevice.productName} (${getDeviceTypeName(routedDevice.type)})"
        }

        _outputInfo.value = AudioOutputInfo(
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channelCount = channelCount,
            encoding = if (pcmEncoding == C.ENCODING_PCM_FLOAT) "PCM_FLOAT" else "PCM_$bitDepth-bit",
            isBitPerfect = isBitPerfectActive,
            outputDeviceName = deviceName
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun applyBitPerfectIfSupported(audioTrack: AudioTrack): Boolean {
        try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val usbDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }

            if (usbDevice != null) {
                val supportedMixerAttrs = audioManager.getSupportedMixerAttributes(usbDevice)
                val bitPerfectAttr = supportedMixerAttrs.firstOrNull {
                    it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
                }

                if (bitPerfectAttr != null) {
                    audioTrack.format?.let { trackFormat ->
                        val mixerAttr = AudioMixerAttributes.Builder(trackFormat)
                            .setMixerBehavior(AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT)
                            .build()
                        audioManager.setPreferredMixerAttributes(
                            AudioAttributes.Builder().build(),
                            usbDevice,
                            mixerAttr
                        )
                        return true
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback if device vendor doesn't support MIXER_BEHAVIOR_BIT_PERFECT
        }
        return false
    }

    private fun getDeviceTypeName(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB DAC"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
            else -> "Audio Out"
        }
    }
}
