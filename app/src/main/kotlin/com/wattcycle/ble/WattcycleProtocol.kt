package com.wattcycle.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

object WattcycleProtocol {
    // BLE UUIDs
    const val SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
    const val WRITE_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"
    const val NOTIFY_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
    const val AUTH_UUID = "0000fffa-0000-1000-8000-00805f9b34fb"
    
    // Protocol constants
    const val FRAME_HEAD: Byte = 0x7E
    const val FRAME_TAIL: Byte = 0x0D
    const val FUNC_READ: Byte = 0x03
    const val DEVICE_ADDR: Byte = 0x01
    
    // DP Addresses
    const val DP_ANALOG_QUANTITY = 140  // 0x8C
    const val DP_PRODUCT_INFO = 146     // 0x92
    
    val AUTH_KEY = "HiLink".toByteArray()
    val DEVICE_NAME_PREFIXES = listOf("XDZN", "WT")
    
    // CRC16 Lookup tables (Modbus)
    private val CRC_HI = byteArrayOf(
        0x00, 0xC1.toByte(), 0x81.toByte(), 0x40, 0x01, 0xC0.toByte(), 0x80.toByte(), 0x41, 
        0x01, 0xC0.toByte(), 0x80.toByte(), 0x41, 0x00, 0xC1.toByte(), 0x81.toByte(), 0x40,
        // ... (truncated for brevity - full table in real implementation)
        0x00, 0xC1.toByte(), 0x81.toByte(), 0x40, 0x01, 0xC0.toByte(), 0x80.toByte(), 0x41
    )
    
    fun modbusCrc16(data: ByteArray): Int {
        var crcHi = 0xFF
        var crcLo = 0xFF
        for (b in data) {
            val idx = (crcHi xor b.toInt()) and 0xFF
            crcHi = (crcLo xor CRC_HI[idx].toInt()) and 0xFF
            crcLo = 0xFF // Simplified - full implementation needed
        }
        return ((crcLo shl 8) or crcHi) and 0xFFFF
    }
    
    fun buildReadFrame(address: Int): ByteArray {
        val buf = ByteBuffer.allocate(11).order(ByteOrder.BIG_ENDIAN)
        buf.put(FRAME_HEAD)
        buf.put(0x00)  // version
        buf.put(DEVICE_ADDR)
        buf.put(FUNC_READ)
        buf.putShort(address.toShort())
        buf.putShort(0)  // read_count
        
        val payload = buf.array().copyOf(8)
        val crc = modbusCrc16(payload)
        buf.putShort(crc.toShort())
        buf.put(FRAME_TAIL)
        
        return buf.array()
    }
    
    fun parseCurrentNegative(data: ByteArray, offset: Int): Pair<Float, Int> {
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        val isNegative = (b0 and 0x80) != 0
        val hasDecimal = (b0 and 0x40) != 0
        val raw = b1 or ((b0 and 0x3F) shl 8)
        var current = if (hasDecimal) raw / 10.0f else raw.toFloat()
        if (isNegative) current = -current
        return Pair(current, offset + 2)
    }
}

data class BatteryData(
    val name: String,
    val address: String,
    val soc: Int = 0,
    val voltage: Float = 0f,
    val current: Float = 0f,
    val remainingCapacity: Float = 0f,
    val totalCapacity: Float = 0f,
    val cellVoltages: List<Float> = emptyList(),
    val temperature: Float = 0f,
    val isConnected: Boolean = false
)
