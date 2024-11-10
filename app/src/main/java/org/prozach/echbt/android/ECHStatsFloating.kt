package org.prozach.echbt.android

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import kotlin.math.abs
import org.prozach.echbt.android.databinding.FloatingEchStatsBinding

class ECHStatsFloating constructor(private val context: Context) {

    private var windowManager: WindowManager? = null
        get() {
            if (field == null) field = (context.getSystemService(WINDOW_SERVICE) as WindowManager)
            return field
        }

    private var _binding: FloatingEchStatsBinding? = FloatingEchStatsBinding.inflate(LayoutInflater.from(context), null, false)
    private val binding get() = _binding!!
    private var floatView = binding.root

    private lateinit var layoutParams: WindowManager.LayoutParams

    var statsService: ECHStatsService? = null
    private val ECHStatsServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            println("Floating Window Connected To Service")
            val binder = service as ECHStatsService.ECHStatsBinder
            statsService = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            println("Floating Window Disconnected From Service")
        }
    }

    private var lastX: Int = 0
    private var lastY: Int = 0
    private var firstX: Int = 0
    private var firstY: Int = 0

    private var isShowing = false
    private var touchConsumedByMove = false

    private val onTouchListener = View.OnTouchListener { view, event ->
        val totalDeltaX = lastX - firstX
        val totalDeltaY = lastY - firstY

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                firstX = lastX
                firstY = lastY
            }
            MotionEvent.ACTION_UP -> {
                view.performClick()
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX.toInt() - lastX
                val deltaY = event.rawY.toInt() - lastY
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                if (abs(totalDeltaX) >= 5 || abs(totalDeltaY) >= 5) {
                    if (event.pointerCount == 1) {
                        layoutParams.x += deltaX
                        layoutParams.y += deltaY
                        touchConsumedByMove = true
                        windowManager?.apply {
                            updateViewLayout(floatView, layoutParams)
                        }
                    } else {
                        touchConsumedByMove = false
                    }
                } else {
                    touchConsumedByMove = false
                }
            }
            else -> {
            }
        }
        touchConsumedByMove
    }

    init {
        with(floatView) {
            binding.icBackFloat.setOnClickListener {
                val intent = Intent(context, ECHStatsActivity::class.java)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(context, intent, null)
                dismiss()
            }

            binding.icResetStats.setOnClickListener {
                statsService?.clearStats()
            }

            binding.icResetTime.setOnClickListener {
                statsService?.clearTime()
            }
        }

        floatView.setOnTouchListener(onTouchListener)

        layoutParams = WindowManager.LayoutParams().apply {
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            @Suppress("DEPRECATION")
            type = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else -> WindowManager.LayoutParams.TYPE_PHONE
            }

            gravity = Gravity.CENTER
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
    }

    fun show() {
        println("show")
        if (context.canDrawOverlays) {
            dismiss()
            isShowing = true
            windowManager?.addView(floatView, layoutParams)
            val filter = IntentFilter()
            filter.addAction("com.prozach.echbt.android.stats")
            context.registerReceiver(broadcastHandler, filter, RECEIVER_EXPORTED)
        }
        val intent = Intent(context, ECHStatsService::class.java)
        context.bindService(intent, ECHStatsServiceConnection, AppCompatActivity.BIND_AUTO_CREATE)
    }

    fun dismiss() {
        if (isShowing) {
            windowManager?.removeView(floatView)
            isShowing = false
            context.unregisterReceiver(broadcastHandler)
        }
    }

    private val broadcastHandler: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            println("onReceive")
            var floating_ech_stats = intent.getStringExtra("floating_ech_stats")
            println("Got:"+floating_ech_stats);
            if(floating_ech_stats == "dismiss") {
                dismiss();
                return;
            }
            with(floatView) {
                binding.cadenceFloat.text = intent.getStringExtra("cadence")
                binding.avgCadenceFloat.text = intent.getStringExtra("cadence_avg")
                binding.maxCadenceFloat.text = intent.getStringExtra("cadence_max")
                binding.resistanceFloat.text = intent.getStringExtra("resistance")
                binding.avgResistanceFloat.text = intent.getStringExtra("resistance_avg")
                binding.maxResistanceFloat.text = intent.getStringExtra("resistance_max")
                binding.powerFloat.text = intent.getStringExtra("power")
                binding.avgPowerFloat.text = intent.getStringExtra("power_avg")
                binding.maxPowerFloat.text = intent.getStringExtra("power_max")
                binding.timeFloat.text = intent.getStringExtra("time")
                binding.kcalFloat.text = intent.getStringExtra("kcal")
                binding.distFloat.text = intent.getStringExtra("dist")
            }
        }
    }
}