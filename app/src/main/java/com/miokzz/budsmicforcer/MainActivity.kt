package com.miokzz.budsmicforcer

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.sqrt

class MainActivity : Activity() {

    private lateinit var audioManager: AudioManager
    private lateinit var status: TextView
    private lateinit var meter: ProgressBar

    @Volatile
    private var testing = false

    private var audioRecord: AudioRecord? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        requestNeededPermissions()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "Buds Mic Lab v0.3"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "Testa se o Android consegue capturar áudio diretamente do microfone Bluetooth dos Buds."
            textSize = 16f
            setPadding(0, 16, 0, 30)
        })

        status = TextView(this).apply {
            textSize = 16f
            text = "Status: parado"
            setPadding(0, 0, 0, 20)
        }
        root.addView(status)

        root.addView(TextView(this).apply {
            text = "Nível do microfone"
            textSize = 14f
        })

        meter = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        root.addView(
            meter,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                36
            )
        )

        root.addView(Button(this).apply {
            text = "TESTAR MICROFONE DOS BUDS"
            setOnClickListener { startMicTest() }
        })

        root.addView(Button(this).apply {
            text = "PARAR TESTE / RESTAURAR"
            setOnClickListener { stopMicTest() }
        })

        root.addView(Button(this).apply {
            text = "ATUALIZAR DISPOSITIVOS"
            setOnClickListener { refreshStatus() }
        })

        setContentView(root)
        refreshStatus()
    }

    private fun requestNeededPermissions() {
        val missing = mutableListOf<String>()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missing += Manifest.permission.RECORD_AUDIO
        }

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            missing += Manifest.permission.BLUETOOTH_CONNECT
        }

        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 100)
        }
    }

    private fun bluetoothCommunicationDevices(): List<AudioDeviceInfo> {
        return try {
            audioManager.availableCommunicationDevices.filter {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun startMicTest() {
        if (testing) return

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNeededPermissions()
            status.text = "Permissões necessárias ainda não foram concedidas."
            return
        }

        val bt = bluetoothCommunicationDevices().firstOrNull()

        if (bt == null) {
            status.text = "Nenhum headset Bluetooth de comunicação encontrado. Conecte os Buds e tente novamente."
            return
        }

        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuffer <= 0) {
            status.text = "Falha ao obter buffer de gravação."
            return
        }

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            val routed = audioManager.setCommunicationDevice(bt)

            val recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .build()

            val preferred = recorder.setPreferredDevice(bt)

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                audioManager.clearCommunicationDevice()
                audioManager.mode = AudioManager.MODE_NORMAL
                status.text = "AudioRecord não inicializou."
                return
            }

            audioRecord = recorder
            testing = true
            recorder.startRecording()

            status.text =
                "TESTANDO ✓\n" +
                "Dispositivo: ${bt.productName}\n" +
                "setCommunicationDevice: $routed\n" +
                "setPreferredDevice: $preferred\n" +
                "Fale perto dos Buds. A barra deve reagir."

            Thread {
                val buffer = ShortArray(minBuffer)
                while (testing) {
                    val read = try {
                        recorder.read(buffer, 0, buffer.size)
                    } catch (_: Exception) {
                        -1
                    }

                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            val v = buffer[i].toDouble()
                            sum += v * v
                        }

                        val rms = sqrt(sum / read)
                        val level = ((rms / 9000.0) * 100.0).toInt().coerceIn(0, 100)

                        mainHandler.post {
                            meter.progress = level
                        }
                    }
                }
            }.start()

        } catch (e: Exception) {
            stopMicTest()
            status.text = "Erro ao iniciar teste: ${e.javaClass.simpleName}: ${e.message ?: "sem detalhes"}"
        }
    }

    private fun stopMicTest() {
        testing = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }

        audioRecord = null

        try {
            audioManager.clearCommunicationDevice()
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }

        meter.progress = 0
        refreshStatus()
    }

    private fun refreshStatus() {
        val devices = bluetoothCommunicationDevices()
        val current = try {
            audioManager.communicationDevice?.productName?.toString() ?: "padrão do sistema"
        } catch (_: SecurityException) {
            "sem permissão"
        }

        val available = if (devices.isEmpty()) {
            "nenhum"
        } else {
            devices.joinToString { "${it.productName} (tipo ${it.type})" }
        }

        status.text =
            "Rota atual: $current\n" +
            "Headsets Bluetooth disponíveis: $available\n" +
            "Modo de áudio: ${audioManager.mode}"
    }

    override fun onDestroy() {
        stopMicTest()
        super.onDestroy()
    }
}
