<?php

namespace Aura\EspProvisioning\Events;

use Illuminate\Foundation\Events\Dispatchable;

class ProvisioningSucceeded
{
    use Dispatchable;

    public function __construct(
        public readonly string $deviceName,
    ) {}
}
