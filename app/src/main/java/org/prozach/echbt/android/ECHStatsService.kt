package org.prozach.echbt.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
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
import kotlin.math.roundToInt

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
    private var resistanceMax: UInt = 0u
    private var resistanceTotal: UInt = 0u
    private var cadenceVal: UInt = 0u
    private var cadenceMax: UInt = 0u
    private var cadenceTotal: UInt = 0u
    private var powerMax: UInt = 0u
    private var dist: Long = 0;
    private var startTimeMillis: Long = 0;
    private var elapsedTimeMillis: Long = 0;
    private var statCount: UInt = 0u
    private var distFormat: DistFormat = DistFormat.MILES;
    private var statsFormat: StatsFormat = StatsFormat.PELOTON;

    enum class StatsFormat {
        ECHELON, PELOTON
    }

    enum class DistFormat {
        MILES, KILOMETERS
    }

    fun setStatsFormat(sf: StatsFormat) {
        var intent = Intent("com.prozach.echbt.android.stats");
        statsFormat = sf
        powerMax = 0U;
    }

    fun setDistFormat(df: DistFormat) {
        var intent = Intent("com.prozach.echbt.android.stats");
        distFormat = df
    }

    fun clearStats() {
        resistanceMax = 0U;
        resistanceTotal = 0U;
        cadenceMax = 0U;
        cadenceTotal = 0U;
        powerMax = 0U;
        statCount = 0U;
        dist = 0L;
    }

    fun clearTime() {
        startTimeMillis = 0L;
        elapsedTimeMillis = 0L;
    }

    fun shutdown() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        stopForeground(true)
        stopSelf()
    }

    fun floatingWindow(action: String) {
        var intent = Intent("com.prozach.echbt.android.stats");
        intent.putExtra("floating_ech_stats", action);
        println("Sent:"+action);
        sendBroadcast(intent);
    }

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

        // Create a notification to keep it running
        createNotificationChannel()
        val notificationIntent = Intent(this, ECHStatsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(this.resources.getString(R.string.app_name))
            .setContentText("Bike Stats collection running...")
            .setSmallIcon(R.mipmap.ic_cadence_white)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)

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
                        var cadenceTemp =
                            characteristic.value[9].toUInt()
                                .shl(8) + characteristic.value[10].toUInt()
                        log("Cadence ${cadenceVal}")

                        // Validate the value before using it
                        if(cadenceTemp < 300U) {
                            cadenceVal = cadenceTemp
                            sendLocalBroadcast()
                        }
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
                if(cadenceVal > 0.toUInt()) {
                    statCount++
                    cadenceTotal += cadenceVal
                    resistanceTotal += resistanceVal

                    // We just started pedaling
                    if(startTimeMillis == 0L) {
                        startTimeMillis = System.currentTimeMillis();
                    }
                } else {
                    // We stopped pedaling, stop the clock
                    if(startTimeMillis > 0) {
                        elapsedTimeMillis = System.currentTimeMillis() - startTimeMillis;
                        startTimeMillis = 0;
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

        if(cadenceVal > cadenceMax) {
            cadenceMax = cadenceVal
        }
        if(resistanceVal > resistanceMax) {
            resistanceMax = resistanceVal
        }

        // Cadence
        intent.putExtra("cadence", cadenceVal.toString());
        intent.putExtra("cadence_max", cadenceMax.toString());
        var cadenceAverage = 0U;
        if(statCount > 0.toUInt()) {
            cadenceAverage = (cadenceTotal / statCount).toUInt();
            intent.putExtra("cadence_avg", cadenceAverage.toString());
        }

        // Resistance
        intent.putExtra("resistance", calcResistance(resistanceVal).toString());
        if(statCount > 0.toUInt()) {
            intent.putExtra("resistance_avg", calcResistance(resistanceTotal / statCount).toString());
        } else {
            intent.putExtra("resistance_avg", calcResistance(resistanceVal).toString());
        }
        intent.putExtra("resistance_max", calcResistance(resistanceMax).toString());


        var currentElapsedTimeMillis = elapsedTimeMillis;
        if(startTimeMillis > 0) {
            currentElapsedTimeMillis += System.currentTimeMillis() - startTimeMillis;
        }
        val minutes = currentElapsedTimeMillis / 1000 / 60
        val seconds = currentElapsedTimeMillis / 1000 % 60
        intent.putExtra("time", minutes.toString()+":"+seconds.toString().padStart(2, '0'));

        // Power
        var power = calcPower(cadenceVal, resistanceVal)
        if(power > powerMax) {
            powerMax = power
        }
        intent.putExtra("power", power.toString());

        var avgPower = 0U;
        if(statCount > 0.toUInt()) {
            avgPower = calcPower(cadenceTotal / statCount, resistanceTotal / statCount);
        }
        intent.putExtra("power_avg", avgPower.toString());
        intent.putExtra("power_max", powerMax.toString());
        var kcal = ((avgPower.toFloat() / 0.24) * (currentElapsedTimeMillis.toFloat()/1000.0))/1000.0;
        intent.putExtra("kcal", kcal.toUInt().toString());

        if(statsFormat == StatsFormat.ECHELON) {
            intent.putExtra("stats_format", "echelon");
        } else if(statsFormat == StatsFormat.PELOTON) {
            intent.putExtra("stats_format", "peloton");
        }

        // Cadence to kph https://github.com/cagnulein/qdomyos-zwift/issues/62
        val distanceKilometers = (cadenceAverage.toFloat() * 0.37497622F) * (currentElapsedTimeMillis.toFloat()/3600000F);
        if(distFormat == DistFormat.MILES) {
            intent.putExtra("dist_format", "miles");
            intent.putExtra("dist", ((distanceKilometers * 0.621371 * 100.0).roundToInt()/100.0).toString());
        } else if(distFormat == DistFormat.KILOMETERS) {
            intent.putExtra("dist_format", "kilometers");
            intent.putExtra("dist", ((distanceKilometers * 100.0).roundToInt()/100.0).toString());
        }

        sendBroadcast(intent)
        println("sendLocalBroadcast")
    }

    private fun calcResistance(r: UInt): UInt {
        when (statsFormat) {
            StatsFormat.PELOTON -> {
                return ((0.0116058 * r.toFloat()
                    .pow(3)) + (-0.568562 * r.toFloat()
                    .pow(2)) + (10.4126 * r.toFloat()) - 31.4807).toUInt()
            }
            StatsFormat.ECHELON -> {
                return r
            }
        }
    }

    private fun calcPower(c: UInt, r: UInt): UInt {
        if (r == 0u || c == 0u) {
            return 0.toUInt()
        }
        return (1.090112f.pow(r.toFloat()) * 1.015343f.pow(c.toFloat()) * 7.228958).toUInt()
    }

    private fun String.hexToBytes() =
        this.chunked(2).map { it.toUpperCase(Locale.US).toInt(16).toByte() }.toByteArray()

    private fun byteArrayOfInts(vararg ints: Int) =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }
}