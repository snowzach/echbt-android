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
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Bundle
import android.os.ParcelUuid
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
import org.jetbrains.anko.alert
import org.jetbrains.anko.noButton
import org.jetbrains.anko.selector
import org.jetbrains.anko.yesButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ECHStatsActivity : AppCompatActivity() {

    private val sensorUUID = java.util.UUID.fromString("0bf669f4-45f2-11e7-9598-0800200c9a66")
    private val writeUUID = java.util.UUID.fromString("0bf669f2-45f2-11e7-9598-0800200c9a66")

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
                        val cadenceVal = characteristic.value[9].toUInt().shl(8) + characteristic.value[10].toUInt()

                        runOnUiThread {
                            cadence.text = cadenceVal.toString()
                        }
                        log("Cadence")
                    }
                    0xD2.toByte() -> {
                        val resistanceVal = characteristic.value[3].toUInt()

                        runOnUiThread {
                            resistance.text = resistanceVal.toString()
                        }
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

