<?php

namespace Aura\EspProvisioning\Events;

use Illuminate\Foundation\Events\Dispatchable;

class DevicesFound
{
    use Dispatchable;

    /**
     * @param  array<int, array{name: string, address: string}>  $devices
     */
    public function __construct(
        public readonly array $devices,
    ) {}
}
