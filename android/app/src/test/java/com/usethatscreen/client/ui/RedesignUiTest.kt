package com.usethatscreen.client.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.usethatscreen.client.R
import com.usethatscreen.client.data.PcConnections
import com.usethatscreen.client.device.DeviceCaps
import com.usethatscreen.client.protocol.Proto
import com.usethatscreen.client.session.PairingRequest
import com.usethatscreen.client.session.Phase
import com.usethatscreen.client.session.SessionHub
import com.usethatscreen.client.session.Status
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RedesignUiTest {
    @Before
    fun setUp() {
        SessionHub.setPairing(null)
        SessionHub.update { Status(phase = Phase.LISTENING) }
        shadowOf(Looper.getMainLooper()).idle()
        WifiActivity.lanAddressesProvider = { listOf("192.168.1.42") }
    }

    @After
    fun tearDown() {
        SessionHub.setPairing(null)
        SessionHub.update { Status(phase = Phase.STOPPED) }
        shadowOf(Looper.getMainLooper()).idle()
        WifiActivity.lanAddressesProvider = { DeviceCaps.lanAddresses() }
    }

    @Test
    fun mainActivityKeepsOpenDisplayVisibleWhenConnectedAndNavigatesToSubScreens() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        SessionHub.update {
            Status(phase = Phase.CONNECTED, hostName = "Workstation-PC", link = Proto.LINK_NETWORK, mode = Proto.MODE_SAS)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btn_open).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btn_disconnect).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.btn_start).visibility)

        activity.findViewById<View>(R.id.card_wifi).performClick()
        assertEquals(
            WifiActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )

        activity.findViewById<View>(R.id.card_usb).performClick()
        assertEquals(
            UsbActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )

        activity.findViewById<View>(R.id.card_paired).performClick()
        assertEquals(
            PairedHostsActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
    }

    @Test
    fun wifiActivityShowsDeviceAddressCopyAndConnectToPcSavedList() {
        val app = RuntimeEnvironment.getApplication()
        PcConnections(app).remember("100.64.10.5", "Office PC")

        val activity = Robolectric.buildActivity(WifiActivity::class.java).setup().get()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.card_wifi_ready).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.card_wifi_off).visibility)
        assertEquals("192.168.1.42:${Proto.PORT}", activity.findViewById<TextView>(R.id.wifi_address).text.toString())

        activity.findViewById<Button>(R.id.btn_copy_address).performClick()
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("192.168.1.42:${Proto.PORT}", cm.primaryClip?.getItemAt(0)?.text?.toString())

        val savedContainer = activity.findViewById<LinearLayout>(R.id.saved_pcs_container)
        assertEquals(View.VISIBLE, savedContainer.visibility)
        assertEquals(1, savedContainer.childCount)
        assertEquals("Office PC", savedContainer.getChildAt(0).findViewById<TextView>(R.id.pc_name).text.toString())
        assertEquals("100.64.10.5", savedContainer.getChildAt(0).findViewById<TextView>(R.id.pc_address).text.toString())
    }

    @Test
    fun wifiActivityOfflineStateHidesCodeWellAndOpensWifiSettings() {
        WifiActivity.lanAddressesProvider = { emptyList() }
        val activity = Robolectric.buildActivity(WifiActivity::class.java).setup().get()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.card_wifi_off).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.card_wifi_ready).visibility)

        activity.findViewById<View>(R.id.btn_open_wifi).performClick()
        assertEquals(Settings.ACTION_WIFI_SETTINGS, shadowOf(activity).nextStartedActivity.action)
    }

    @Test
    fun usbActivityShowsAllStepsUnconditionallyAndExpandsManualAdbCommand() {
        val activity = Robolectric.buildActivity(UsbActivity::class.java).setup().get()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btn_open_about_device).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btn_open_dev_options).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.manual_adb_body).visibility)

        activity.findViewById<View>(R.id.btn_open_about_device).performClick()
        assertEquals(Settings.ACTION_DEVICE_INFO_SETTINGS, shadowOf(activity).nextStartedActivity.action)

        activity.findViewById<View>(R.id.btn_open_dev_options).performClick()
        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, shadowOf(activity).nextStartedActivity.action)

        activity.findViewById<View>(R.id.manual_adb_header).performClick()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.manual_adb_body).visibility)

        activity.findViewById<View>(R.id.btn_copy_adb).performClick()
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(activity.getString(R.string.usb_command), cm.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun baseActivityPairingDialogUsesFormattedSasAndCodesMatchAction() {
        var accepted: Boolean? = null
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        SessionHub.setPairing(PairingRequest("482917", "Studio Mac", "macOS") { accepted = it })
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        assertEquals("482 917", dialog.findViewById<TextView>(R.id.pairing_code)?.text?.toString())
        assertEquals(
            activity.getString(R.string.codes_match),
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString(),
        )
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(true, accepted)
        assertFalse(dialog.isShowing)
    }
}
