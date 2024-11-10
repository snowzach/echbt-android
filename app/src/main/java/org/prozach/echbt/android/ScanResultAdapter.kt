package org.prozach.echbt.android

import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.prozach.echbt.android.databinding.RowScanResultBinding

class ScanResultAdapter(
    private val items: List<ScanResult>,
    private val onClickListener: ((device: ScanResult) -> Unit)
) : RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {

    private var _binding: RowScanResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        _binding = RowScanResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false)
        val view = binding.root
        return ViewHolder(view, binding, onClickListener)

//        val view = parent.context.layoutInflater.inflate(
//            R.layout.row_scan_result,
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
        private val binding: RowScanResultBinding,
        private val onClickListener: ((device: ScanResult) -> Unit)
    ) : RecyclerView.ViewHolder(view) {

        fun bind(result: ScanResult) {
            binding.deviceName.text = result.device.name ?: "Unnamed"
            binding.macAddress.text = result.device.address
            binding.signalStrength.text = "${result.rssi} dBm"
            view.setOnClickListener { onClickListener.invoke(result) }
        }
    }
}
