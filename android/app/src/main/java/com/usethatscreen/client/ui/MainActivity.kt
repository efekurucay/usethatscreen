package com.usethatscreen.client.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.usethatscreen.client.BuildConfig
import com.usethatscreen.client.R
import com.usethatscreen.client.data.Identity
import com.usethatscreen.client.data.PairedHosts
import com.usethatscreen.client.device.DeviceCaps
import com.usethatscreen.client.protocol.Proto
import com.usethatscreen.client.session.ConnectionService
import com.usethatscreen.client.session.Phase
import com.usethatscreen.client.session.SessionHub
import com.usethatscreen.client.session.Status

class MainActivity : BaseActivity() {
    private lateinit var statusIcon: ImageView
    private lateinit var statusProgress: CircularProgressIndicator
    private lateinit var statusTitle: TextView
    private lateinit var statusPill: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusError: TextView
    private lateinit var statusActions: View
    private lateinit var btnOpen: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnStart: Button
    private lateinit var wifiSummary: TextView
    private lateinit var pairedSummary: TextView
    private val pairedHosts by lazy { PairedHosts(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        insetRoot(findViewById(R.id.root))

        findViewById<MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }

        statusIcon = findViewById(R.id.status_icon)
        statusProgress = findViewById(R.id.status_progress)
        statusTitle = findViewById(R.id.status_title)
        statusPill = findViewById(R.id.status_pill)
        statusDetail = findViewById(R.id.status_detail)
        statusError = findViewById(R.id.status_error)
        statusActions = findViewById(R.id.status_actions)
        btnOpen = findViewById(R.id.btn_open)
        btnDisconnect = findViewById(R.id.btn_disconnect)
        btnStart = findViewById(R.id.btn_start)
        wifiSummary = findViewById(R.id.wifi_summary)
        pairedSummary = findViewById(R.id.paired_summary)

        configureResponsiveChoiceGrid()

        btnOpen.setOnClickListener { startActivity(Intent(this, StreamActivity::class.java)) }
        btnDisconnect.setOnClickListener { SessionHub.session?.disconnect() }
        btnStart.setOnClickListener { ConnectionService.start(this) }
        findViewById<View>(R.id.card_wifi).setOnClickListener { startActivity(Intent(this, WifiActivity::class.java)) }
        findViewById<View>(R.id.card_usb).setOnClickListener { startActivity(Intent(this, UsbActivity::class.java)) }
        findViewById<View>(R.id.card_paired).setOnClickListener { startActivity(Intent(this, PairedHostsActivity::class.java)) }

        findViewById<TextView>(R.id.footer).text =
            getString(R.string.device_footer, Identity(this).deviceIdHex.take(8), BuildConfig.VERSION_NAME)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        ConnectionService.start(this)
    }

    override fun onResume() {
        super.onResume()
        refreshSummaries()
    }

    private fun configureResponsiveChoiceGrid() {
        if (!resources.getBoolean(R.bool.wide_layout)) return
        val grid = findViewById<LinearLayout>(R.id.choice_grid)
        val wifiCard = findViewById<View>(R.id.card_wifi)
        val usbCard = findViewById<View>(R.id.card_usb)
        grid.orientation = LinearLayout.HORIZONTAL
        wifiCard.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        usbCard.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun refreshSummaries() {
        val addresses = DeviceCaps.lanAddresses()
        if (addresses.isEmpty()) {
            wifiSummary.setText(R.string.home_wifi_off)
            wifiSummary.typeface = Typeface.DEFAULT
        } else {
            wifiSummary.text = "${addresses.first()}:${Proto.PORT}"
            wifiSummary.typeface = Typeface.MONOSPACE
        }

        val hosts = pairedHosts.list()
        pairedSummary.text = when (hosts.size) {
            0 -> getString(R.string.home_paired_none)
            1 -> hosts.first().name.ifBlank { hosts.first().hostIdHex.take(8) }
            else -> getString(R.string.home_paired_count, hosts.size)
        }
    }

    override fun onStatus(status: Status) {
        val busy = status.phase == Phase.HANDSHAKE || status.phase == Phase.PAIRING
        statusProgress.visibility = if (busy || status.phase == Phase.LISTENING) View.VISIBLE else View.GONE
        statusProgress.isIndeterminate = true
        statusIcon.setImageResource(if (status.phase == Phase.CONNECTED) R.drawable.ic_link else R.drawable.ic_computer)
        when (status.phase) {
            Phase.STOPPED -> {
                statusTitle.setText(R.string.status_stopped)
                statusPill.setText(R.string.status_pill_stopped)
                statusDetail.text = status.lastError ?: getString(R.string.status_stopped_detail)
            }
            Phase.LISTENING -> {
                statusTitle.setText(R.string.status_waiting)
                statusPill.setText(R.string.status_pill_waiting)
                statusDetail.setText(R.string.status_waiting_detail)
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
        val err = status.lastError
        statusError.visibility = if (err != null && status.phase == Phase.LISTENING) View.VISIBLE else View.GONE
        statusError.text = if (err != null) getString(R.string.last_error, err) else ""

        val showOpen = status.phase == Phase.CONNECTED
        val showDisconnect = status.phase == Phase.CONNECTED
        val showStart = status.phase == Phase.STOPPED
        btnOpen.visibility = if (showOpen) View.VISIBLE else View.GONE
        btnDisconnect.visibility = if (showDisconnect) View.VISIBLE else View.GONE
        btnStart.visibility = if (showStart) View.VISIBLE else View.GONE
        statusActions.visibility = if (showOpen || showDisconnect || showStart) View.VISIBLE else View.GONE
    }
}
