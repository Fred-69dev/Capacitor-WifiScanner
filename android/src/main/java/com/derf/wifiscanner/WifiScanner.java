package com.derf.wifiscanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class WifiScanner {

    private static final String TAG = "WifiScanner";
    // (Optionnel) anti-rafale si tu enchaînes des scans rapides
    private static final long MIN_SCAN_INTERVAL_MS = 15_000;
    private long lastScanAt = 0L;

    private final Context appContext;
    private final WifiManager wifiManager;

    private BroadcastReceiver scanReceiver;
    private final AtomicBoolean receiverRegistered = new AtomicBoolean(false);
    private ScanResultListener callback;

    public interface ScanResultListener {
        void onScanResults(List<ScanResult> results);
        void onScanError(String error);
    }

    public WifiScanner(Context context) {
        this.appContext = context.getApplicationContext();
        this.wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
        initReceiver();
    }

    private void initReceiver() {
        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                try {
                    if (intent == null) return;
                    if (!WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) return;

                    boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                    if (success) {
                        List<ScanResult> results = safeGetScanResults();
                        if (callback != null) callback.onScanResults(results);
                    } else {
                        if (callback != null) callback.onScanError("Scan failed");
                    }
                } catch (SecurityException se) {
                    Log.e(TAG, "Permission error while reading results", se);
                    if (callback != null) callback.onScanError("Permission denied: " + se.getMessage());
                } catch (Throwable t) {
                    Log.e(TAG, "Unexpected error while reading results", t);
                    if (callback != null) callback.onScanError("Unexpected error: " + t.getMessage());
                } finally {
                    // One-shot : on se désinscrit après livraison
                    unregisterReceiverSafely();
                }
            }
        };
    }

    public void startScan(ScanResultListener listener) {
        this.callback = listener;

        if (wifiManager == null) {
            if (callback != null) callback.onScanError("WifiManager not available");
            return;
        }
        if (!isWifiEnabled()) {
            if (callback != null) callback.onScanError("WiFi is disabled");
            return;
        }
        if (!canStartNewScan()) {
            if (callback != null) callback.onScanError("Scan throttled");
            return;
        }

        // Nettoyage préventif
        unregisterReceiverSafely();

        // Inscription du receiver (compat Android 13+)
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        int flags = ContextCompat.RECEIVER_NOT_EXPORTED;
        ContextCompat.registerReceiver(appContext, scanReceiver, filter, flags);
        receiverRegistered.set(true);

        boolean started = false;
        try {
            started = wifiManager.startScan();
        } catch (Throwable t) {
            Log.e(TAG, "startScan() threw", t);
        }

        if (!started) {
            unregisterReceiverSafely();
            if (callback != null) callback.onScanError("Failed to start scan");
            return;
        }

        markScanStartedNow();
    }

    public boolean isWifiEnabled() {
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    public List<ScanResult> getLastScanResults() {
        try {
            return safeGetScanResults();
        } catch (Throwable t) {
            Log.e(TAG, "getLastScanResults() failed", t);
            return null;
        }
    }

    public void cleanup() {
        callback = null;
        unregisterReceiverSafely();
    }

    // ---------- internes ----------

    private List<ScanResult> safeGetScanResults() {
        List<ScanResult> results = wifiManager.getScanResults();
        return results != null ? results : new ArrayList<>();
    }

    private void unregisterReceiverSafely() {
        if (receiverRegistered.compareAndSet(true, false)) {
            try {
                appContext.unregisterReceiver(scanReceiver);
            } catch (IllegalArgumentException ignored) {
                // Déjà désinscrit (course), on ignore
            } catch (Throwable t) {
                Log.w(TAG, "unregisterReceiver failed", t);
            }
        }
    }

    private boolean canStartNewScan() {
        return System.currentTimeMillis() - lastScanAt >= MIN_SCAN_INTERVAL_MS;
    }

    private void markScanStartedNow() {
        lastScanAt = System.currentTimeMillis();
    }
}
