package org.prozach.echbt.android

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_ech_stats.log_scroll_view
import kotlinx.android.synthetic.main.activity_ech_stats.log_text_view
import kotlinx.android.synthetic.main.activity_ech_stats.cadence
import kotlinx.android.synthetic.main.activity_ech_stats.cadence_avg
import kotlinx.android.synthetic.main.activity_ech_stats.cadence_max
import kotlinx.android.synthetic.main.activity_ech_stats.ic_reset_stats
import kotlinx.android.synthetic.main.activity_ech_stats.ic_reset_time
import kotlinx.android.synthetic.main.activity_ech_stats.resistance
import kotlinx.android.synthetic.main.activity_ech_stats.resistance_avg
import kotlinx.android.synthetic.main.activity_ech_stats.resistance_max
import kotlinx.android.synthetic.main.activity_ech_stats.power
import kotlinx.android.synthetic.main.activity_ech_stats.power_avg
import kotlinx.android.synthetic.main.activity_ech_stats.power_max
import kotlinx.android.synthetic.main.activity_ech_stats.pipButton
import kotlinx.android.synthetic.main.activity_ech_stats.kcal
import kotlinx.android.synthetic.main.activity_ech_stats.pip_help
import kotlinx.android.synthetic.main.activity_ech_stats.reset_stats
import kotlinx.android.synthetic.main.activity_ech_stats.reset_time
import kotlinx.android.synthetic.main.activity_ech_stats.stats_format_echelon
import kotlinx.android.synthetic.main.activity_ech_stats.stats_format_peleton
import kotlinx.android.synthetic.main.activity_ech_stats.time
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ECHStatsActivity : AppCompatActivity() {

    private lateinit var ECHStatsFloating: ECHStatsFloating
    private var floatingWindowShown: Boolean = false

    private val dateFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)

    private var receiverRegistered: Boolean = false

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
            statsService?.floatingWindow("dismiss");
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            println("Activity Disconnected")
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        println("ONCREATE")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ech_stats)

        var intent = Intent(this, ECHStatsService::class.java)
        bindService(intent, ECHStatsServiceConnection, BIND_AUTO_CREATE)

        val filter = IntentFilter()
        filter.addAction("com.prozach.echbt.android.stats")
        registerReceiver(broadcastHandler, filter)
        receiverRegistered = true

        ECHStatsFloating = ECHStatsFloating(applicationContext)

        pipButton.setOnClickListener {
            if (canDrawOverlays) {
                ECHStatsFloating.show()
                floatingWindowShown = true
                finish()
                // Return to home screen
                val startMain = Intent(Intent.ACTION_MAIN)
                startMain.addCategory(Intent.CATEGORY_HOME)
                startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(startMain)
            } else {
                startManageDrawOverlaysPermission()
            }
        }

        if (canDrawOverlays) {
            pip_help.visibility = View.INVISIBLE
        } else {
            pip_help.visibility = View.VISIBLE
        }

        ic_reset_stats.setOnClickListener{
            statsService?.clearStats()
        }
        reset_stats.setOnClickListener{
            statsService?.clearStats()
        }
        ic_reset_time.setOnClickListener{
            statsService?.clearStats()
        }
        reset_time.setOnClickListener{
            statsService?.clearTime()
        }
        stats_format_echelon.setOnClickListener{
            statsService?.setStatsFormat(ECHStatsService.StatsFormat.ECHELON)
        }
        stats_format_peleton.setOnClickListener{
            statsService?.setStatsFormat(ECHStatsService.StatsFormat.PELOTON)
        }
    }

    private val broadcastHandler: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            println("onReceive")
            runOnUiThread {
                cadence.text = intent.getStringExtra("cadence")
                cadence_avg.text = intent.getStringExtra("cadence_avg")
                cadence_max.text = intent.getStringExtra("cadence_max")
                resistance.text = intent.getStringExtra("resistance")
                resistance_avg.text = intent.getStringExtra("resistance_avg")
                resistance_max.text = intent.getStringExtra("resistance_max")
                power.text = intent.getStringExtra("power")
                power_avg.text = intent.getStringExtra("power_avg")
                power_max.text = intent.getStringExtra("power_max")
                time.text = intent.getStringExtra("time")
                kcal.text = intent.getStringExtra("kcal")
            }
        }
    }

    override fun onBackPressed() {
        statsService?.shutdown()
        super.onBackPressed() // Don't call this
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(ECHStatsServiceConnection)
        if (receiverRegistered) {
            unregisterReceiver(broadcastHandler)
            receiverRegistered = false
        }
    }

    override fun onPause() {
        super.onPause()
        if (receiverRegistered) {
            unregisterReceiver(broadcastHandler)
            receiverRegistered = false
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter()
        filter.addAction("com.prozach.echbt.android.stats")
        if (!receiverRegistered) {
            registerReceiver(broadcastHandler, filter)
            receiverRegistered = true
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_DRAW_OVERLAY_PERMISSION -> {
                if (canDrawOverlays) {
                    ECHStatsFloating.show();
                    floatingWindowShown = true;
                    finish()
                    // Return to home screen
                    val startMain = Intent(Intent.ACTION_MAIN)
                    startMain.addCategory(Intent.CATEGORY_HOME)
                    startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(startMain)
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

