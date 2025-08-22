export interface WifiScannerPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  checkPermissions(): Promise<{ granted: boolean }>;
  requestPermissions(): Promise<{ granted: boolean }>;
  isWiFiEnabled(): Promise<{ enabled: boolean }>;
  scan(): Promise<{ networks: WifiNetwork[] }>;
}

export interface WifiNetwork {
  ssid: string;
  bssid: string;
  signalStrength: number;
}
