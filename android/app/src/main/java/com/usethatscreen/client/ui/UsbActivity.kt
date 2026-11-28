package com.usethatscreen.client.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import com.google.android.material.appbar.MaterialToolbar
import com.usethatscreen.client.R
import com.usethatscreen.client.session.Phase
import com.usethatscreen.client.session.Status

class UsbActivity : BaseActivity() {
    private lateinit var connectedBanner: View
    private lateinit var connectedText: TextView
    private lateinit var manualAdbBody: View
    private lateinit var manualAdbChevron: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usb)
        insetRoot(findViewById(R.id.root))

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        connectedBanner = findViewById(R.id.connected_banner)
        connectedText = findViewById(R.id.connected_text)
        manualAdbBody = findViewById(R.id.manual_adb_body)
        manualAdbChevron = findViewById(R.id.manual_adb_chevron)

        findViewById<View>(R.id.btn_open_display).setOnClickListener {
            startActivity(Intent(this, StreamActivity::class.java))
        }
        findViewById<View>(R.id.btn_open_about_device).setOnClickListener {
            openSettingsSafely(this, Settings.ACTION_DEVICE_INFO_SETTINGS)
        }
        findViewById<View>(R.id.btn_open_dev_options).setOnClickListener {
            openSettingsSafely(this, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        }
        findViewById<View>(R.id.manual_adb_header).setOnClickListener {
            val expand = manualAdbBody.visibility != View.VISIBLE
            manualAdbBody.visibility = if (expand) View.VISIBLE else View.GONE
            manualAdbChevron.rotation = if (expand) 180f else 0f
        }
        val adbCommand = getString(R.string.usb_command)
        findViewById<View>(R.id.btn_copy_adb).setOnClickListener { copyToClipboard(this, adbCommand) }
        findViewById<View>(R.id.usb_command).setOnLongClickListener {
            copyToClipboard(this, adbCommand)
            true
        }
    }

    override fun onStatus(status: Status) {
        val connected = status.phase == Phase.CONNECTED
        connectedBanner.visibility = if (connected) View.VISIBLE else View.GONE
        if (connected) {
            connectedText.text = getString(R.string.status_connected, status.hostName)
        }
    }
}
