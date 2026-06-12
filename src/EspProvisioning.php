<?php

namespace Aura\EspProvisioning;

use NativePHP\Mobile\Bridge\Bridge;

class EspProvisioning
{
    /**
     * Scan for BLE ESP devices in provisioning mode.
     *
     * @param  string  $prefix  Device name prefix to filter (e.g. "PROV_")
     */
    public function searchBleDevices(string $prefix = 'PROV_'): mixed
    {
        return Bridge::call('EspProvisioning.SearchBleDevices', [
            'prefix' => $prefix,
        ]);
    }

    /**
     * Manually create an ESP device instance.
     *
     * @param  string  $deviceName   Device name
     * @param  string  $transport    "ble" or "softap"
     * @param  int     $security     Security version: 0, 1, or 2
     */
    public function createDevice(string $deviceName, string $transport = 'ble', int $security = 1): mixed
    {
        return Bridge::call('EspProvisioning.CreateDevice', [
            'deviceName' => $deviceName,
            'transport' => $transport,
            'security' => $security,
        ]);
    }

    /**
     * Connect to an ESP device via BLE.
     *
     * @param  string  $deviceName  Device name returned from searchBleDevices
     */
    public function connectBle(string $deviceName): mixed
    {
        return Bridge::call('EspProvisioning.ConnectBLE', [
            'deviceName' => $deviceName,
        ]);
    }

    /**
     * Connect to an ESP device via SoftAP.
     *
     * @param  string  $deviceName  Device name / SSID
     * @param  string  $password    SoftAP password
     */
    public function connectSoftAp(string $deviceName, string $password = ''): mixed
    {
        return Bridge::call('EspProvisioning.ConnectSoftAP', [
            'deviceName' => $deviceName,
            'password' => $password,
        ]);
    }

    /**
     * Scan a QR code to extract device provisioning metadata.
     */
    public function scanQr(): mixed
    {
        return Bridge::call('EspProvisioning.ScanQR', []);
    }

    /**
     * Set the Proof of Possession for the connected device.
     *
     * @param  string  $deviceName  Device name
     * @param  string  $pop         Proof of possession string
     */
    public function setProofOfPossession(string $deviceName, string $pop): mixed
    {
        return Bridge::call('EspProvisioning.SetProofOfPossession', [
            'deviceName' => $deviceName,
            'pop' => $pop,
        ]);
    }

    /**
     * Scan available WiFi networks via the connected device.
     *
     * @param  string  $deviceName  Device name
     */
    public function scanNetworks(string $deviceName): mixed
    {
        return Bridge::call('EspProvisioning.ScanNetworks', [
            'deviceName' => $deviceName,
        ]);
    }

    /**
     * Provision the device with WiFi credentials.
     *
     * @param  string  $deviceName  Device name
     * @param  string  $ssid        WiFi SSID
     * @param  string  $passphrase  WiFi passphrase
     */
    public function provision(string $deviceName, string $ssid, string $passphrase): mixed
    {
        return Bridge::call('EspProvisioning.Provision', [
            'deviceName' => $deviceName,
            'ssid' => $ssid,
            'passphrase' => $passphrase,
        ]);
    }

    /**
     * Send custom data to the connected device.
     *
     * @param  string  $deviceName  Device name
     * @param  string  $path        Custom endpoint path
     * @param  string  $data        Data to send (base64 encoded recommended)
     */
    public function sendData(string $deviceName, string $path, string $data): mixed
    {
        return Bridge::call('EspProvisioning.SendData', [
            'deviceName' => $deviceName,
            'path' => $path,
            'data' => $data,
        ]);
    }
}
