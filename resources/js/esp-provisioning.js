const baseUrl = '/_native/api/call';

async function bridgeCall(method, params = {}) {
    const response = await fetch(baseUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ method, params }),
    });
    return response.json();
}

export async function searchBleDevices(prefix = 'PROV_') {
    return bridgeCall('EspProvisioning.SearchBleDevices', { prefix });
}

export async function createDevice(deviceName, transport = 'ble', security = 1) {
    return bridgeCall('EspProvisioning.CreateDevice', { deviceName, transport, security });
}

export async function connectBle(deviceName) {
    return bridgeCall('EspProvisioning.ConnectBLE', { deviceName });
}

export async function connectSoftAp(deviceName, password = '') {
    return bridgeCall('EspProvisioning.ConnectSoftAP', { deviceName, password });
}

export async function scanQr() {
    return bridgeCall('EspProvisioning.ScanQR', {});
}

export async function setProofOfPossession(deviceName, pop) {
    return bridgeCall('EspProvisioning.SetProofOfPossession', { deviceName, pop });
}

export async function scanNetworks(deviceName) {
    return bridgeCall('EspProvisioning.ScanNetworks', { deviceName });
}

export async function provision(deviceName, ssid, passphrase) {
    return bridgeCall('EspProvisioning.Provision', { deviceName, ssid, passphrase });
}

export async function sendData(deviceName, path, data) {
    return bridgeCall('EspProvisioning.SendData', { deviceName, path, data });
}
