package com.aura.plugins.espprovisioning

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPDevice
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.WiFiAccessPoint
import com.espressif.provisioning.listeners.BleScanListener
import com.espressif.provisioning.listeners.ProvisionListener
import com.espressif.provisioning.listeners.ResponseListener
import com.espressif.provisioning.listeners.WiFiScanListener
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse
import com.nativephp.mobile.utils.NativeActionCoordinator
import org.json.JSONArray
import org.json.JSONObject

private val mainHandler = Handler(Looper.getMainLooper())

private val BLE_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

private object DeviceRegistry {
    val espDevices = mutableMapOf<String, ESPDevice>()
    val bleDevices = mutableMapOf<String, BluetoothDevice>()
    val serviceUuids = mutableMapOf<String, String>()
}

private fun dispatch(activity: FragmentActivity, event: String, payload: JSONObject) {
    mainHandler.post {
        NativeActionCoordinator.dispatchEvent(activity, event, payload.toString())
    }
}

private fun hasBlePermissions(activity: FragmentActivity): Boolean =
    BLE_PERMISSIONS.all {
        ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
    }

private fun requestBlePermissions(activity: FragmentActivity) {
    ActivityCompat.requestPermissions(activity, BLE_PERMISSIONS, 9001)
}

object EspProvisioningFunctions {

    class SearchBleDevices(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val prefix = parameters["prefix"] as? String ?: "PROV_"

            mainHandler.post {
                if (!hasBlePermissions(activity)) {
                    requestBlePermissions(activity)
                    dispatch(
                        activity,
                        "Aura\\EspProvisioning\\Events\\DevicesFound",
                        JSONObject()
                            .put("devices", JSONArray())
                            .put("error", "Bluetooth permissions not granted. Please allow Bluetooth access and try again.")
                    )
                    return@post
                }

                try {
                    val manager = ESPProvisionManager.getInstance(activity)
                    val found = LinkedHashMap<String, BluetoothDevice>()

                    manager.searchBleEspDevices(prefix, object : BleScanListener {
                        override fun scanStartFailed() {
                            dispatch(
                                activity,
                                "Aura\\EspProvisioning\\Events\\DevicesFound",
                                JSONObject().put("devices", JSONArray()).put("error", "Scan failed to start")
                            )
                        }

                        override fun onPeripheralFound(device: BluetoothDevice, scanResult: ScanResult) {
                            if (!found.containsKey(device.address)) {
                                found[device.address] = device
                                val name = try { device.name } catch (e: SecurityException) { null }
                                val key = name ?: device.address
                                DeviceRegistry.bleDevices[key] = device
                                val uuid = scanResult.scanRecord?.serviceUuids?.firstOrNull()?.toString() ?: ""
                                DeviceRegistry.serviceUuids[key] = uuid
                            }
                        }

                        override fun scanCompleted() {
                            val arr = JSONArray()
                            found.values.forEach { d ->
                                val name = try { d.name } catch (e: SecurityException) { null }
                                arr.put(JSONObject().put("name", name ?: "").put("address", d.address))
                            }
                            dispatch(
                                activity,
                                "Aura\\EspProvisioning\\Events\\DevicesFound",
                                JSONObject().put("devices", arr)
                            )
                        }

                        override fun onFailure(exception: Exception) {
                            dispatch(
                                activity,
                                "Aura\\EspProvisioning\\Events\\DevicesFound",
                                JSONObject().put("devices", JSONArray()).put("error", exception.message ?: "Scan failed")
                            )
                        }
                    })
                } catch (e: Exception) {
                    dispatch(
                        activity,
                        "Aura\\EspProvisioning\\Events\\DevicesFound",
                        JSONObject().put("devices", JSONArray()).put("error", e.message ?: "BLE error")
                    )
                }
            }

            return BridgeResponse.success(mapOf("status" to "scanning"))
        }
    }

    class CreateDevice(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val deviceName = parameters["deviceName"] as? String
                ?: return BridgeResponse.error("MISSING_PARAM", "Missing deviceName")

            val transportRaw = parameters["transport"] as? String ?: "ble"
            val securityRaw = (parameters["security"] as? Number)?.toInt() ?: 1

            val transport = when (transportRaw.lowercase()) {
                "softap" -> ESPConstants.TransportType.TRANSPORT_SOFTAP
                else -> ESPConstants.TransportType.TRANSPORT_BLE
            }

            val security = when (securityRaw) {
                0 -> ESPConstants.SecurityType.SECURITY_0
                2 -> ESPConstants.SecurityType.SECURITY_2
                else -> ESPConstants.SecurityType.SECURITY_1
            }

            val pop = parameters["pop"] as? String ?: ""

            mainHandler.post {
                try {
                    val device = ESPProvisionManager.getInstance(activity).createESPDevice(transport, security)
                    device.setDeviceName(deviceName)
                    device.setProofOfPossession(pop)
                    DeviceRegistry.espDevices[deviceName] = device
                } catch (e: Exception) {
                    // Logged — caller checks registry on next call
                }
            }

            return BridgeResponse.success(mapOf("deviceName" to deviceName))
        }
    }

    class ConnectBLE(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val deviceName = parameters["deviceName"] as? String
                ?: return BridgeResponse.error("MISSING_PARAM", "Missing deviceName")

            val serviceUuid = parameters["serviceUuid"] as? String
                ?: DeviceRegistry.serviceUuids[deviceName]
                ?: ""

            val device = DeviceRegistry.espDevices[deviceName]
                ?: return BridgeResponse.error("DEVICE_NOT_FOUND", "Call CreateDevice first for $deviceName")

            val bleDevice = DeviceRegistry.bleDevices[deviceName]
                ?: return BridgeResponse.error("BLE_DEVICE_NOT_FOUND", "Run SearchBleDevices first to find $deviceName")

            mainHandler.post {
                val subscriber = object {
                    @Subscribe(threadMode = ThreadMode.MAIN)
                    fun onDeviceConnection(event: DeviceConnectionEvent) {
                        EventBus.getDefault().unregister(this)
                        when (event.eventType) {
                            ESPConstants.EVENT_DEVICE_CONNECTED -> {
                                // connectBLEDevice() overwrites deviceName with bluetoothDevice.getName()
                                // which can be null — restore it before provisioning uses it for SRP.
                                device.setDeviceName(deviceName)
                                dispatch(
                                    activity,
                                    "Aura\\EspProvisioning\\Events\\DeviceConnected",
                                    JSONObject().put("deviceName", deviceName)
                                )
                            }
                            else -> {
                                dispatch(
                                    activity,
                                    "Aura\\EspProvisioning\\Events\\DeviceConnectionFailed",
                                    JSONObject().put("deviceName", deviceName).put("error", "BLE connection failed")
                                )
                            }
                        }
                    }
                }
                try {
                    EventBus.getDefault().register(subscriber)
                    device.connectBLEDevice(bleDevice, serviceUuid)
                } catch (e: Exception) {
                    EventBus.getDefault().unregister(subscriber)
                    dispatch(
                        activity,
                        "Aura\\EspProvisioning\\Events\\DeviceConnectionFailed",
                        JSONObject().put("deviceName", deviceName).put("error", e.message ?: "Connect failed")
                    )
                }
            }

            return BridgeResponse.success(mapOf("status" to "connecting", "deviceName" to deviceName))
        }
    }

    class ConnectSoftAP(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val deviceName = parameters["deviceName"] as? String
                ?: return BridgeResponse.error("MISSING_PARAM", "Missing deviceName")

            val device = DeviceRegistry.espDevices[deviceName]
                ?: return BridgeResponse.error("DEVICE_NOT_FOUND", "Call CreateDevice first for $deviceName")

            mainHandler.post {
                try {
                    device.connectWiFiDevice()
                    dispatch(
                        activity,
                        "Aura\\EspProvisioning\\Events\\DeviceConnected",
                        JSONObject().put("deviceName", deviceName)
                    )
                } catch (e: Exception) {
                    dispatch(
                        activity,
                        "Aura\\EspProvisioning\\Events\\DeviceConnectionFailed",
                        JSONObject().put("deviceName", deviceName).put("error", e.message ?: "Connect failed")
                    )
                }
            }

            return BridgeResponse.success(mapOf("status" to "connecting", "deviceName" to deviceName))
        }
    }

    /**
     * QR scan requires a camera preview view (CameraSourcePreview or PreviewView) embedded in an
     * Activity layout — cannot be invoked headlessly from a bridge function.
     */
    class ScanQR(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            return BridgeResponse.error(
                "NOT_SUPPORTED",
                "ScanQR requires a camera preview view in an Activity layout."
            )
        }
    }

    class SetProofOfPossession(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val deviceName = parameters["deviceName"] as? String
                ?: return BridgeResponse.error("MISSING_PARAM", "Missing deviceName")

            val pop = parameters["pop"] as? String
                ?: return BridgeResponse.error("MISSING_PARAM", "Missing pop")

            val device = DeviceRegistry.espDevices[deviceName]
                ?: return BridgeResponse.error("DEVICE_NOT_FOUND", "Device $deviceName not found")

            mainHandler.post {
                try { device.setProofOfPossession(pop) } catch (_: Exception) {}
            }

            return BridgeResponse.success(mapOf("deviceName" to deviceName))
        }
    }

    class ScanNetworks(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val deviceName = parameters["deviceName"] as? String
                ?: return BridgeResponse.error("MISSING_PARAM", "Missing deviceName")

            val device = DeviceRegistry.espDevices[deviceName]
                ?: return BridgeResponse.error("DEVICE_NOT_FOUND", "Device $deviceName not found")

            mainHandler.post {
                try {
                    device.scanNetworks(object : WiFiScanListener {
                        override fun onWifiListReceived(wifiList: ArrayList<WiFiAccessPoint>) {
                            val arr = JSONArray()
                            wifiList.forEach { ap ->
                                arr.put(
                                    JSONObject()
                                        .put("ssid", ap.wifiName)
                                        .put("rssi", ap.rssi)
                                        .put("auth", ap.security.toString())
                                )
                            }
                            dispatch(
                                activity,
                                "Aura\\EspProvisioning\\Events\\NetworksScanned",
                                JSONObject().put("deviceName", deviceName).put("networks", arr)
                            )
                        }

                        override fun onWiFiScanFailed(exception: Exception) {
                            dispatch(
                                activity,
                                "Aura\\EspProvisioning\\Events\\NetworksScanned",
                                JSONObject()
                                    .put("deviceName", deviceName)
                                    .put("networks", JSONArray())
                                    .put("error", exception.message ?: "Scan failed")
                            )
                        }
                    })
                } catch (e: Exception) {
                    dispatch(
                        activity,
                        "Aura\\EspProvisioning\\Events\\NetworksScanned",
                        JSONObject().put("deviceName", deviceName).put("networks", JSONArray()).put("error", e.message ?: "Error")
                    )
                }
            }

            return BridgeResponse.success(mapOf("status" to "scanning_networks", "deviceName" to deviceName))
        }
    }

    class Provision(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val deviceName = parameters["deviceName"] as? String
                ?: return BridgeResponse.error("MISSING_PARAM", "Missing deviceName")

            val ssid = parameters["ssid"] as? String
                ?: return BridgeResponse.error("MISSING_PARAM", "Missing ssid")

            val passphrase = parameters["passphrase"] as? String ?: ""

            val device = DeviceRegistry.espDevices[deviceName]
                ?: return BridgeResponse.error("DEVICE_NOT_FOUND", "Device $deviceName not found")

            mainHandler.post {
                try {
                    device.provision(ssid, passphrase, object : ProvisionListener {
                        override fun createSessionFailed(exception: Exception) {
                            dispatch(activity, "Aura\\EspProvisioning\\Events\\ProvisioningFailed",
                                JSONObject().put("deviceName", deviceName).put("error", exception.message ?: "Session failed"))
                        }
                        override fun wifiConfigSent() {}
                        override fun wifiConfigFailed(exception: Exception) {
                            dispatch(activity, "Aura\\EspProvisioning\\Events\\ProvisioningFailed",
                                JSONObject().put("deviceName", deviceName).put("error", exception.message ?: "Config failed"))
                        }
                        override fun wifiConfigApplied() {}
                        override fun wifiConfigApplyFailed(exception: Exception) {
                            dispatch(activity, "Aura\\EspProvisioning\\Events\\ProvisioningFailed",
                                JSONObject().put("deviceName", deviceName).put("error", exception.message ?: "Apply failed"))
                        }
                        override fun provisioningFailedFromDevice(failureReason: ESPConstants.ProvisionFailureReason) {
                            dispatch(activity, "Aura\\EspProvisioning\\Events\\ProvisioningFailed",
                                JSONObject().put("deviceName", deviceName).put("error", failureReason.toString()))
                        }
                        override fun deviceProvisioningSuccess() {
                            dispatch(activity, "Aura\\EspProvisioning\\Events\\ProvisioningSucceeded",
                                JSONObject().put("deviceName", deviceName))
                            DeviceRegistry.espDevices.remove(deviceName)
                            DeviceRegistry.bleDevices.remove(deviceName)
                            DeviceRegistry.serviceUuids.remove(deviceName)
                        }
                        override fun onProvisioningFailed(exception: Exception) {
                            dispatch(activity, "Aura\\EspProvisioning\\Events\\ProvisioningFailed",
                                JSONObject().put("deviceName", deviceName).put("error", exception.message ?: "Provisioning failed"))
                        }
                    })
                } catch (e: Exception) {
                    dispatch(activity, "Aura\\EspProvisioning\\Events\\ProvisioningFailed",
                        JSONObject().put("deviceName", deviceName).put("error", e.message ?: "Error"))
                }
            }

            return BridgeResponse.success(mapOf("status" to "provisioning", "deviceName" to deviceName))
        }
    }

    class SendData(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val deviceName = parameters["deviceName"] as? String
                ?: return BridgeResponse.error("MISSING_PARAM", "Missing deviceName")

            val path = parameters["path"] as? String
                ?: return BridgeResponse.error("MISSING_PARAM", "Missing path")

            val data = parameters["data"] as? String
                ?: return BridgeResponse.error("MISSING_PARAM", "Missing data")

            val device = DeviceRegistry.espDevices[deviceName]
                ?: return BridgeResponse.error("DEVICE_NOT_FOUND", "Device $deviceName not found")

            val bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)

            mainHandler.post {
                try {
                    device.sendDataToCustomEndPoint(path, bytes, object : ResponseListener {
                        override fun onSuccess(returnData: ByteArray?) {}
                        override fun onFailure(exception: Exception) {}
                    })
                } catch (_: Exception) {}
            }

            return BridgeResponse.success(mapOf("status" to "sending", "deviceName" to deviceName, "path" to path))
        }
    }
}
