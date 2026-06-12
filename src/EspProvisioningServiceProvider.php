<?php

namespace Aura\EspProvisioning;

use Illuminate\Support\ServiceProvider;

class EspProvisioningServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(EspProvisioning::class);
    }

    public function boot(): void
    {
        //
    }
}
