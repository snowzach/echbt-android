package org.prozach.echbt.android

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.prozach.echbt.android.ble.printProperties
import org.prozach.echbt.android.databinding.RowCharacteristicBinding

class CharacteristicAdapter(
    private val items: List<BluetoothGattCharacteristic>,
    private val onClickListener: ((characteristic: BluetoothGattCharacteristic) -> Unit)
) : RecyclerView.Adapter<CharacteristicAdapter.ViewHolder>() {

    private var _binding: RowCharacteristicBinding? = null
    private val binding get() = _binding!!

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        _binding = RowCharacteristicBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false)
        val view = binding.root
        return ViewHolder(view, binding, onClickListener)
//        val view = parent.context.layoutInflater.inflate(
//            R.layout.row_characteristic,
//            parent,
//            false
//        )
//        return ViewHolder(view, onClickListener)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    class ViewHolder(
        private val view: View,
        private val binding: RowCharacteristicBinding,
        private val onClickListener: ((characteristic: BluetoothGattCharacteristic) -> Unit)
    ) : RecyclerView.ViewHolder(view) {

        fun bind(characteristic: BluetoothGattCharacteristic) {
            binding.characteristicUuid.text = characteristic.uuid.toString()
            binding.characteristicProperties.text = characteristic.printProperties()
            view.setOnClickListener { onClickListener.invoke(characteristic) }
        }
    }
}
