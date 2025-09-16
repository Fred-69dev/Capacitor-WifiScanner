package com.derf.wifiscanner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@CapacitorPlugin(
    name = "WifiScanner",
    permissions = {
        @Permission(strings = { Manifest.permission.ACCESS_WIFI_STATE },  alias = "accessWifi"),
        @Permission(strings = { Manifest.permission.CHANGE_WIFI_STATE },  alias = "changeWifi"),
        // Android <= 12 : FINE_LOCATION requis
        @Permission(strings = { Manifest.permission.ACCESS_FINE_LOCATION }, alias = "location"),
        // Android 13+ : NEARBY_WIFI_DEVICES (FINE_LOCATION parfois aussi selon OEM)
        @Permission(strings = { Manifest.permission.NEARBY_WIFI_DEVICES }, alias = "nearby")
    }
)
public class WifiScannerPlugin extends Plugin {

    private static final String TAG = "WifiScannerPlugin";
    private WifiScanner scanner;
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    @Override
    public void load() {
        super.load();
        scanner = new WifiScanner(getActivity()); // ApplicationContext inside
        Log.d(TAG, "WifiScanner loaded");
    }

    /* ---------------- Helpers LOGS & Permissions ---------------- */

    private boolean needsNearbyPermission() {
        return Build.VERSION.SDK_INT >= 33;
    }

    private String asString(PermissionState s) {
        if (s == PermissionState.GRANTED) return "granted";
        if (s == PermissionState.DENIED)  return "denied";
        return "prompt";
    }

    /** Logge les états de TOUTES les permissions gérées par le plugin. */
    private void logAllPermStates(String context) {
        PermissionState pAccess   = getPermissionState("accessWifi");
        PermissionState pChange   = getPermissionState("changeWifi");
        PermissionState pLoc      = getPermissionState("location");
        PermissionState pNearby   = getPermissionState("nearby");
        boolean api33plus         = needsNearbyPermission();

        Log.d(TAG, "=== PERMS [" + context + "] ===");
        Log.d(TAG, "SDK>=33? " + api33plus);
        Log.d(TAG, "accessWifi: " + asString(pAccess));
        Log.d(TAG, "changeWifi: " + asString(pChange));
        Log.d(TAG, "location  : " + asString(pLoc) + " (ACCESS_FINE_LOCATION)");
        Log.d(TAG, "nearby    : " + (api33plus ? asString(pNearby) : "n/a (API<33)"));
        Log.d(TAG, "===============================");
    }

    /** Logge le résultat de checkSelfPermission (niveau Android) pour chaque permission. */
    private void logSelfPermissionChecks(String context) {
        boolean hasAccess   = getActivity().checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
        boolean hasChange   = getActivity().checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
        boolean hasFine     = getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasNearby   = (!needsNearbyPermission()) || (getActivity().checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED);

        Log.d(TAG, "=== ANDROID CHECK [" + context + "] ===");
        Log.d(TAG, "checkSelfPermission ACCESS_WIFI_STATE      : " + hasAccess);
        Log.d(TAG, "checkSelfPermission CHANGE_WIFI_STATE      : " + hasChange);
        Log.d(TAG, "checkSelfPermission ACCESS_FINE_LOCATION   : " + hasFine);
        Log.d(TAG, "checkSelfPermission NEARBY_WIFI_DEVICES    : " + (needsNearbyPermission() ? hasNearby : false) + (needsNearbyPermission() ? "" : " (API<33)"));
        Log.d(TAG, "=========================================");
    }

    private boolean hasAllRequiredWifiPermissions() {
        boolean ok =
            getActivity().checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED &&
            getActivity().checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;

        if (needsNearbyPermission()) {
            ok = ok && (getActivity().checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED);
        } else {
            ok = ok && (getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        }
        return ok;
    }

    private boolean isLocationEnabled() {
        try {
            Context ctx = getContext();
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            boolean enabled = lm != null && (
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            );
            Log.d(TAG, "Location toggle (system) ON? " + enabled);
            return enabled;
        } catch (Throwable t) {
            Log.w(TAG, "isLocationEnabled(): " + t.getMessage());
            // En cas d’erreur, on ne bloque pas
            return true;
        }
    }

    /* ---------------- Plugin API: Permissions ---------------- */

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        logAllPermStates("checkPermissions/before");
        logSelfPermissionChecks("checkPermissions/before");

        JSObject res = new JSObject();
        res.put("granted", hasAllRequiredWifiPermissions());
        res.put("accessWifi",  asString(getPermissionState("accessWifi")));
        res.put("changeWifi",  asString(getPermissionState("changeWifi")));
        res.put("location",    asString(getPermissionState("location")));
        res.put("nearby",      needsNearbyPermission() ? asString(getPermissionState("nearby")) : "n/a");

        Log.d(TAG, "checkPermissions → " + res.toString());
        call.resolve(res);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        logAllPermStates("requestPermissions/before");
        logSelfPermissionChecks("requestPermissions/before");

        if (hasAllRequiredWifiPermissions()) {
            JSObject res = new JSObject();
            res.put("granted", true);
            res.put("accessWifi",  asString(getPermissionState("accessWifi")));
            res.put("changeWifi",  asString(getPermissionState("changeWifi")));
            res.put("location",    asString(getPermissionState("location")));
            res.put("nearby",      needsNearbyPermission() ? asString(getPermissionState("nearby")) : "n/a");
            Log.d(TAG, "requestPermissions (already granted) → " + res.toString());
            call.resolve(res);
            return;
        }
        requestAllPermissions(call, "permissionsCallback");
    }

    @PermissionCallback
    private void permissionsCallback(PluginCall call) {
        logAllPermStates("permissionsCallback/after");
        logSelfPermissionChecks("permissionsCallback/after");

        JSObject res = new JSObject();
        res.put("granted", hasAllRequiredWifiPermissions());
        res.put("accessWifi",  asString(getPermissionState("accessWifi")));
        res.put("changeWifi",  asString(getPermissionState("changeWifi")));
        res.put("location",    asString(getPermissionState("location")));
        res.put("nearby",      needsNearbyPermission() ? asString(getPermissionState("nearby")) : "n/a");

        Log.d(TAG, "permissionsCallback → " + res.toString());
        call.resolve(res);
    }

    /* ---------------- Utils ---------------- */

    private int freqToChannel(int freqMhz) {
        if (freqMhz >= 2412 && freqMhz <= 2472) return (freqMhz - 2407) / 5; // 2.4 GHz
        if (freqMhz == 2484) return 14;
        if (freqMhz >= 5000 && freqMhz <= 5900) return (freqMhz - 5000) / 5; // 5 GHz
        if (freqMhz >= 5955 && freqMhz <= 7115) return (freqMhz - 5955) / 5 + 1; // 6 GHz approx
        return -1;
    }

    /* ---------------- Public API ---------------- */

    @PluginMethod
    public void isWifiEnabled(PluginCall call) {
        boolean enabled = scanner.isWifiEnabled();
        Log.d(TAG, "isWifiEnabled → " + enabled);
        JSObject ret = new JSObject();
        ret.put("enabled", enabled);
        call.resolve(ret);
    }

    @PluginMethod
    public void getLastScanResults(PluginCall call) {
        try {
            List<ScanResult> results = scanner.getLastScanResults();
            if (results == null) {
                Log.w(TAG, "getLastScanResults → null");
                call.reject("No scan results available");
                return;
            }
            JSArray arr = new JSArray();
            Set<String> seen = new HashSet<>();

            for (ScanResult r : results) {
                if (r.BSSID == null || !seen.add(r.BSSID)) continue;
                JSObject o = new JSObject();
                o.put("ssid",  r.SSID  != null ? r.SSID  : "");
                o.put("bssid", r.BSSID != null ? r.BSSID : "");
                o.put("signalStrength", r.level);
                o.put("frequency", r.frequency);
                o.put("channel", freqToChannel(r.frequency));
                arr.put(o);
            }
            JSObject ret = new JSObject();
            ret.put("networks", arr);
            ret.put("count", arr.length());
            Log.d(TAG, "getLastScanResults → count=" + arr.length());
            call.resolve(ret);
        } catch (Throwable t) {
            Log.e(TAG, "getLastScanResults error: " + t.getMessage(), t);
            call.reject("Error getting last scan results: " + t.getMessage());
        }
    }

    @PluginMethod
    public void scan(PluginCall call) {
        // Log complet AVANT chaque garde
        logAllPermStates("scan/before");
        logSelfPermissionChecks("scan/before");

        boolean hasAccess = getActivity().checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
        boolean hasChange = getActivity().checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
        boolean hasFine   = getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasNear   = !needsNearbyPermission() || (getActivity().checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED);

        Log.d(TAG, "scan guards → hasAccess=" + hasAccess + " hasChange=" + hasChange + " hasFine=" + hasFine + " hasNearby=" + hasNear);

        if (!hasAllRequiredWifiPermissions()) {
            Log.w(TAG, "scan reject → Missing required permissions for Wi-Fi scanning");
            call.reject("Missing required permissions for Wi-Fi scanning");
            return;
        }
        if (!scanner.isWifiEnabled()) {
            Log.w(TAG, "scan reject → Wi-Fi is disabled");
            call.reject("Wi-Fi is disabled");
            return;
        }
        if (!isLocationEnabled()) {
            Log.w(TAG, "scan reject → Device location must be turned ON to scan Wi-Fi");
            call.reject("Device location must be turned ON to scan Wi-Fi");
            return;
        }
        if (!inFlight.compareAndSet(false, true)) {
            Log.w(TAG, "scan reject → A scan is already in progress");
            call.reject("A scan is already in progress");
            return;
        }

        final AtomicBoolean settled = new AtomicBoolean(false);
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable timeout = () -> {
            if (settled.compareAndSet(false, true)) {
                scanner.cleanup();
                inFlight.set(false);
                Log.w(TAG, "scan timeout (7s watchdog)");
                call.reject("Wi-Fi scan timeout");
            }
        };

        // Watchdog 7s
        handler.postDelayed(timeout, 7000);

        // Exécuter le startScan en main thread (par sécurité)
        getBridge().executeOnMainThread(() ->
            scanner.startScan(new WifiScanner.ScanResultListener() {
                @Override
                public void onScanResults(List<ScanResult> results) {
                    if (!settled.compareAndSet(false, true)) return;
                    handler.removeCallbacks(timeout);
                    try {
                        JSArray arr = new JSArray();
                        Set<String> seen = new HashSet<>();
                        for (ScanResult r : results) {
                            if (r.BSSID == null || !seen.add(r.BSSID)) continue;
                            JSObject o = new JSObject();
                            o.put("ssid",  r.SSID  != null ? r.SSID  : "");
                            o.put("bssid", r.BSSID != null ? r.BSSID : "");
                            o.put("signalStrength", r.level);
                            o.put("frequency", r.frequency);
                            o.put("channel", freqToChannel(r.frequency));
                            o.put("timestamp", System.currentTimeMillis());
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                o.put("centerFreq0", r.centerFreq0);
                                o.put("centerFreq1", r.centerFreq1);
                                o.put("channelWidth", r.channelWidth);
                            }
                            arr.put(o);
                        }
                        JSObject ret = new JSObject();
                        ret.put("networks", arr);
                        ret.put("count", arr.length());
                        ret.put("timestamp", System.currentTimeMillis());
                        Log.d(TAG, "scan success → count=" + arr.length());
                        call.resolve(ret);
                    } catch (Throwable t) {
                        Log.e(TAG, "scan onScanResults error: " + t.getMessage(), t);
                        call.reject("Error processing scan results: " + t.getMessage());
                    } finally {
                        scanner.cleanup();
                        inFlight.set(false);
                    }
                }

                @Override
                public void onScanError(String error) {
                    if (!settled.compareAndSet(false, true)) return;
                    handler.removeCallbacks(timeout);
                    try {
                        Log.e(TAG, "scan onScanError: " + error);
                        call.reject("Scan failed: " + error);
                    } finally {
                        scanner.cleanup();
                        inFlight.set(false);
                    }
                }
            })
        );
    }

    /* ---------------- Lifecycle : nettoyage auto ---------------- */

    @Override
    protected void handleOnPause() {
        super.handleOnPause();
        if (scanner != null) scanner.cleanup();
    }

    @Override
    protected void handleOnStop() {
        super.handleOnStop();
        if (scanner != null) scanner.cleanup();
    }

    @Override
    protected void handleOnDestroy() {
        if (scanner != null) scanner.cleanup();
        super.handleOnDestroy();
    }
}
