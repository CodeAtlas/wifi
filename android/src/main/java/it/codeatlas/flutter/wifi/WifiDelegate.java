package it.codeatlas.flutter.wifi;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

public class
WifiDelegate implements PluginRegistry.RequestPermissionsResultListener {
    private Activity activity;
    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private final PermissionManager permissionManager;
    private static final int REQUEST_ACCESS_FINE_LOCATION_PERMISSION = 1;
    private static final int REQUEST_CHANGE_WIFI_STATE_PERMISSION = 2;
    final NetworkChangeReceiver networkReceiver;
    private final NetworkChangeCallback networkCallback =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? new NetworkChangeCallback() : null;

    interface PermissionManager {
        boolean isPermissionGranted(String permissionName);
        void askForPermission(String permissionName, int requestCode);
    }

    public WifiDelegate(final Activity activity, final WifiManager wifiManager, ConnectivityManager connectivityManager) {
        this(activity, wifiManager, connectivityManager, null, null, new PermissionManager() {

            @Override
            public boolean isPermissionGranted(String permissionName) {
                return ActivityCompat.checkSelfPermission(activity, permissionName) == PackageManager.PERMISSION_GRANTED;
            }

            @Override
            public void askForPermission(String permissionName, int requestCode) {
                ActivityCompat.requestPermissions(activity, new String[]{permissionName}, requestCode);
            }
        });
    }

    private MethodChannel.Result result;
    private MethodCall methodCall;

    WifiDelegate(
            Activity activity,
            WifiManager wifiManager,
            ConnectivityManager connectivityManager,
            MethodChannel.Result result,
            MethodCall methodCall,
            PermissionManager permissionManager) {
        this.activity = activity;
        this.wifiManager = wifiManager;
        this.connectivityManager = connectivityManager;
        this.result = result;
        this.methodCall = methodCall;
        this.permissionManager = permissionManager;
        this.networkReceiver = new NetworkChangeReceiver();
    }

    public void dispose() {
        activity = null;
        wifiManager = null;
        connectivityManager = null;
    }

    void setActivity(@Nullable Activity activity) {
        this.activity = activity;
    }

    public boolean hasActivity() {
        return activity != null;
    }

    public void getSSID(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        launchSSID();
    }

    public void getLevel(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        launchLevel();
    }

    private void launchSSID() {
        String wifiName = wifiManager != null ? wifiManager.getConnectionInfo().getSSID().replace("\"", "") : "";
        if (!wifiName.isEmpty()) {
            result.success(wifiName);
            clearMethodCallAndResult();
        } else {
            finishWithError("unavailable", "wifi name not available.");
        }
    }

    private void launchLevel() {
        int level = wifiManager != null ? wifiManager.getConnectionInfo().getRssi() : 0;
        if (level != 0) {
            if (level <= 0 && level >= -55) {
                result.success(3);
            } else if (level < -55 && level >= -80) {
                result.success(2);
            } else if (level < -80 && level >= -100) {
                result.success(1);
            } else {
                result.success(0);
            }
            clearMethodCallAndResult();
        } else {
            finishWithError("unavailable", "wifi level not available.");
        }
    }

    public void getIP(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        launchIP();
    }

    public void isWifiEnabled(MethodCall methodCall, MethodChannel.Result result) {
        WifiManager wifiManager = (WifiManager) activity.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            result.success(wifiManager.isWifiEnabled());
        } else {
            result.error("unavailable", "wifi service not available.", null);
        }
    }

    private void launchIP() {
        NetworkInfo info = ((ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (info != null && info.isConnected()) {
            if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                try {
                    for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                        NetworkInterface intf = en.nextElement();
                        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                result.success(inetAddress.getHostAddress());
                                clearMethodCallAndResult();
                            }
                        }
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                }
            } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ipAddress = intIP2StringIP(wifiInfo.getIpAddress());
                result.success(ipAddress);
                clearMethodCallAndResult();
            }
        } else {
            finishWithError("unavailable", "ip not available.");
        }
    }

    private static String intIP2StringIP(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                (ip >> 24 & 0xFF);
    }

    public void getWifiList(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        if (!permissionManager.isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionManager.askForPermission(Manifest.permission.ACCESS_FINE_LOCATION, REQUEST_ACCESS_FINE_LOCATION_PERMISSION);
            return;
        }
        launchWifiList();
    }

    private void launchWifiList() {
        String key = methodCall.argument("key");
        List<HashMap<String, Object>> list = new ArrayList<>();
        if (wifiManager != null) {
            List<ScanResult> scanResultList = wifiManager.getScanResults();
            for (ScanResult scanResult : scanResultList) {
                int level;
                if (scanResult.level <= 0 && scanResult.level >= -55) {
                    level = 3;
                } else if (scanResult.level < -55 && scanResult.level >= -80) {
                    level = 2;
                } else if (scanResult.level < -80 && scanResult.level >= -100) {
                    level = 1;
                } else {
                    level = 0;
                }
                HashMap<String, Object> maps = new HashMap<>();
                if (key == null || key.isEmpty() || scanResult.SSID.contains(key)) {
                    maps.put("ssid", scanResult.SSID);
                    maps.put("level", level);
                    maps.put("bssid", scanResult.BSSID);
                    list.add(maps);
                }
            }
        }
        result.success(list);
        clearMethodCallAndResult();
    }

    public void connection(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        if (!permissionManager.isPermissionGranted(Manifest.permission.CHANGE_WIFI_STATE)) {
            permissionManager.askForPermission(Manifest.permission.CHANGE_WIFI_STATE, REQUEST_ACCESS_FINE_LOCATION_PERMISSION);
            return;
        }
        connection();
    }

    private void connection() {
        String ssid = methodCall.argument("ssid");
        String password = methodCall.argument("password");

        // support Android 10 Q (API level 29)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            networkCallback.waitNetwork();
            requestNetwork(ssid, password);
            return;
        }

        WifiConfiguration wifiConfig = createWifiConfig(ssid, password);
        if (wifiConfig == null) {
            finishWithError("unavailable", "wifi config is null!");
            return;
        }
        int netId = wifiManager.addNetwork(wifiConfig);
        if (netId == -1) {
            result.success(0);
            clearMethodCallAndResult();
        } else {
            // support Android 8.0 O (API level 26)
            // https://stackoverflow.com/questions/50462987/android-o-wifimanager-enablenetwork-cannot-work
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                wifiManager.enableNetwork(netId, true);
                wifiManager.reconnect();
                result.success(1);
                clearMethodCallAndResult();
            } else {
                networkReceiver.connect(netId);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private void requestNetwork(String ssid, String password) {
        WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder();
        builder.setSsid(ssid);
        if (password != null && !password.isEmpty()) {
            builder.setWpa2Passphrase(password);
        }

        WifiNetworkSpecifier wifiNetworkSpecifier = builder.build();

        NetworkRequest nr = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(wifiNetworkSpecifier)
                .build();

        connectivityManager.requestNetwork(nr, networkCallback);
    }

    private WifiConfiguration createWifiConfig(String ssid, String password) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + ssid + "\"";
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        WifiConfiguration tempConfig = isExist(wifiManager, ssid);
        if (tempConfig != null) {
            wifiManager.removeNetwork(tempConfig.networkId);
        }
        if (password != null && !password.isEmpty()) {
            config.preSharedKey = "\"" + password + "\"";
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        } else {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }
        config.hiddenSSID = true;
        config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.status = WifiConfiguration.Status.ENABLED;
        return config;
    }

    private WifiConfiguration isExist(WifiManager wifiManager, String ssid) {
        List<WifiConfiguration> existingConfigs = wifiManager.getConfiguredNetworks();
        if(existingConfigs != null) {
            for (WifiConfiguration existingConfig : existingConfigs) {
                if (existingConfig.SSID.equals("\"" + ssid + "\"")) {
                    return existingConfig;
                }
            }
        }
        return null;
    }

    private boolean setPendingMethodCallAndResult(MethodCall methodCall, MethodChannel.Result result) {
        if (this.result != null) {
            return false;
        }
        this.methodCall = methodCall;
        this.result = result;
        return true;
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean permissionGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        switch (requestCode) {
            case REQUEST_ACCESS_FINE_LOCATION_PERMISSION:
                if (permissionGranted) {
                    launchWifiList();
                }
                break;
            case REQUEST_CHANGE_WIFI_STATE_PERMISSION:
                if (permissionGranted) {
                    connection();
                }
                break;
            default:
                return false;
        }
        if (!permissionGranted) {
            clearMethodCallAndResult();
        }
        return true;
    }

    private void finishWithAlreadyActiveError() {
        finishWithError("already_active", "wifi is already active");
    }

    private void finishWithError(String errorCode, String errorMessage) {
        result.error(errorCode, errorMessage, null);
        clearMethodCallAndResult();
    }

    private void clearMethodCallAndResult() {
        methodCall = null;
        result = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private class NetworkChangeCallback extends ConnectivityManager.NetworkCallback {
        private boolean waitNetwork = false;

        public void waitNetwork() {
            waitNetwork = true;
        }

        @Override
        public void onAvailable(@NonNull Network network) {
            if (waitNetwork) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    result.success(1);
                    clearMethodCallAndResult();
                });
                waitNetwork = false;
            }
        }
    }

    // support Android O
    // https://stackoverflow.com/questions/50462987/android-o-wifimanager-enablenetwork-cannot-work
    private class NetworkChangeReceiver extends BroadcastReceiver {
        private int netId;
        private boolean willLink = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkInfo netInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if (netInfo != null && willLink) {
                switch (netInfo.getState()) {
                    case DISCONNECTED:
                        wifiManager.enableNetwork(netId, true);
                        wifiManager.reconnect();
                        result.success(1);
                        willLink = false;
                        clearMethodCallAndResult();
                        break;
                    case CONNECTED:
                        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                        if (wifiInfo != null && netId == wifiInfo.getNetworkId()) {
                            result.success(1);
                            willLink = false;
                            clearMethodCallAndResult();
                        }
                        break;
                }
            }
        }

        public void connect(int netId) {
            this.netId = netId;
            willLink = true;
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {
                wifiManager.disconnect();
            } else {
                wifiManager.enableNetwork(netId, true);
                wifiManager.reconnect();
            }
        }
    }
}
