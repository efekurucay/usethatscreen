package com.usethatscreen.client.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.usethatscreen.client.R

internal fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

/**
 * Launches a system Settings action, falling back to [Settings.ACTION_SETTINGS] if the OEM
 * (such as locked Developer options on Samsung One UI) rejects or omits the target activity.
 */
internal fun openSettingsSafely(context: Context, primaryAction: String): Boolean {
    return try {
        context.startActivity(Intent(primaryAction))
        true
    } catch (_: ActivityNotFoundException) {
        openSettingsFallback(context)
    } catch (_: SecurityException) {
        openSettingsFallback(context)
    }
}

private fun openSettingsFallback(context: Context): Boolean {
    return try {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
        false
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
        false
    }
}
