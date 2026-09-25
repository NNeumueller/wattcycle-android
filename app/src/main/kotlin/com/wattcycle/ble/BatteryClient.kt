package com.wattcycle.ble

import android.bluetooth.*
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class BatteryClient(
    private val context: Context,
    private val device: BluetoothDevice,
    private val onDataUpdate: (BatteryData) -> Unit
) {
    private var gatt: BluetoothGatt? = null
    private var isConnected = false
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var responseBuffer = ByteArray(0)
    private var expectedLength = 0
    private var responseContinuation: CancellableContinuation<ByteArray>? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected")
                    isConnected = false
                    cleanup()
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UUID.fromString(WattcycleProtocol.SERVICE_UUID))
                if (service != null) {
                    writeChar = service.getCharacteristic(UUID.fromString(WattcycleProtocol.WRITE_UUID))
                    notifyChar = service.getCharacteristic(UUID.fromString(WattcycleProtocol.NOTIFY_UUID))
                    val authChar = service.getCharacteristic(UUID.fromString(WattcycleProtocol.AUTH_UUID))
                    
                    // Enable notifications
                    notifyChar?.let {
                        gatt.setCharacteristicNotification(it, true)
                        val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                    
                    // Authenticate
                    authChar?.let {
                        it.value = WattcycleProtocol.AUTH_KEY
                        gatt.writeCharacteristic(it)
                    }
                    
                    isConnected = true
                    
                    // Start polling
                    scope.launch {
                        delay(1000) // Wait for auth
                        startPolling()
                    }
                }
            }
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            
            // Append to buffer
            responseBuffer += data
            
            // Calculate expected length from first packet
            if (expectedLength == 0 && responseBuffer.size >= 8) {
                val dataLen = ByteBuffer.wrap(responseBuffer, 6, 2).order(ByteOrder.BIG_ENDIAN).short.toInt()
                expectedLength = dataLen + 11
            }
            
            // Check if we have complete frame
            if (expectedLength > 0 && responseBuffer.size >= expectedLength) {
                val completeFrame = responseBuffer.copyOf(expectedLength)
                responseBuffer = ByteArray(0)
                expectedLength = 0
                
                responseContinuation?.resume(completeFrame)
                responseContinuation = null
            }
        }
    }
    
    fun connect() {
        gatt = device.connectGatt(context, false, gattCallback)
    }
    
    fun disconnect() {
        scope.cancel()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
    
    private fun cleanup() {
        writeChar = null
        notifyChar = null
        responseBuffer = ByteArray(0)
        expectedLength = 0
        responseContinuation?.cancel()
        responseContinuation = null
    }

    private suspend fun sendCommand(command: ByteArray): ByteArray =
    withTimeout(3_000L) {
        suspendCancellableCoroutine { cont ->

            if (!isConnected || writeChar == null || gatt == null) {
                cont.cancel(
                    CancellationException("Bluetooth is not connected")
                )
                return@suspendCancellableCoroutine
            }

            responseBuffer = ByteArray(0)
            expectedLength = 0
            responseContinuation = cont

            cont.invokeOnCancellation {
                if (responseContinuation === cont) {
                    responseContinuation = null
                }
            }

            writeChar?.value = command
            gatt?.writeCharacteristic(writeChar)
        }
    }
    
    private suspend fun startPolling() {
        while (isConnected) {
            try {
                readBatteryData()
                delay(5000) // Poll every 5 seconds
            } catch (e: Exception) {
                Log.e(TAG, "Error reading battery data", e)
                delay(5000)
            }
        }
    }
    
    private suspend fun readBatteryData() {
        try {
            // Read analog quantity
            val command = WattcycleProtocol.buildReadFrame(WattcycleProtocol.DP_ANALOG_QUANTITY)
            val response = sendCommand(command)
            
            if (response.isNotEmpty()) {
                val frame = WattcycleProtocol.parseFrame(response)
                if (frame != null) {
                    val analogData = WattcycleProtocol.parseAnalogQuantity(frame.data)
                    
                    if (analogData != null && hasBluetoothConnectPermission()) {
                        val batteryData = BatteryData(
                            name = device.name ?: "Unknown",
                            address = device.address,
                            soc = analogData.soc,
                            voltage = analogData.voltage,
                            current = analogData.current,
                            remainingCapacity = analogData.remainingCapacity,
                            totalCapacity = analogData.totalCapacity,
                            cellVoltages = analogData.cellVoltages,
                            temperature = analogData.mosTemperature,
                            isConnected = true
                        )
                        
                        onDataUpdate(batteryData)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in readBatteryData", e)
        }
    }
    
    companion object {
        private const val TAG = "BatteryClient"
    }
}
