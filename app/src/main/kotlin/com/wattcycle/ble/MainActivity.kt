package com.wattcycle.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BatteryAdapter
    private val batteries = mutableListOf<BatteryData>()
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: return
            
            // Check if it's a Wattcycle device
            if (WattcycleProtocol.DEVICE_NAME_PREFIXES.any { deviceName.startsWith(it) }) {
                val existing = batteries.find { it.address == device.address }
                if (existing == null) {
                    batteries.add(BatteryData(
                        name = deviceName,
                        address = device.address
                    ))
                    runOnUiThread {
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        recyclerView = findViewById(R.id.batteryList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = BatteryAdapter(batteries)
        recyclerView.adapter = adapter
        
        checkPermissions()
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
        } else {
            startScanning()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startScanning()
            } else {
                Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startScanning() {
        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
        
        // Continuous scanning with periodic updates
        lifecycleScope.launch {
            while (true) {
                delay(5000)
                runOnUiThread {
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }
}

class BatteryAdapter(private val batteries: List<BatteryData>) : 
    RecyclerView.Adapter<BatteryAdapter.BatteryViewHolder>() {
    
    class BatteryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.batteryName)
        val addressText: TextView = view.findViewById(R.id.batteryAddress)
        val socText: TextView = view.findViewById(R.id.batterySoc)
        val voltageText: TextView = view.findViewById(R.id.batteryVoltage)
        val currentText: TextView = view.findViewById(R.id.batteryCurrent)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BatteryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.battery_card, parent, false)
        return BatteryViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: BatteryViewHolder, position: Int) {
        val battery = batteries[position]
        holder.nameText.text = battery.name
        holder.addressText.text = battery.address
        holder.socText.text = "SoC: ${battery.soc}%"
        holder.voltageText.text = "%.2f V".format(battery.voltage)
        holder.currentText.text = "%.1f A".format(battery.current)
    }
    
    override fun getItemCount() = batteries.size
}
