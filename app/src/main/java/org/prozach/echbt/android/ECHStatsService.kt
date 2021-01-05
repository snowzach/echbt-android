package org.prozach.echbt.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.prozach.echbt.android.ble.ConnectionEventListener
import org.prozach.echbt.android.ble.ConnectionManager
import org.prozach.echbt.android.ble.toHexString
import java.util.Locale
import java.util.UUID
import kotlin.math.pow

class ECHStatsService : Service() {

    private val sensorUUID = java.util.UUID.fromString("0bf669f4-45f2-11e7-9598-0800200c9a66")
    private val writeUUID = java.util.UUID.fromString("0bf669f2-45f2-11e7-9598-0800200c9a66")
    private val CHANNEL_ID = "Echadence"

    // https://www.techotopia.com/index.php/Android_Local_Bound_Services_%E2%80%93_A_Kotlin_Example
    private val binder = ECHStatsBinder()

    private lateinit var device: BluetoothDevice
    private val characteristics by lazy {
        ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        } ?: listOf()
    }
    private var notifyingCharacteristics = mutableListOf<UUID>()

    private var resistanceVal: UInt = 0u
    private var cadenceVal: UInt = 0u

    // Helpers to start/stop
    // https://androidwave.com/foreground-service-android-example-in-kotlin/
    companion object {
        fun startService(context: Context, message: String) {
            val startIntent = Intent(context, ECHStatsService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, ECHStatsService::class.java)
            context.stopService(stopIntent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId);
        println("onStartCommand")

        connectionEventListener

        // Create a notification to keep it running
        createNotificationChannel()
        val notificationIntent = Intent(this, ECHStatsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(this.resources.getString(R.string.app_name))
            .setContentText("Bike Stats collection running...")
            .setSmallIcon(R.mipmap.ic_cadence_white)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)

        ConnectionManager.registerListener(connectionEventListener)
        device = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")

        for (characteristic in characteristics) {
            when (characteristic.uuid) {
                sensorUUID -> {
                    ConnectionManager.enableNotifications(device, characteristic)
                    log("Enabling notifications from ${characteristic.uuid}")
                }
                writeUUID -> {
                    ConnectionManager.writeCharacteristic(
                        device,
                        characteristic,
                        byteArrayOfInts(0xF0, 0xB0, 0x01, 0x01, 0xA2)
                    )
                    log("Writing activation string to ${characteristic.uuid}")
                }
            }
        }

        return START_NOT_STICKY;
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    inner class ECHStatsBinder : Binder() {
        fun getService(): ECHStatsService {
            return this@ECHStatsService
        }
    }

    override fun onCreate() {
        println("onCreate")
        super.onCreate()
    }

    override fun onDestroy() {
        println("onDestroy")
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        super.onDestroy()
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                log("Disconnected")
            }

            onCharacteristicRead = { _, characteristic ->
                log("Read from ${characteristic.uuid}: ${characteristic.value.toHexString()}")
            }

            onCharacteristicWrite = { _, characteristic ->
                log("Wrote to ${characteristic.uuid}")
            }

            onCharacteristicChanged = { _, characteristic ->
                when (characteristic.value[1].toByte()) {
                    0xD1.toByte() -> {
                        cadenceVal =
                            characteristic.value[9].toUInt()
                                .shl(8) + characteristic.value[10].toUInt()
                        log("Cadence ${cadenceVal}")
                        sendLocalBroadcast()
                    }
                    0xD2.toByte() -> {
                        resistanceVal = characteristic.value[3].toUInt()
                        log("Resistance ${resistanceVal}")
                        sendLocalBroadcast()
                    }
                    else -> {
                        log("Value changed on ${characteristic.uuid}: ${characteristic.value.toHexString()}")
                    }
                }
            }

            onNotificationsEnabled = { _, characteristic ->
                log("Enabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.add(characteristic.uuid)
            }

            onNotificationsDisabled = { _, characteristic ->
                log("Disabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.remove(characteristic.uuid)
            }
        }
    }

    private fun log(message: String) {
        println(message)
//            val formattedMessage = String.format("%s: %s", dateFormatter.format(Date()), message)
//            runOnUiThread {
//                val currentLogText = if (log_text_view.text.isEmpty()) {
//                    "Beginning of log."
//                } else {
//                    log_text_view.text
//                }
//                log_text_view.text = "$currentLogText\n$formattedMessage"
//                log_scroll_view.post { log_scroll_view.fullScroll(View.FOCUS_DOWN) }
//            }
    }

    private fun sendLocalBroadcast() {
        var intent = Intent("com.prozach.echbt.android.stats");
        intent.putExtra("cadence", cadenceVal.toString());
        var r = ((0.0116058 * resistanceVal.toFloat().pow(3)) + (-0.568562 * resistanceVal.toFloat()
            .pow(
                2
            )) + (10.4126 * resistanceVal.toFloat()) - 31.4807).toUInt()
        intent.putExtra("resistance", r.toString());
        var p = 0u
        if (resistanceVal > 0u && cadenceVal > 0u) {
            p =
                (1.090112f.pow(resistanceVal.toFloat()) * 1.015343f.pow(cadenceVal.toFloat()) * 7.228958).toUInt()
        }
        intent.putExtra("power", p.toString());
        sendBroadcast(intent)
        println("sendLocalBroadcast")
    }

    private fun String.hexToBytes() =
        this.chunked(2).map { it.toUpperCase(Locale.US).toInt(16).toByte() }.toByteArray()

    private fun byteArrayOfInts(vararg ints: Int) =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }
}