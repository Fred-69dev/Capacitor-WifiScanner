package com.derf.wifiscanner;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class WifiScanner {
    private static final String TAG = "WifiScanner";
    private WifiManager wifiManager;
    private Context context;

    public String echo(String value) {
        Log.i("Echo", value);
        return value;
    }

    public void initialize(Context context) {
        this.context = context;
        this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public boolean isWiFiEnabled() {
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    public void scanWifiNetworks(final ScanResultsListener listener) {
        if (wifiManager == null) {
            listener.onScanFailure("WifiManager not initialized");
            return;
        }

        // Enregistrer un BroadcastReceiver pour capturer les résultats du scan
        android.content.BroadcastReceiver wifiScanReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, android.content.Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                if (success) {
                    List<ScanResult> results = wifiManager.getScanResults();
                    
                    // Filtrer les résultats
                    List<ScanResult> filteredResults = new ArrayList<>();
                    for (ScanResult result : results) {
                        // Ignorer les réseaux avec SSID vide ou trop faibles
                        if (result.SSID != null && !result.SSID.isEmpty() && result.level > -85) {
                            filteredResults.add(result);
                        }
                    }
                    
                    listener.onScanResults(filteredResults);
                } else {
                    listener.onScanFailure("Scan failed");
                }
                context.unregisterReceiver(this);
            }
        };

        // Enregistrer le receiver et démarrer le scan
        android.content.IntentFilter intentFilter = new android.content.IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(wifiScanReceiver, intentFilter);

        boolean started = wifiManager.startScan();
        if (!started) {
            context.unregisterReceiver(wifiScanReceiver);
            listener.onScanFailure("Failed to start scan");
        }
    }

    public interface ScanResultsListener {
        void onScanResults(List<ScanResult> results);
        void onScanFailure(String reason);
    }
}
