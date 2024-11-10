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
import org.prozach.echbt.android.databinding.ActivityEchStatsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ECHStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEchStatsBinding
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
        binding = ActivityEchStatsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        var intent = Intent(this, ECHStatsService::class.java)
        bindService(intent, ECHStatsServiceConnection, BIND_AUTO_CREATE)

        val filter = IntentFilter()
        filter.addAction("com.prozach.echbt.android.stats")
        registerReceiver(broadcastHandler, filter, RECEIVER_EXPORTED)
        receiverRegistered = true

        ECHStatsFloating = ECHStatsFloating(applicationContext)

        binding.pipButton.setOnClickListener {
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
            binding.pipHelp.visibility = View.INVISIBLE
        } else {
            binding.pipHelp.visibility = View.VISIBLE
        }

        binding.icResetStats.setOnClickListener{
            statsService?.clearStats()
        }
        binding.resetStats.setOnClickListener{
            statsService?.clearStats()
        }
        binding.icResetTime.setOnClickListener{
            statsService?.clearStats()
        }
        binding.resetTime.setOnClickListener{
            statsService?.clearTime()
        }
        binding.statsFormatEchelon.setOnClickListener{
            statsService?.setStatsFormat(ECHStatsService.StatsFormat.ECHELON)
        }
        binding.statsFormatPeleton.setOnClickListener{
            statsService?.setStatsFormat(ECHStatsService.StatsFormat.PELOTON)
        }
        binding.distFormatMiles.setOnClickListener{
            statsService?.setDistFormat(ECHStatsService.DistFormat.MILES)
        }
        binding.distFormatKilometers.setOnClickListener{
            statsService?.setDistFormat(ECHStatsService.DistFormat.KILOMETERS)
        }
    }

    private val broadcastHandler: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            println("onReceive")
            runOnUiThread {
                binding.cadence.text = intent.getStringExtra("cadence")
                binding.cadenceAvg.text = intent.getStringExtra("cadence_avg")
                binding.cadenceMax.text = intent.getStringExtra("cadence_max")
                binding.resistance.text = intent.getStringExtra("resistance")
                binding.resistanceAvg.text = intent.getStringExtra("resistance_avg")
                binding.resistanceMax.text = intent.getStringExtra("resistance_max")
                binding.power.text = intent.getStringExtra("power")
                binding.powerAvg.text = intent.getStringExtra("power_avg")
                binding.powerMax.text = intent.getStringExtra("power_max")
                binding.time.text = intent.getStringExtra("time")
                binding.kcal.text = intent.getStringExtra("kcal")
                binding.dist.text = intent.getStringExtra("dist")

                var statsFormat = intent.getStringExtra("stats_format")
                if(statsFormat != "") {
                    if(statsFormat == "echelon" && !binding.statsFormatEchelon.isChecked) {
                        binding.statsFormatEchelon.isChecked = true
                        binding.statsFormatPeleton.isChecked = false
                    }
                    if(statsFormat == "peloton" && !binding.statsFormatPeleton.isChecked) {
                        binding.statsFormatEchelon.isChecked = false
                        binding.statsFormatPeleton.isChecked = true
                    }
                }
                var distFormat = intent.getStringExtra("dist_format")
                if(distFormat != "") {
                    if(distFormat == "miles" && !binding.distFormatMiles.isChecked) {
                        binding.distFormatMiles.isChecked = true
                        binding.distFormatKilometers.isChecked = false
                    }
                    if(distFormat == "kilometers" && !binding.distFormatKilometers.isChecked) {
                        binding.distFormatMiles.isChecked = false
                        binding.distFormatKilometers.isChecked = true
                    }
                }
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
            val currentLogText = if (binding.logTextView.text.isEmpty()) {
                "Beginning of log."
            } else {
                binding.logTextView.text
            }
            binding.logTextView.text = "$currentLogText\n$formattedMessage"
            binding.logScrollView.post { binding.logScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }
}

