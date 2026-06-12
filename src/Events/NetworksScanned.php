<?php

namespace Aura\EspProvisioning\Events;

use Illuminate\Foundation\Events\Dispatchable;

class NetworksScanned
{
    use Dispatchable;

    /**
     * @param  string  $deviceName
     * @param  array<int, array{ssid: string, rssi: int, auth: string}>  $networks
     */
    public function __construct(
        public readonly string $deviceName,
        public readonly array $networks,
    ) {}
}
