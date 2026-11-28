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
import com.usethatscreen.client.protocol.Proto
import com.usethatscreen.client.session.ConnectionService
import com.usethatscreen.client.session.Phase
import com.usethatscreen.client.session.SessionHub
import com.usethatscreen.client.session.Status

class UsbActivity : BaseActivity() {
    private lateinit var statusTitle: TextView
    private lateinit var statusPill: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusActions: View
    private lateinit var btnOpenDisplay: View
    private lateinit var btnDisconnect: View
    private lateinit var btnStartService: View
    private lateinit var manualAdbBody: View
    private lateinit var manualAdbChevron: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usb)
        insetRoot(findViewById(R.id.root))

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        statusTitle = findViewById(R.id.usb_status_title)
        statusPill = findViewById(R.id.usb_status_pill)
        statusDetail = findViewById(R.id.usb_status_detail)
        statusActions = findViewById(R.id.usb_status_actions)
        btnOpenDisplay = findViewById(R.id.btn_open_display)
        btnDisconnect = findViewById(R.id.btn_disconnect)
        btnStartService = findViewById(R.id.btn_start_service)
        manualAdbBody = findViewById(R.id.manual_adb_body)
        manualAdbChevron = findViewById(R.id.manual_adb_chevron)

        btnOpenDisplay.setOnClickListener { startActivity(Intent(this, StreamActivity::class.java)) }
        btnDisconnect.setOnClickListener { SessionHub.session?.disconnect() }
        btnStartService.setOnClickListener { ConnectionService.start(this) }
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
        when (status.phase) {
            Phase.STOPPED -> {
                statusTitle.setText(R.string.status_stopped)
                statusPill.setText(R.string.status_pill_stopped)
                statusDetail.text = status.lastError ?: getString(R.string.status_stopped_detail)
            }
            Phase.LISTENING -> {
                statusTitle.setText(R.string.usb_status_waiting)
                statusPill.setText(R.string.status_pill_waiting)
                statusDetail.setText(R.string.usb_status_waiting_detail)
            }
            Phase.HANDSHAKE -> {
                statusTitle.setText(R.string.status_handshake)
                statusPill.setText(R.string.status_pill_connecting)
                statusDetail.text = status.peer
            }
            Phase.PAIRING -> {
                statusTitle.text = getString(R.string.status_pairing, status.hostName)
                statusPill.setText(R.string.status_pill_pairing)
                statusDetail.setText(R.string.status_pairing_detail)
            }
            Phase.CONNECTED -> {
                statusTitle.text = getString(R.string.status_connected, status.hostName)
                statusPill.setText(R.string.status_pill_connected)
                val cfg = status.config
                val stream = if (cfg != null) " · ${cfg.width}×${cfg.height} ${Proto.codecName(cfg.codec)} ${cfg.refreshHz} Hz" else ""
                statusDetail.text = ConnectionService.linkLabel(this, status.link, status.mode) + stream
            }
        }
        val positivePill = status.phase == Phase.LISTENING || status.phase == Phase.CONNECTED
        statusPill.setBackgroundResource(if (positivePill) R.drawable.bg_status_pill else R.drawable.bg_status_pill_neutral)
        statusPill.setTextColor(getColor(if (positivePill) R.color.brand_on_highlight else R.color.brand_body))

        val connected = status.phase == Phase.CONNECTED
        val stopped = status.phase == Phase.STOPPED
        btnOpenDisplay.visibility = if (connected) View.VISIBLE else View.GONE
        btnDisconnect.visibility = if (connected) View.VISIBLE else View.GONE
        btnStartService.visibility = if (stopped) View.VISIBLE else View.GONE
        statusActions.visibility = if (connected || stopped) View.VISIBLE else View.GONE
    }
}
