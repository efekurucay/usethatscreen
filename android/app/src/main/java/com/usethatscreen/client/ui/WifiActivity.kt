package com.usethatscreen.client.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.usethatscreen.client.R
import com.usethatscreen.client.data.PcConnection
import com.usethatscreen.client.data.PcConnections
import com.usethatscreen.client.data.parsePcAddress
import com.usethatscreen.client.device.DeviceCaps
import com.usethatscreen.client.protocol.Proto
import com.usethatscreen.client.session.ConnectionService
import com.usethatscreen.client.session.Phase
import com.usethatscreen.client.session.Status

class WifiActivity : BaseActivity() {
    private lateinit var connectedBanner: View
    private lateinit var connectedText: TextView
    private lateinit var cardWifiOff: View
    private lateinit var cardWifiReady: View
    private lateinit var wifiAddress: TextView
    private lateinit var btnCopyAddress: Button
    private lateinit var connectPcBusy: TextView
    private lateinit var savedPcsContainer: LinearLayout
    private lateinit var btnEnterPcAddress: Button
    private val pcConnections by lazy { PcConnections(this) }
    private var canConnectPc = true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi)
        insetRoot(findViewById(R.id.root))

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        connectedBanner = findViewById(R.id.connected_banner)
        connectedText = findViewById(R.id.connected_text)
        cardWifiOff = findViewById(R.id.card_wifi_off)
        cardWifiReady = findViewById(R.id.card_wifi_ready)
        wifiAddress = findViewById(R.id.wifi_address)
        btnCopyAddress = findViewById(R.id.btn_copy_address)
        connectPcBusy = findViewById(R.id.connect_pc_busy)
        savedPcsContainer = findViewById(R.id.saved_pcs_container)
        btnEnterPcAddress = findViewById(R.id.btn_enter_pc_address)

        findViewById<View>(R.id.btn_open_display).setOnClickListener {
            startActivity(Intent(this, StreamActivity::class.java))
        }
        findViewById<View>(R.id.btn_open_wifi).setOnClickListener {
            openSettingsSafely(this, Settings.ACTION_WIFI_SETTINGS)
        }
        btnCopyAddress.setOnClickListener {
            val text = wifiAddress.text.toString()
            if (text.isNotBlank()) copyToClipboard(this, text)
        }
        wifiAddress.setOnLongClickListener {
            val text = wifiAddress.text.toString()
            if (text.isNotBlank()) copyToClipboard(this, text)
            true
        }
        btnEnterPcAddress.setOnClickListener { showPcAddressEntry() }
        findViewById<View>(R.id.card_paired_link).setOnClickListener {
            startActivity(Intent(this, PairedHostsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshWifiAddresses()
        renderSavedPcs()
    }

    override fun onStatus(status: Status) {
        val connected = status.phase == Phase.CONNECTED
        connectedBanner.visibility = if (connected) View.VISIBLE else View.GONE
        if (connected) {
            connectedText.text = getString(R.string.status_connected, status.hostName)
        }
        canConnectPc = status.phase == Phase.LISTENING || status.phase == Phase.STOPPED
        connectPcBusy.visibility = if (canConnectPc) View.GONE else View.VISIBLE
        btnEnterPcAddress.isEnabled = canConnectPc
        renderSavedPcs()
    }

    internal fun refreshWifiAddresses(addresses: List<String> = lanAddressesProvider()) {
        if (addresses.isEmpty()) {
            cardWifiOff.visibility = View.VISIBLE
            cardWifiReady.visibility = View.GONE
            wifiAddress.text = ""
        } else {
            cardWifiOff.visibility = View.GONE
            cardWifiReady.visibility = View.VISIBLE
            wifiAddress.text = addresses.joinToString("\n") { "$it:${Proto.PORT}" }
        }
    }

    private fun renderSavedPcs() {
        val saved = pcConnections.list()
        savedPcsContainer.removeAllViews()
        savedPcsContainer.visibility = if (saved.isEmpty()) View.GONE else View.VISIBLE
        val inflater = LayoutInflater.from(this)
        for (entry in saved) {
            val row = inflater.inflate(R.layout.item_saved_pc, savedPcsContainer, false)
            val nameView = row.findViewById<TextView>(R.id.pc_name)
            val addrView = row.findViewById<TextView>(R.id.pc_address)
            val editBtn = row.findViewById<Button>(R.id.btn_edit_pc)
            if (entry.name == entry.address) {
                nameView.text = entry.address
                addrView.visibility = View.GONE
            } else {
                nameView.text = entry.name
                addrView.text = entry.address
                addrView.visibility = View.VISIBLE
            }
            row.isEnabled = canConnectPc
            row.alpha = if (canConnectPc) 1f else 0.55f
            row.setOnClickListener {
                if (canConnectPc) ConnectionService.connectPc(this, entry.address)
            }
            editBtn.setOnClickListener { editPcConnection(entry) }
            savedPcsContainer.addView(row)
        }
    }

    private fun wrapDialogInput(input: EditText): View = FrameLayout(this).apply {
        val h = (24 * resources.displayMetrics.density).toInt()
        val t = (8 * resources.displayMetrics.density).toInt()
        setPadding(h, t, h, 0)
        addView(input)
    }

    private fun editPcConnection(entry: PcConnection) {
        val name = EditText(this).apply {
            hint = getString(R.string.pc_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
            setText(entry.name)
            selectAll()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_pc_connection)
            .setMessage(entry.address)
            .setView(wrapDialogInput(name))
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.remove_pc_connection) { _, _ ->
                pcConnections.remove(entry.address)
                renderSavedPcs()
            }
            .setPositiveButton(R.string.save_pc_connection) { _, _ ->
                pcConnections.rename(entry.address, name.text.toString().trim().ifEmpty { entry.address })
                renderSavedPcs()
            }
            .show()
    }

    private fun showPcAddressEntry() {
        if (!canConnectPc) return
        val address = EditText(this).apply {
            hint = getString(R.string.pc_address_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.connect_pc)
            .setMessage(R.string.connect_pc_detail)
            .setView(wrapDialogInput(address))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.connect_pc, null)
            .show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val target = parsePcAddress(address.text.toString())?.hostAddress
            if (target != null) {
                ConnectionService.connectPc(this, target)
                dialog.dismiss()
            } else {
                address.error = getString(R.string.pc_address_invalid)
            }
        }
    }

    companion object {
        internal var lanAddressesProvider: () -> List<String> = { DeviceCaps.lanAddresses() }
    }
}
