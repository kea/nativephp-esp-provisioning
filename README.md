# NativePHP ESP Provisioning

A [NativePHP Mobile](https://nativephp.com/mobile) plugin for provisioning **ESP32 / ESP-IDF devices** over **BLE** or **SoftAP**, powered by Espressif's official [esp-idf-provisioning-android](https://github.com/espressif/esp-idf-provisioning-android) library.

Send Wi-Fi credentials to your IoT devices straight from your Laravel app — no Kotlin required.

> **Platform support:** Android only (for now). iOS support is planned.

## Features

- 📡 **BLE device discovery** — scan for ESP devices in provisioning mode by name prefix
- 🔗 **BLE & SoftAP transports** — connect over Bluetooth Low Energy or the device's own access point
- 📷 **QR code provisioning** — scan the standard ESP provisioning QR code to auto-configure device name, transport, security and proof of possession
- 🔐 **Security 0 / 1 / 2** — supports all ESP-IDF provisioning security schemes, including proof of possession
- 📶 **Wi-Fi network scanning** — list networks visible to the ESP device before provisioning
- 📨 **Custom endpoint data** — exchange arbitrary data with the device over the provisioning channel
- ⚡ **Native Laravel events** — every async step dispatches a real Laravel event you can listen to (Livewire included)
- 🌐 **JavaScript bridge** — first-class ES module for React / Vue / Alpine frontends

## Requirements

- PHP 8.2+
- Laravel app running [NativePHP Mobile](https://nativephp.com/mobile)
- Android 8.0+ (API 26)
- An ESP32 flashed with firmware using the [ESP-IDF provisioning component](https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-reference/provisioning/wifi_provisioning.html) (e.g. the `wifi_prov_mgr` example)

## Installation

```bash
composer require aura/nativephp-esp-provisioning
```

Then rebuild your native app so the plugin's Kotlin sources, permissions and Gradle dependencies are picked up:

```bash
php artisan native:install android
php artisan native:run android
```

That's it — the service provider is auto-discovered and all Android permissions are declared by the plugin manifest.

## Usage

### Quick start: full BLE provisioning flow

```php
use Aura\EspProvisioning\Facades\EspProvisioning;

// 1. Register the device (name, transport, security scheme)
EspProvisioning::createDevice('PROV_A1B2C3', transport: 'ble', security: 1);

// 2. Scan for it over BLE — results arrive via the DevicesFound event
EspProvisioning::searchBleDevices(prefix: 'PROV_');

// 3. Set the proof of possession (required for security 1/2)
EspProvisioning::setProofOfPossession('PROV_A1B2C3', 'abcd1234');

// 4. Connect — DeviceConnected / DeviceConnectionFailed will fire
EspProvisioning::connectBle('PROV_A1B2C3');

// 5. Ask the device which Wi-Fi networks it can see — NetworksScanned fires
EspProvisioning::scanNetworks('PROV_A1B2C3');

// 6. Send the credentials — ProvisioningSucceeded / ProvisioningFailed fires
EspProvisioning::provision('PROV_A1B2C3', ssid: 'MyHomeWiFi', passphrase: 'secret');
```

All calls return immediately; the actual work happens on the device and the result is delivered through [events](#events).

### QR code provisioning

If your firmware prints the standard ESP provisioning QR code, one call opens the camera and extracts everything (device name, transport, security, proof of possession):

```php
EspProvisioning::scanQr();
```

### SoftAP transport

For devices provisioning over their own access point instead of BLE:

```php
EspProvisioning::createDevice('PROV_A1B2C3', transport: 'softap', security: 1);
EspProvisioning::connectSoftAp('PROV_A1B2C3', password: 'abcd1234');
```

### Custom endpoint data

Exchange application-specific data with the device over the secure provisioning channel (e.g. a device token or MQTT config):

```php
EspProvisioning::sendData('PROV_A1B2C3', path: 'custom-data', data: base64_encode($payload));
```

## Events

Every asynchronous step dispatches a native Laravel event:

| Event | Payload | Fired when |
|---|---|---|
| `DevicesFound` | `array $devices` — `[{name, address}, …]` | BLE scan completes (or fails — empty list + error) |
| `DeviceConnected` | `string $deviceName` | Device session is established |
| `DeviceConnectionFailed` | `string $deviceName`, `string $error` | Connection or session setup fails |
| `NetworksScanned` | `string $deviceName`, `array $networks` — `[{ssid, rssi, auth}, …]` | Wi-Fi scan on the device completes |
| `ProvisioningSucceeded` | `string $deviceName` | Device confirms it joined the network |
| `ProvisioningFailed` | `string $deviceName`, `string $error` | Credentials rejected or Wi-Fi join failed |

All events live under the `Aura\EspProvisioning\Events` namespace.

### Listening in Livewire

```php
use Aura\EspProvisioning\Events\DevicesFound;
use Aura\EspProvisioning\Events\ProvisioningSucceeded;
use Livewire\Attributes\On;
use Livewire\Component;

class ProvisionDevice extends Component
{
    public array $devices = [];

    #[On('native:'.DevicesFound::class)]
    public function devicesFound(array $devices): void
    {
        $this->devices = $devices;
    }

    #[On('native:'.ProvisioningSucceeded::class)]
    public function provisioned(string $deviceName): void
    {
        session()->flash('status', "{$deviceName} is now online!");
    }
}
```

### Listening in a class-based listener

```php
use Aura\EspProvisioning\Events\NetworksScanned;
use Illuminate\Support\Facades\Event;

Event::listen(NetworksScanned::class, function (NetworksScanned $event) {
    // $event->deviceName, $event->networks
});
```

## JavaScript bridge

For SPA frontends (React, Vue, Alpine…), the plugin ships an ES module mirroring the PHP API:

```js
import {
    searchBleDevices,
    createDevice,
    connectBle,
    setProofOfPossession,
    scanNetworks,
    provision,
} from 'vendor/aura/nativephp-esp-provisioning/resources/js/esp-provisioning.js';

await createDevice('PROV_A1B2C3', 'ble', 1);
await searchBleDevices('PROV_');
await setProofOfPossession('PROV_A1B2C3', 'abcd1234');
await connectBle('PROV_A1B2C3');
await provision('PROV_A1B2C3', 'MyHomeWiFi', 'secret');
```

## API reference

| Method | Description |
|---|---|
| `searchBleDevices(string $prefix = 'PROV_')` | Scan for BLE ESP devices in provisioning mode, filtered by name prefix |
| `createDevice(string $deviceName, string $transport = 'ble', int $security = 1)` | Register a device instance. `$transport`: `ble` or `softap`. `$security`: `0`, `1`, `2` |
| `connectBle(string $deviceName)` | Connect over BLE (device must be found via `searchBleDevices` first) |
| `connectSoftAp(string $deviceName, string $password = '')` | Connect to the device's SoftAP network |
| `scanQr()` | Open the camera and read the ESP provisioning QR code |
| `setProofOfPossession(string $deviceName, string $pop)` | Set the PoP secret (required for security 1/2) |
| `scanNetworks(string $deviceName)` | Ask the connected device to scan for Wi-Fi networks |
| `provision(string $deviceName, string $ssid, string $passphrase)` | Send Wi-Fi credentials to the device |
| `sendData(string $deviceName, string $path, string $data)` | Send data to a custom endpoint (base64 recommended) |

## Permissions

The plugin declares the following Android permissions automatically:

- `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` — BLE discovery and connection
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` — required by Android for BLE scanning
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` — SoftAP transport
- `CAMERA` — QR code scanning

Runtime permission prompts are handled by the plugin when a call needs them.

## Troubleshooting

- **`DevicesFound` fires with an empty list** — make sure the ESP is in provisioning mode (usually after a factory reset or first boot), Bluetooth and Location are enabled on the phone, and the name prefix matches your firmware's `PROV_` prefix.
- **`DeviceConnectionFailed` right after connecting** — wrong security version in `createDevice`, or missing/incorrect proof of possession. It must match your firmware configuration.
- **`ProvisioningFailed`** — usually a wrong Wi-Fi passphrase, or the ESP32 can't reach the network (note: classic ESP32 only supports 2.4 GHz networks).
- **BLE scan fails silently on Android 12+** — check that the user granted the "Nearby devices" permission.

## License

Apache-2.0. See [LICENSE](LICENSE) for details.

Built on Espressif's [esp-idf-provisioning-android](https://github.com/espressif/esp-idf-provisioning-android).
