<?php

namespace Aura\EspProvisioning\Facades;

use Illuminate\Support\Facades\Facade;

/**
 * @method static mixed searchBleDevices(string $prefix = 'PROV_')
 * @method static mixed createDevice(string $deviceName, string $transport = 'ble', int $security = 1)
 * @method static mixed connectBle(string $deviceName)
 * @method static mixed connectSoftAp(string $deviceName, string $password = '')
 * @method static mixed scanQr()
 * @method static mixed setProofOfPossession(string $deviceName, string $pop)
 * @method static mixed scanNetworks(string $deviceName)
 * @method static mixed provision(string $deviceName, string $ssid, string $passphrase)
 * @method static mixed sendData(string $deviceName, string $path, string $data)
 *
 * @see \Aura\EspProvisioning\EspProvisioning
 */
class EspProvisioning extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return \Aura\EspProvisioning\EspProvisioning::class;
    }
}
