package it.codeatlas.flutter.wifi;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

public class WifiPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler {

    private PluginRegistry.Registrar registrar;
    private FlutterPluginBinding pluginBinding;
    private ActivityPluginBinding activityBinding;
    private WifiDelegate delegate;

    public static void registerWith(PluginRegistry.Registrar registrar) {
        final BinaryMessenger messenger = registrar.messenger();
        final Context context = registrar.activeContext().getApplicationContext();
        final Activity activity = registrar.activity();

        WifiPlugin plugin = new WifiPlugin();
        plugin.setRegistrar(registrar);
        plugin.init(messenger, context, activity);
    }

    private void setRegistrar(PluginRegistry.Registrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        checkAndInit();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activityBinding = null;
        delegate.setActivity(null);
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activityBinding = binding;
        delegate.setActivity(activityBinding.getActivity());
    }

    @Override
    public void onDetachedFromActivity() {
        activityBinding = null;
        delegate.setActivity(null);
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        pluginBinding = binding;
        checkAndInit();
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (delegate != null) {
            if (activityBinding != null) {
                activityBinding.removeRequestPermissionsResultListener(delegate);
            }
            binding.getApplicationContext().unregisterReceiver(delegate.networkReceiver);
            delegate.dispose();
        }
    }

    private void checkAndInit() {
        if (delegate == null && activityBinding != null && pluginBinding != null) {
            init(pluginBinding.getBinaryMessenger(), pluginBinding.getApplicationContext(), activityBinding.getActivity());
        }
    }

    private void init(BinaryMessenger messenger, Context context, Activity activity) {
        final MethodChannel channel = new MethodChannel(messenger, "flutter.codeatlas.it/wifi");
        channel.setMethodCallHandler(this);

        @SuppressLint("WifiManagerPotentialLeak")
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        delegate = new WifiDelegate(activity, wifiManager, connectivityManager);

        if (activityBinding != null) {
            activityBinding.addRequestPermissionsResultListener(delegate);
        }
        if (registrar != null) {
            registrar.addRequestPermissionsResultListener(delegate);
        }

        // support Android O,listen network disconnect event
        // https://stackoverflow.com/questions/50462987/android-o-wifimanager-enablenetwork-cannot-work
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(delegate.networkReceiver, filter);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (!delegate.hasActivity()) {
            result.error("no_activity", "wifi plugin requires a foreground activity.", null);
            return;
        }
        switch (call.method) {
            case "ssid":
                delegate.getSSID(call, result);
                break;
            case "level":
                delegate.getLevel(call, result);
                break;
            case "ip":
                delegate.getIP(call, result);
                break;
            case "wifiEnabled":
                delegate.isWifiEnabled(call, result);
                break;
            case "list":
                delegate.getWifiList(call, result);
                break;
            case "connection":
                delegate.connection(call, result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

}
