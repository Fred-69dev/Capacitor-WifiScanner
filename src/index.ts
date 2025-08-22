import { registerPlugin } from '@capacitor/core';

import type { WifiScannerPlugin } from './definitions';

const WifiScanner = registerPlugin<WifiScannerPlugin>('WifiScanner', {
  web: () => import('./web').then((m) => new m.WifiScannerWeb()),
});

export * from './definitions';
export { WifiScanner };
