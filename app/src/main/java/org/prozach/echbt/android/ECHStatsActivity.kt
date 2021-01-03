/*
 * Copyright 2020 Punch Through Design LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.prozach.echbt.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Rational
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
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

    private val sensorUUID = java.util.UUID.fromString("0bf669f4-45f2-11e7-9598-0800200c9a66")
    private val writeUUID = java.util.UUID.fromString("0bf669f2-45f2-11e7-9598-0800200c9a66")

    private var resistanceVal : UInt = 0u
    private var cadenceVal : UInt = 0u

    private lateinit var device: BluetoothDevice
    private val dateFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)
    private val characteristics by lazy {
        ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        } ?: listOf()
    }
    private val characteristicProperties by lazy {
        characteristics.map { characteristic ->
            characteristic to mutableListOf<CharacteristicProperty>().apply {
                if (characteristic.isNotifiable()) add(CharacteristicProperty.Notifiable)
                if (characteristic.isIndicatable()) add(CharacteristicProperty.Indicatable)
                if (characteristic.isReadable()) add(CharacteristicProperty.Readable)
                if (characteristic.isWritable()) add(CharacteristicProperty.Writable)
                if (characteristic.isWritableWithoutResponse()) {
                    add(CharacteristicProperty.WritableWithoutResponse)
                }
            }.toList()
        }.toMap()
    }
    private val characteristicAdapter: CharacteristicAdapter by lazy {
        CharacteristicAdapter(characteristics) { characteristic ->
            showCharacteristicOptions(characteristic)
        }
    }
    private var notifyingCharacteristics = mutableListOf<UUID>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ConnectionManager.registerListener(connectionEventListener)
        super.onCreate(savedInstanceState)
        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")

        setContentView(R.layout.activity_ech_stats)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            title = getString(R.string.echbt_dash)
        }

        pipButton.setOnClickListener { enterPipMode() }
        exitButton.setOnClickListener {
            super.onBackPressed();
        }

        for (characteristic in characteristics) {
            when (characteristic.uuid) {
                sensorUUID -> {
                    ConnectionManager.enableNotifications(device, characteristic)
                    log("Enabling notifications from ${characteristic.uuid}")
                }
                writeUUID -> {
                    ConnectionManager.writeCharacteristic(device, characteristic, byteArrayOfInts(0xF0, 0xB0, 0x01, 0x01, 0xA2))
                    log("Writing activation string to ${characteristic.uuid}")
                }
            }
        }
    }

    override fun onDestroy() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean, newConfig: Configuration?) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {

        } else {
            pipButton.visibility = View.VISIBLE
            exitButton.visibility = View.VISIBLE
        }
    }

    fun enterPipMode() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(5, 4))
            .build()
        pipButton.visibility = View.INVISIBLE
        exitButton.visibility = View.INVISIBLE
        enterPictureInPictureMode(params)

        // Trigger Home
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
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

    private fun updateCadence() {
        runOnUiThread {
            cadence.text = cadenceVal.toString()
        }
    }

    private fun updateResistance() {
        var r = ((0.0116058 * resistanceVal.toFloat().pow(3)) + (-0.568562 * resistanceVal.toFloat().pow(2)) + (10.4126 * resistanceVal.toFloat()) - 31.4807).toUInt()
        runOnUiThread {
            resistance.text = r.toString()
        }
    }

    private fun updatePower() {
        var p = 0u
        if (resistanceVal > 0u && cadenceVal > 0u) {
            p = (1.090112f.pow(resistanceVal.toFloat()) * 1.015343f.pow(cadenceVal.toFloat()) * 7.228958).toUInt()
        }
        runOnUiThread {
            power.text = p.toString()
        }
    }

    private fun showCharacteristicOptions(characteristic: BluetoothGattCharacteristic) {
        characteristicProperties[characteristic]?.let { properties ->
            selector("Select an action to perform", properties.map { it.action }) { _, i ->
                when (properties[i]) {
                    CharacteristicProperty.Readable -> {
                        log("Reading from ${characteristic.uuid}")
                        ConnectionManager.readCharacteristic(device, characteristic)
                    }
                    CharacteristicProperty.Writable, CharacteristicProperty.WritableWithoutResponse -> {
                        showWritePayloadDialog(characteristic)
                    }
                    CharacteristicProperty.Notifiable, CharacteristicProperty.Indicatable -> {
                        if (notifyingCharacteristics.contains(characteristic.uuid)) {
                            log("Disabling notifications on ${characteristic.uuid}")
                            ConnectionManager.disableNotifications(device, characteristic)
                        } else {
                            log("Enabling notifications on ${characteristic.uuid}")
                            ConnectionManager.enableNotifications(device, characteristic)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showWritePayloadDialog(characteristic: BluetoothGattCharacteristic) {
        val hexField = layoutInflater.inflate(R.layout.edittext_hex_payload, null) as EditText
        alert {
            customView = hexField
            isCancelable = false
            yesButton {
                with(hexField.text.toString()) {
                    if (isNotBlank() && isNotEmpty()) {
                        val bytes = hexToBytes()
                        log("Writing to ${characteristic.uuid}: ${bytes.toHexString()}")
                        ConnectionManager.writeCharacteristic(device, characteristic, bytes)
                    } else {
                        log("Please enter a hex payload to write to ${characteristic.uuid}")
                    }
                }
            }
            noButton {}
        }.show()
        hexField.showKeyboard()
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                runOnUiThread {
                    alert {
                        title = "Disconnected"
                        message = "Disconnected from device."
                        positiveButton("OK") { onBackPressed() }
                    }.show()
                }
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
                        cadenceVal = characteristic.value[9].toUInt().shl(8) + characteristic.value[10].toUInt()
                        updateCadence()
                        updatePower()
                        log("Cadence")
                    }
                    0xD2.toByte() -> {
                        resistanceVal = characteristic.value[3].toUInt()
                        updateResistance()
                        updatePower()
                        log("Resistance")
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

    private enum class CharacteristicProperty {
        Readable,
        Writable,
        WritableWithoutResponse,
        Notifiable,
        Indicatable;

        val action
            get() = when (this) {
                Readable -> "Read"
                Writable -> "Write"
                WritableWithoutResponse -> "Write Without Response"
                Notifiable -> "Toggle Notifications"
                Indicatable -> "Toggle Indications"
            }
    }

    private fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun EditText.showKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        requestFocus()
        inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun String.hexToBytes() =
        this.chunked(2).map { it.toUpperCase(Locale.US).toInt(16).toByte() }.toByteArray()

    private fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }
}

