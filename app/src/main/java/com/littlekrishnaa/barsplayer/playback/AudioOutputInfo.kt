package com.littlekrishnaa.barsplayer.playback

data class AudioOutputInfo(
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channelCount: Int = 0,
    val encoding: String = "PCM",
    val isBitPerfect: Boolean = false,
    val outputDeviceName: String = "Speaker"
)
