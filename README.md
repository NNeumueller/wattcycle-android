# Wattcycle BLE Android App

Android application for discovering and monitoring Wattcycle/XDZN battery management systems over Bluetooth Low Energy.

## Features

- 🔍 **Auto-discovery**: Automatically scans for and lists all Wattcycle batteries in range
- 📊 **Live monitoring**: Displays battery state of charge (SoC), voltage, current, and capacity
- 🔋 **Multi-battery support**: Shows all batteries in range with individual cards
- 🎨 **Simple UI**: Clean, Material Design interface with battery status cards

## Supported Devices

- XDZN/Wattcycle BMS devices with BLE (prefix: XDZN or WT)
- Tested with XDZN_001_EF2F (4S LiFePO4, 314 Ah)

## Protocol

Based on the reverse-engineered Wattcycle BLE protocol. See [wattcycle_ble](https://github.com/qume/wattcycle_ble) for Python implementation and protocol documentation.

## Permissions

The app requires the following permissions:
- `BLUETOOTH_SCAN` - To scan for BLE devices
- `BLUETOOTH_CONNECT` - To connect to batteries
- `ACCESS_FINE_LOCATION` - Required for BLE scanning on Android

## Installation

Download the latest APK from [Releases](https://github.com/qume/wattcycle-android/releases).

## Building

```bash
./gradlew assembleRelease
```

The APK will be in `app/build/outputs/apk/release/`.

## Development

Built with:
- Kotlin
- Android SDK 34 (minimum SDK 26)
- Material Design Components
- Coroutines for async operations

## License

MIT
