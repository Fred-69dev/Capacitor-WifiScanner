import { WebPlugin } from '@capacitor/core';

import type { WifiScannerPlugin, WifiNetwork } from './definitions';

export class WifiScannerWeb extends WebPlugin implements WifiScannerPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }

  async checkPermissions(): Promise<{ granted: boolean }> {
    // Sur le web, simulez une permission accordée ou utilisez l'API Permissions si nécessaire
    return { granted: true };
  }

  async requestPermissions(): Promise<{ granted: boolean }> {
    // Sur le web, simulez une permission accordée ou utilisez l'API Permissions si nécessaire
    return { granted: true };
  }

  async isWiFiEnabled(): Promise<{ enabled: boolean }> {
    // Sur le web, supposez que le WiFi est activé
    return { enabled: true };
  }

  async scan(): Promise<{ networks: WifiNetwork[] }> {
    // Sur le web, retournez des données simulées ou un tableau vide
    console.warn('WiFi scanning is not available in web environment');
    return { networks: [] };
  }
}
