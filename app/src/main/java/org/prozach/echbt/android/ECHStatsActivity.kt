package org.prozach.echbt.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Rational
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import org.prozach.echbt.android.ble.ConnectionEventListener
import org.prozach.echbt.android.ble.ConnectionManager
import org.prozach.echbt.android.ble.isIndicatable
import org.prozach.echbt.android.ble.isNotifiable
import org.prozach.echbt.android.ble.isReadable
import org.prozach.echbt.android.ble.isWritable
import org.prozach.echbt.android.ble.isWritableWithoutResponse
import org.prozach.echbt.android.ble.toHexString
import kotlinx.android.synthetic.main.activity_ech_stats.log_scroll_view
import kotlinx.android.synthetic.main.activity_ech_stats.log_text_view
import kotlinx.android.synthetic.main.activity_ech_stats.cadence
import kotlinx.android.synthetic.main.activity_ech_stats.debug_view
import kotlinx.android.synthetic.main.activity_ech_stats.resistance
import kotlinx.android.synthetic.main.activity_ech_stats.power
import kotlinx.android.synthetic.main.activity_ech_stats.pipButton
import kotlinx.android.synthetic.main.activity_ech_stats.exitButton
import org.jetbrains.anko.alert
import org.jetbrains.anko.noButton
import org.jetbrains.anko.selector
import org.jetbrains.anko.yesButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.pow

class ECHStatsActivity : AppCompatActivity() {

    private lateinit var simpleFloatingWindow: SimpleFloatingWindow
    private var floatingWindowShown: Boolean = false

    private val dateFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)

    companion object {
        private const val REQUEST_CODE_DRAW_OVERLAY_PERMISSION = 5
    }

    var statsService: ECHStatsService? = null
    var isBound = false
    private val ECHStatsServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            println("Activity Connected")
            val binder = service as ECHStatsService.ECHStatsBinder
            statsService = binder.getService()
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName) {
            println("Activity Disconnected")
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ech_stats)
        val intent = Intent(this, ECHStatsService::class.java)
        bindService(intent, ECHStatsServiceConnection, BIND_AUTO_CREATE)

        val filter = IntentFilter()
        filter.addAction("com.prozach.echbt.android.stats")
        registerReceiver(broadcastHandler, filter)

        simpleFloatingWindow = SimpleFloatingWindow(applicationContext)

        pipButton.setOnClickListener {
            if (canDrawOverlays) {
                simpleFloatingWindow.show()
                floatingWindowShown = true
                val startMain = Intent(Intent.ACTION_MAIN)
                startMain.addCategory(Intent.CATEGORY_HOME)
                startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(startMain)
            } else {
                startManageDrawOverlaysPermission()
            }
        }
        exitButton.setOnClickListener {
            super.onBackPressed();
        }
    }

    private val broadcastHandler : BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            println("onReceive")
            runOnUiThread {
                cadence.text = intent.getStringExtra("cadence")
                resistance.text = intent.getStringExtra("resistance")
                power.text = intent.getStringExtra("power")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(ECHStatsServiceConnection)
        unregisterReceiver(broadcastHandler)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(broadcastHandler)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter()
        filter.addAction("com.prozach.echbt.android.stats")
        registerReceiver(broadcastHandler, filter)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_DRAW_OVERLAY_PERMISSION -> {
                if (canDrawOverlays) {
                    simpleFloatingWindow.show()
                } else {
                    showToast("Permission is not granted!")
                }
            }
        }
    }

    private fun startManageDrawOverlaysPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${applicationContext.packageName}")
            ).let {
                startActivityForResult(it, REQUEST_CODE_DRAW_OVERLAY_PERMISSION)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun log(message: String) {
        val formattedMessage = String.format("%s: %s", dateFormatter.format(Date()), message)
        runOnUiThread {
            val currentLogText = if (log_text_view.text.isEmpty()) {
                "Beginning of log."
            } else {
                log_text_view.text
            }
            log_text_view.text = "$currentLogText\n$formattedMessage"
            log_scroll_view.post { log_scroll_view.fullScroll(View.FOCUS_DOWN) }
        }
    }
}

