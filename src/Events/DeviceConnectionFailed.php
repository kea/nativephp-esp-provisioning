<?php

namespace Aura\EspProvisioning\Events;

use Illuminate\Foundation\Events\Dispatchable;

class DeviceConnectionFailed
{
    use Dispatchable;

    public function __construct(
        public readonly string $deviceName,
        public readonly string $error,
    ) {}
}
