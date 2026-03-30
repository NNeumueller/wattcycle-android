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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BatteryAdapter
    private val batteries = mutableMapOf<String, BatteryData>()
    private val clients = mutableMapOf<String, BatteryClient>()
    
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
                val address = device.address
                
                if (!batteries.containsKey(address)) {
                    // Add new battery
                    batteries[address] = BatteryData(
                        name = deviceName,
                        address = address
                    )
                    runOnUiThread {
                        adapter.updateBatteries(batteries.values.toList())
                    }
                    
                    // Connect to battery
                    val client = BatteryClient(this@MainActivity, device) { updatedData ->
                        batteries[address] = updatedData
                        runOnUiThread {
                            adapter.updateBatteries(batteries.values.toList())
                        }
                    }
                    clients[address] = client
                    client.connect()
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        recyclerView = findViewById(R.id.batteryList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = BatteryAdapter()
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
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        clients.values.forEach { it.disconnect() }
    }
}

class BatteryAdapter : RecyclerView.Adapter<BatteryAdapter.BatteryViewHolder>() {
    
    private var batteries: List<BatteryData> = emptyList()
    
    fun updateBatteries(newBatteries: List<BatteryData>) {
        batteries = newBatteries
        notifyDataSetChanged()
    }
    
    class BatteryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.batteryName)
        val addressText: TextView = view.findViewById(R.id.batteryAddress)
        val socText: TextView = view.findViewById(R.id.batterySoc)
        val voltageText: TextView = view.findViewById(R.id.batteryVoltage)
        val currentText: TextView = view.findViewById(R.id.batteryCurrent)
        val capacityText: TextView = view.findViewById(R.id.batteryCapacity)
        val tempText: TextView = view.findViewById(R.id.batteryTemp)
        val statusText: TextView = view.findViewById(R.id.batteryStatus)
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
        holder.capacityText.text = "%.1f / %.1f Ah".format(battery.remainingCapacity, battery.totalCapacity)
        holder.tempText.text = "%.1f °C".format(battery.temperature)
        holder.statusText.text = if (battery.isConnected) "● Connected" else "○ Scanning..."
    }
    
    override fun getItemCount() = batteries.size
}
