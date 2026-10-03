package com.miokzz.budsmicforcer

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.widget.*
import android.graphics.Typeface

class MainActivity : Activity() {
    private lateinit var audio: AudioManager
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.RECORD_AUDIO), 10)
        }
        val box = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(48,80,48,48) }
        box.addView(TextView(this).apply { text="Buds Mic Forcer"; textSize=28f; setTypeface(null,Typeface.BOLD) })
        box.addView(TextView(this).apply { text="Força a rota de comunicação Bluetooth para testar o microfone dos Buds."; textSize=16f; setPadding(0,16,0,32) })
        status = TextView(this).apply { textSize=16f; text="Status: aguardando"; setPadding(0,0,0,28) }
        box.addView(status)
        box.addView(Button(this).apply { text="FORCE BUDS MIC"; setOnClickListener { forceBluetooth() } })
        box.addView(Button(this).apply { text="STOP / RESTAURAR"; setOnClickListener { stopForce() } })
        box.addView(Button(this).apply { text="ATUALIZAR STATUS"; setOnClickListener { refresh() } })
        setContentView(box)
        audio.addOnCommunicationDeviceChangedListener(mainExecutor) { refresh() }
        refresh()
    }

    private fun bluetoothDevices(): List<AudioDeviceInfo> = audio.availableCommunicationDevices.filter {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }

    private fun forceBluetooth() {
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        val d = bluetoothDevices().firstOrNull()
        val ok = d != null && audio.setCommunicationDevice(d)
        status.text = if (ok) "FORÇADO ✓\nDispositivo: ${d?.productName}\nAgora abra o Gemini e teste." else "Falhou: nenhum headset Bluetooth de comunicação disponível, ou o Android recusou a rota."
    }

    private fun stopForce() {
        audio.clearCommunicationDevice()
        audio.mode = AudioManager.MODE_NORMAL
        refresh()
    }

    private fun refresh() {
        val d = audio.communicationDevice
        val avail = bluetoothDevices().joinToString { it.productName.toString() }.ifBlank { "nenhum" }
        status.text = "Rota atual: ${d?.productName ?: "padrão do sistema"}\nBluetooth disponível: $avail\nModo: ${audio.mode}"
    }

    override fun onDestroy() {
        audio.clearCommunicationDevice()
        audio.mode = AudioManager.MODE_NORMAL
        super.onDestroy()
    }
}
