package com.derf.wifiscanner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PermissionState;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.List;

@CapacitorPlugin(
    name = "WifiScanner",
    permissions = {
        @Permission(strings = {Manifest.permission.ACCESS_FINE_LOCATION}, alias = "location"),
        @Permission(strings = {Manifest.permission.ACCESS_WIFI_STATE}, alias = "accessWifi"),
        @Permission(strings = {Manifest.permission.CHANGE_WIFI_STATE}, alias = "changeWifi")
    }
)
public class WifiScannerPlugin extends Plugin {

    private static final String TAG = "WifiScannerPlugin";
    private WifiScanner implementation;

    @Override
    public void load() {
        implementation = new WifiScanner();
        implementation.initialize(getContext());
    }

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        JSObject permissionsResult = new JSObject();
        boolean locationGranted = hasRequiredPermissions();
        permissionsResult.put("granted", locationGranted);
        call.resolve(permissionsResult);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (hasRequiredPermissions()) {
            JSObject permissionsResult = new JSObject();
            permissionsResult.put("granted", true);
            call.resolve(permissionsResult);
            return;
        }
        
        requestPermissionForAlias("location", call, "permissionsCallback");
    }

    @PermissionCallback
    private void permissionsCallback(PluginCall call) {
        JSObject permissionsResult = new JSObject();
        permissionsResult.put("granted", hasRequiredPermissions());
        call.resolve(permissionsResult);
    }

    @PluginMethod
    public void isWiFiEnabled(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("enabled", implementation.isWiFiEnabled());
        call.resolve(ret);
    }

    @PluginMethod
    public void scan(PluginCall call) {
        if (!hasRequiredPermissions()) {
            call.reject("Location permission is required for WiFi scanning");
            return;
        }

        if (!implementation.isWiFiEnabled()) {
            call.reject("WiFi is not enabled");
            return;
        }

        implementation.scanWifiNetworks(new WifiScanner.ScanResultsListener() {
            @Override
            public void onScanResults(List<ScanResult> results) {
                try {
                    JSObject ret = new JSObject();
                    JSArray networks = new JSArray();

                    for (ScanResult result : results) {
                        JSObject network = new JSObject();
                        network.put("ssid", result.SSID);
                        network.put("bssid", result.BSSID);
                        network.put("signalStrength", result.level);
                        networks.put(network);
                    }

                    ret.put("networks", networks);
                    call.resolve(ret);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing scan results", e);
                    call.reject("Error processing scan results: " + e.getMessage());
                }
            }

            @Override
            public void onScanFailure(String reason) {
                call.reject("WiFi scan failed: " + reason);
            }
        });
    }

    public boolean hasRequiredPermissions() {
        return getPermissionState("location") == PermissionState.GRANTED &&
               getPermissionState("accessWifi") == PermissionState.GRANTED &&
               getPermissionState("changeWifi") == PermissionState.GRANTED;
    }
}
