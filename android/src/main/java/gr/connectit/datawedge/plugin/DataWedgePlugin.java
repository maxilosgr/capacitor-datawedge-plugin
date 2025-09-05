package gr.connectit.datawedge.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import gr.connectit.datawedge.plugin.DataWedgeVersion.DataWedgeFeature;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

@CapacitorPlugin(name = "DataWedge")
public class DataWedgePlugin extends Plugin {

    private static final String TAG = "DataWedgePlugin";
    private static final String DATAWEDGE_PACKAGE = "com.symbol.datawedge";
    private static final String[] DATAWEDGE_PACKAGES = {
        "com.symbol.datawedge",
        "com.motorolasolutions.datawedge",
        "com.zebra.datawedge"
    };
    private static final String SCAN_INTENT_ACTION = "gr.connectit.datawedge.SCAN";
    
    private BroadcastReceiver scanReceiver;
    private BroadcastReceiver versionReceiver;
    private BroadcastReceiver resultReceiver;
    private BroadcastReceiver notificationReceiver;
    private boolean isListenerRegistered = false;
    private boolean isNotificationListenerRegistered = false;
    private DataWedgeVersion detectedVersion = null;
    private boolean versionDetectionAttempted = false;
    
    // Store pending calls for async responses
    private PluginCall pendingEnumerateCall;
    private PluginCall pendingProfileCall;
    private PluginCall pendingProfilesListCall;
    private PluginCall pendingScannerStatusCall;
    private PluginCall pendingAssociatedAppsCall;
    private PluginCall pendingConfigCall;
    private PluginCall pendingDisabledAppListCall;
    private PluginCall pendingIgnoreDisabledProfilesCall;
    private PluginCall pendingTriggersCall;
    private PluginCall pendingSoftScanCall;
    private PluginCall pendingSoftRfidCall;

    @Override
    public void load() {
        super.load();
        Log.d(TAG, "DataWedge plugin loaded");
        
        // Register result receiver
        registerResultReceiver();
        
        // Detect DataWedge version on load
        if (isDataWedgeInstalled()) {
            detectDataWedgeVersion();
        }
    }

    @PluginMethod
    public void isDataWedgeAvailable(PluginCall call) {
        Log.d(TAG, "🔍 Checking DataWedge availability...");
        boolean isAvailable = isDataWedgeInstalled();
        Log.d(TAG, "📊 DataWedge availability result: " + isAvailable);
        
        JSObject result = new JSObject();
        result.put("available", isAvailable);
        call.resolve(result);
    }

    @PluginMethod
    public void sendIntent(PluginCall call) {
        String action = call.getString("action");
        JSObject extrasObject = call.getObject("extras");

        Log.d(TAG, "📡 DataWedge intent request received");
        Log.d(TAG, "🎯 Action: " + action);
        Log.d(TAG, "📦 Extras: " + (extrasObject != null ? extrasObject.toString() : "null"));

        if (action == null) {
            Log.e(TAG, "❌ Action is required but was null");
            call.reject("Action is required");
            return;
        }

        try {
            Intent dwIntent = new Intent();
            dwIntent.setAction(action);
            dwIntent.setPackage("com.symbol.datawedge");

            Log.d(TAG, "📤 Created intent with action: " + action);
            Log.d(TAG, "📦 Target package: com.symbol.datawedge");

            // Convert JSObject extras to Bundle
            if (extrasObject != null) {
                Bundle extras = convertJSObjectToBundle(extrasObject);
                dwIntent.putExtras(extras);
                Log.d(TAG, "✅ Extras converted and added to intent");
                
                // Log bundle contents for debugging
                for (String key : extras.keySet()) {
                    Object value = extras.get(key);
                    Log.d(TAG, "📋 Bundle key: " + key + " = " + value);
                }
            } else {
                Log.d(TAG, "ℹ️ No extras to add");
            }

            // Send the intent
            Log.d(TAG, "🚀 Sending broadcast intent...");
            getContext().sendBroadcast(dwIntent);
            Log.d(TAG, "✅ DataWedge intent sent successfully: " + action);
            call.resolve();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending DataWedge intent: " + action, e);
            Log.e(TAG, "📊 Error details: " + e.getMessage());
            call.reject("Error sending intent: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getDiagnosticInfo(PluginCall call) {
        JSObject result = new JSObject();
        PackageManager pm = getContext().getPackageManager();
        
        // Check each package individually
        JSObject packageCheck = new JSObject();
        for (String packageName : DATAWEDGE_PACKAGES) {
            try {
                pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
                packageCheck.put(packageName, true);
            } catch (PackageManager.NameNotFoundException e) {
                packageCheck.put(packageName, false);
            }
        }
        result.put("packageCheck", packageCheck);
        
        // Check for DataWedge API receivers using official action
        try {
            Intent dwIntent = new Intent();
            dwIntent.setAction("com.symbol.datawedge.api.ACTION");
            dwIntent.setPackage("com.symbol.datawedge");
            int receivers = pm.queryBroadcastReceivers(dwIntent, 0).size();
            result.put("officialApiReceiversFound", receivers);
        } catch (Exception e) {
            result.put("officialApiReceiversError", e.getMessage());
        }
        
        // Also check for version info receivers
        try {
            Intent versionIntent = new Intent();
            versionIntent.setAction("com.symbol.datawedge.api.GET_VERSION_INFO");
            int versionReceivers = pm.queryBroadcastReceivers(versionIntent, 0).size();
            result.put("versionApiReceiversFound", versionReceivers);
        } catch (Exception e) {
            result.put("versionApiReceiversError", e.getMessage());
        }
        
        result.put("isAvailable", isDataWedgeInstalled());
        call.resolve(result);
    }
    
    @PluginMethod
    public void queryDataWedgeStatus(PluginCall call) {
        if (!isDataWedgeInstalled()) {
            JSObject result = new JSObject();
            result.put("available", false);
            result.put("error", "DataWedge not found on device");
            call.resolve(result);
            return;
        }
        
        try {
            // Use the official DataWedge API to get version info (2024 approach)
            Intent i = new Intent();
            i.setAction("com.symbol.datawedge.api.ACTION");
            i.setPackage("com.symbol.datawedge");
            i.putExtra("com.symbol.datawedge.api.GET_VERSION_INFO", "");
            
            // Send the intent
            getContext().sendBroadcast(i);
            
            // For now, just return that query was sent
            // In a real implementation, you'd set up a receiver for the response
            JSObject result = new JSObject();
            result.put("available", true);
            result.put("queryStatus", "version_info_requested");
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error querying DataWedge status", e);
            JSObject result = new JSObject();
            result.put("available", false);
            result.put("error", "Failed to query DataWedge: " + e.getMessage());
            call.resolve(result);
        }
    }

    @PluginMethod
    public void getLastScanIntent(PluginCall call) {
        // This would typically be implemented to check for scan data
        // from the last received intent, but for simplicity we'll just
        // return empty for now
        JSObject result = new JSObject();
        call.resolve(result);
    }

    @PluginMethod
    public void registerScanListener(PluginCall call) {
        if (!isListenerRegistered) {
            registerScanBroadcastReceiver();
            isListenerRegistered = true;
        }
        call.resolve();
    }

    private boolean isDataWedgeInstalled() {
        PackageManager pm = getContext().getPackageManager();
        
        // First, try the official DataWedge API approach (2024 method)
        try {
            Intent dwIntent = new Intent();
            dwIntent.setAction("com.symbol.datawedge.api.ACTION");
            dwIntent.setPackage("com.symbol.datawedge");
            
            // Check if DataWedge can receive this intent
            if (pm.queryBroadcastReceivers(dwIntent, 0).size() > 0) {
                Log.d(TAG, "DataWedge API receivers found via official intent query");
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "Error querying official DataWedge API: " + e.getMessage());
        }
        
        // Fallback: Check package manager for each possible DataWedge package name
        for (String packageName : DATAWEDGE_PACKAGES) {
            try {
                pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
                Log.d(TAG, "DataWedge found with package name: " + packageName);
                
                // Double-check that this package can handle DataWedge APIs
                Intent testIntent = new Intent();
                testIntent.setAction("com.symbol.datawedge.api.ACTION");
                testIntent.setPackage(packageName);
                
                if (pm.queryBroadcastReceivers(testIntent, 0).size() > 0) {
                    Log.d(TAG, "Confirmed: " + packageName + " supports DataWedge APIs");
                    return true;
                } else {
                    Log.d(TAG, "Package found but doesn't support DataWedge APIs: " + packageName);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.d(TAG, "DataWedge package not found: " + packageName);
            }
        }
        
        // Final attempt: Check for any intent receivers that handle DataWedge scan intents
        try {
            Intent scanIntent = new Intent();
            scanIntent.setAction("com.symbol.datawedge.api.GET_VERSION_INFO");
            
            if (pm.queryBroadcastReceivers(scanIntent, 0).size() > 0) {
                Log.d(TAG, "DataWedge version query receivers found");
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "Error querying DataWedge version receivers: " + e.getMessage());
        }
        
        Log.w(TAG, "DataWedge not found on this device");
        return false;
    }

    private void registerScanBroadcastReceiver() {
        if (scanReceiver != null) {
            return; // Already registered
        }

        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "Received broadcast: " + action);

                if (SCAN_INTENT_ACTION.equals(action)) {
                    handleScanIntent(intent);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(SCAN_INTENT_ACTION);
        filter.addCategory(Intent.CATEGORY_DEFAULT);

        getContext().registerReceiver(scanReceiver, filter);
        Log.d(TAG, "Scan broadcast receiver registered");
    }

    private void handleScanIntent(Intent intent) {
        try {
            String scanData = intent.getStringExtra("com.symbol.datawedge.data_string");
            String labelType = intent.getStringExtra("com.symbol.datawedge.label_type");
            
            if (scanData != null) {
                JSObject scanResult = new JSObject();
                scanResult.put("data", scanData);
                scanResult.put("labelType", labelType != null ? labelType : "UNKNOWN");
                scanResult.put("timestamp", System.currentTimeMillis());

                Log.d(TAG, "Scan received: " + scanData + " (" + labelType + ")");
                notifyListeners("scanReceived", scanResult);
            } else {
                Log.w(TAG, "Received scan intent but no data found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling scan intent", e);
        }
    }

    private Bundle convertJSObjectToBundle(JSObject jsObject) {
        Bundle bundle = new Bundle();
        
        try {
            Iterator<String> keys = jsObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = jsObject.opt(key);
                
                if (value instanceof String) {
                    bundle.putString(key, (String) value);
                } else if (value instanceof Boolean) {
                    bundle.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    bundle.putInt(key, (Integer) value);
                } else if (value instanceof Double) {
                    bundle.putDouble(key, (Double) value);
                } else if (value instanceof JSONObject) {
                    // Handle nested objects recursively
                    Bundle nestedBundle = convertJSObjectToBundle(JSObject.fromJSONObject((JSONObject) value));
                    bundle.putBundle(key, nestedBundle);
                } else {
                    // Convert everything else to string
                    bundle.putString(key, String.valueOf(value));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error converting JSObject to Bundle", e);
        }
        
        return bundle;
    }

    @PluginMethod
    public void enumerateScanners(PluginCall call) {
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 5)) {
            call.reject("enumerateScanners requires DataWedge 6.5 or higher");
            return;
        }
        
        // Store pending call
        pendingEnumerateCall = call;
        
        // Send enumerate scanners intent
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.ENUMERATE_SCANNERS", "");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Requested scanner enumeration");
    }

    @PluginMethod
    public void getActiveProfile(PluginCall call) {
        // Store pending call
        pendingProfileCall = call;
        
        // Send get active profile intent
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.GET_ACTIVE_PROFILE", "");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Requested active profile");
    }

    @PluginMethod
    public void getProfilesList(PluginCall call) {
        // Store pending call
        pendingProfilesListCall = call;
        
        // Send get profiles list intent
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.GET_PROFILES_LIST", "");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Requested profiles list");
    }

    @PluginMethod
    public void getScannerStatus(PluginCall call) {
        // Store pending call
        pendingScannerStatusCall = call;
        
        // Send get scanner status intent
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.GET_SCANNER_STATUS", "");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Requested scanner status");
    }

    @PluginMethod
    public void getAssociatedApps(PluginCall call) {
        String profileName = call.getString("profileName");
        if (profileName == null) {
            call.reject("Profile name is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 5)) {
            call.reject("getAssociatedApps requires DataWedge 6.5 or higher");
            return;
        }
        
        // Store pending call
        pendingAssociatedAppsCall = call;
        
        // Create the intent
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        
        Bundle bConfig = new Bundle();
        bConfig.putString("PROFILE_NAME", profileName);
        bConfig.putStringArray("APP_LIST", new String[]{});
        
        i.putExtra("com.symbol.datawedge.api.GET_CONFIG", bConfig);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Requested associated apps for profile: " + profileName);
    }

    @PluginMethod
    public void getConfig(PluginCall call) {
        String profileName = call.getString("profileName");
        if (profileName == null) {
            call.reject("Profile name is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 5)) {
            call.reject("getConfig requires DataWedge 6.5 or higher");
            return;
        }
        
        // Store pending call
        pendingConfigCall = call;
        
        // Create the intent
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        
        Bundle bConfig = new Bundle();
        bConfig.putString("PROFILE_NAME", profileName);
        
        // Get config type if specified
        String configType = call.getString("configType");
        if (configType != null) {
            bConfig.putString("CONFIG_MODE", configType);
        }
        
        i.putExtra("com.symbol.datawedge.api.GET_CONFIG", bConfig);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Requested config for profile: " + profileName);
    }

    @PluginMethod
    public void getDisabledAppList(PluginCall call) {
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 9)) {
            call.reject("getDisabledAppList requires DataWedge 6.9 or higher");
            return;
        }
        
        // Store pending call
        pendingDisabledAppListCall = call;
        
        // Send get disabled app list intent
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.GET_DISABLED_APP_LIST", "");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Requested disabled app list");
    }

    @PluginMethod
    public void getIgnoreDisabledProfiles(PluginCall call) {
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(7, 1)) {
            call.reject("getIgnoreDisabledProfiles requires DataWedge 7.1 or higher");
            return;
        }
        
        // Store pending call
        pendingIgnoreDisabledProfilesCall = call;
        
        // Send get ignore disabled profiles intent
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.GET_IGNORE_DISABLED_PROFILES", "");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Requested ignore disabled profiles status");
    }

    @PluginMethod
    public void getDatawedgeStatus(PluginCall call) {
        // Send get DataWedge status intent and wait for response
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.GET_DATAWEDGE_STATUS", "");
        
        // Register one-time receiver for this specific response
        BroadcastReceiver statusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("com.symbol.datawedge.api.RESULT_ACTION".equals(action)) {
                    Bundle bundle = intent.getExtras();
                    if (bundle != null && bundle.containsKey("com.symbol.datawedge.api.RESULT_GET_DATAWEDGE_STATUS")) {
                        String status = bundle.getString("com.symbol.datawedge.api.RESULT_GET_DATAWEDGE_STATUS");
                        
                        JSObject result = new JSObject();
                        result.put("isEnabled", "ENABLED".equals(status));
                        call.resolve(result);
                        
                        // Unregister this receiver
                        getContext().unregisterReceiver(this);
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.symbol.datawedge.api.RESULT_ACTION");
        getContext().registerReceiver(statusReceiver, filter);
        
        getContext().sendBroadcast(i);
        Log.d(TAG, "Requested DataWedge status");
    }

    // ==================== Configuration Management Methods ====================
    
    @PluginMethod
    public void cloneProfile(PluginCall call) {
        String sourceProfileName = call.getString("sourceProfileName");
        String destinationProfileName = call.getString("destinationProfileName");
        
        if (sourceProfileName == null || destinationProfileName == null) {
            call.reject("Both source and destination profile names are required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 5)) {
            call.reject("cloneProfile requires DataWedge 6.5 or higher");
            return;
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.CLONE_PROFILE", sourceProfileName);
        i.putExtra("com.symbol.datawedge.api.DESTINATION_PROFILE_NAME", destinationProfileName);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Cloning profile: " + sourceProfileName + " to " + destinationProfileName);
        call.resolve();
    }
    
    @PluginMethod
    public void createProfile(PluginCall call) {
        String profileName = call.getString("profileName");
        if (profileName == null) {
            call.reject("Profile name is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 4)) {
            call.reject("createProfile requires DataWedge 6.4 or higher");
            return;
        }
        
        Boolean profileEnabled = call.getBoolean("profileEnabled", true);
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.CREATE_PROFILE", profileName);
        
        // If we want to set enabled state, we need to use SET_CONFIG after creation
        if (!profileEnabled) {
            // Send a follow-up SET_CONFIG to disable the profile
            Bundle profileConfig = new Bundle();
            profileConfig.putString("PROFILE_NAME", profileName);
            profileConfig.putString("PROFILE_ENABLED", "false");
            profileConfig.putString("CONFIG_MODE", "UPDATE");
            
            Intent configIntent = new Intent();
            configIntent.setAction("com.symbol.datawedge.api.ACTION");
            configIntent.putExtra("com.symbol.datawedge.api.SET_CONFIG", profileConfig);
            getContext().sendBroadcast(configIntent);
        }
        
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Created profile: " + profileName);
        call.resolve();
    }
    
    @PluginMethod
    public void deleteProfile(PluginCall call) {
        String profileName = call.getString("profileName");
        if (profileName == null) {
            call.reject("Profile name is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 6)) {
            call.reject("deleteProfile requires DataWedge 6.6 or higher");
            return;
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.DELETE_PROFILE", profileName);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Deleted profile: " + profileName);
        call.resolve();
    }
    
    @PluginMethod
    public void importConfig(PluginCall call) {
        String configFile = call.getString("configFile");
        if (configFile == null) {
            call.reject("Config file path is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 7)) {
            call.reject("importConfig requires DataWedge 6.7 or higher");
            return;
        }
        
        String importMode = call.getString("importMode", "OVERWRITE");
        
        Bundle bConfig = new Bundle();
        bConfig.putString("FOLDER_PATH", configFile);
        bConfig.putString("CONFIG_MODE", importMode);
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.IMPORT_CONFIG", bConfig);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Importing config from: " + configFile);
        call.resolve();
    }
    
    @PluginMethod
    public void renameProfile(PluginCall call) {
        String currentProfileName = call.getString("currentProfileName");
        String newProfileName = call.getString("newProfileName");
        
        if (currentProfileName == null || newProfileName == null) {
            call.reject("Both current and new profile names are required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 6)) {
            call.reject("renameProfile requires DataWedge 6.6 or higher");
            return;
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.RENAME_PROFILE", currentProfileName);
        i.putExtra("com.symbol.datawedge.api.DESTINATION_PROFILE_NAME", newProfileName);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Renaming profile: " + currentProfileName + " to " + newProfileName);
        call.resolve();
    }
    
    @PluginMethod
    public void restoreConfig(PluginCall call) {
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 7)) {
            call.reject("restoreConfig requires DataWedge 6.7 or higher");
            return;
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.RESTORE_CONFIG", "");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Restoring DataWedge to factory defaults");
        call.resolve();
    }
    
    @PluginMethod
    public void setConfig(PluginCall call) {
        String profileName = call.getString("profileName");
        if (profileName == null) {
            call.reject("Profile name is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 5)) {
            call.reject("setConfig requires DataWedge 6.5 or higher");
            return;
        }
        
        Bundle profileConfig = new Bundle();
        profileConfig.putString("PROFILE_NAME", profileName);
        
        // Set profile enabled state if provided
        Boolean profileEnabled = call.getBoolean("profileEnabled");
        if (profileEnabled != null) {
            profileConfig.putString("PROFILE_ENABLED", profileEnabled ? "true" : "false");
        }
        
        // Set config mode if provided
        String configMode = call.getString("configMode", "UPDATE");
        profileConfig.putString("CONFIG_MODE", configMode);
        
        // Add additional config if provided
        JSObject config = call.getObject("config");
        if (config != null) {
            // Add plugin configurations
            if (config.has("PLUGIN_CONFIG")) {
                JSObject pluginConfig = config.getJSObject("PLUGIN_CONFIG");
                if (pluginConfig != null) {
                    Bundle pluginBundle = convertJSObjectToBundle(pluginConfig);
                    profileConfig.putBundle("PLUGIN_CONFIG", pluginBundle);
                }
            }
            
            // Add app associations
            if (config.has("APP_LIST")) {
                try {
                    JSONArray appList = config.getJSONArray("APP_LIST");
                    if (appList != null) {
                        Bundle[] apps = new Bundle[appList.length()];
                        for (int i = 0; i < appList.length(); i++) {
                            try {
                            JSONObject app = appList.getJSONObject(i);
                            Bundle appBundle = new Bundle();
                            appBundle.putString("PACKAGE_NAME", app.getString("packageName"));
                            if (app.has("activityList")) {
                                JSONArray activities = app.getJSONArray("activityList");
                                String[] activityArray = new String[activities.length()];
                                for (int j = 0; j < activities.length(); j++) {
                                    activityArray[j] = activities.getString(j);
                                }
                                appBundle.putStringArray("ACTIVITY_LIST", activityArray);
                            }
                            apps[i] = appBundle;
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing app list", e);
                        }
                    }
                    profileConfig.putParcelableArray("APP_LIST", apps);
                }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing APP_LIST", e);
                }
            }
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.SET_CONFIG", profileConfig);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Set config for profile: " + profileName);
        call.resolve();
    }
    
    @PluginMethod
    public void setDisabledAppList(PluginCall call) {
        JSONArray apps = call.getArray("apps");
        if (apps == null) {
            call.reject("Apps list is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 9)) {
            call.reject("setDisabledAppList requires DataWedge 6.9 or higher");
            return;
        }
        
        String mode = call.getString("mode", "SET");
        
        Bundle disabledAppList = new Bundle();
        disabledAppList.putString("CONFIG_MODE", mode);
        
        Bundle[] appArray = new Bundle[apps.length()];
        for (int i = 0; i < apps.length(); i++) {
            try {
                JSONObject app = apps.getJSONObject(i);
                Bundle appBundle = new Bundle();
                appBundle.putString("PACKAGE_NAME", app.getString("packageName"));
                
                if (app.has("activityList")) {
                    JSONArray activities = app.getJSONArray("activityList");
                    String[] activityArray = new String[activities.length()];
                    for (int j = 0; j < activities.length(); j++) {
                        activityArray[j] = activities.getString(j);
                    }
                    appBundle.putStringArray("ACTIVITY_LIST", activityArray);
                }
                
                appArray[i] = appBundle;
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing disabled app", e);
            }
        }
        disabledAppList.putParcelableArray("APP_LIST", appArray);
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.SET_DISABLED_APP_LIST", disabledAppList);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Set disabled app list with mode: " + mode);
        call.resolve();
    }
    
    @PluginMethod
    public void setIgnoreDisabledProfiles(PluginCall call) {
        Boolean ignoreDisabledProfiles = call.getBoolean("ignoreDisabledProfiles");
        if (ignoreDisabledProfiles == null) {
            call.reject("ignoreDisabledProfiles value is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(7, 1)) {
            call.reject("setIgnoreDisabledProfiles requires DataWedge 7.1 or higher");
            return;
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.SET_IGNORE_DISABLED_PROFILES", ignoreDisabledProfiles ? "true" : "false");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Set ignore disabled profiles: " + ignoreDisabledProfiles);
        call.resolve();
    }
    
    // ==================== End Configuration Management Methods ====================
    
    // ==================== Runtime Operations Methods ====================
    
    @PluginMethod
    public void disableDatawedge(PluginCall call) {
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.ENABLE_DATAWEDGE", false);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Disabled DataWedge");
        call.resolve();
    }
    
    @PluginMethod
    public void enableDatawedge(PluginCall call) {
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.ENABLE_DATAWEDGE", true);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Enabled DataWedge");
        call.resolve();
    }
    
    @PluginMethod
    public void disableScannerInput(PluginCall call) {
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 6)) {
            call.reject("disableScannerInput requires DataWedge 6.6 or higher");
            return;
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.SCANNER_INPUT_PLUGIN", "DISABLE_PLUGIN");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Disabled scanner input plugin");
        call.resolve();
    }
    
    @PluginMethod
    public void enableScannerInput(PluginCall call) {
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 6)) {
            call.reject("enableScannerInput requires DataWedge 6.6 or higher");
            return;
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.SCANNER_INPUT_PLUGIN", "ENABLE_PLUGIN");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Enabled scanner input plugin");
        call.resolve();
    }
    
    @PluginMethod
    public void enumerateTriggers(PluginCall call) {
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(8, 0)) {
            call.reject("enumerateTriggers requires DataWedge 8.0 or higher");
            return;
        }
        
        // Store pending call
        pendingTriggersCall = call;
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.ENUMERATE_TRIGGERS", "");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Requested trigger enumeration");
    }
    
    @PluginMethod
    public void notify(PluginCall call) {
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(11, 0)) {
            call.reject("notify requires DataWedge 11.0 or higher");
            return;
        }
        
        Bundle notifyBundle = new Bundle();
        
        String notificationType = call.getString("notificationType", "BEEP");
        notifyBundle.putString("NOTIFICATION_TYPE", notificationType);
        
        if ("LED".equals(notificationType)) {
            String ledColor = call.getString("ledColor", "GREEN");
            Integer ledOnDuration = call.getInt("ledOnDuration", 500);
            Integer ledOffDuration = call.getInt("ledOffDuration", 500);
            
            notifyBundle.putString("LED_COLOR", ledColor);
            notifyBundle.putInt("LED_ON_DURATION", ledOnDuration);
            notifyBundle.putInt("LED_OFF_DURATION", ledOffDuration);
        } else if ("BEEP".equals(notificationType)) {
            Integer beepVolume = call.getInt("beepVolume", 50);
            notifyBundle.putInt("BEEP_VOLUME", beepVolume);
        } else if ("VIBRATE".equals(notificationType)) {
            Integer vibrateDuration = call.getInt("vibrateDuration", 200);
            notifyBundle.putInt("VIBRATE_DURATION", vibrateDuration);
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.NOTIFY", notifyBundle);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Sent notification: " + notificationType);
        call.resolve();
    }
    
    @PluginMethod
    public void resetDefaultProfile(PluginCall call) {
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 8)) {
            call.reject("resetDefaultProfile requires DataWedge 6.8 or higher");
            return;
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.RESET_DEFAULT_PROFILE", "");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Reset default profile to Profile0");
        call.resolve();
    }
    
    @PluginMethod
    public void setDefaultProfile(PluginCall call) {
        String profileName = call.getString("profileName");
        if (profileName == null) {
            call.reject("Profile name is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 8)) {
            call.reject("setDefaultProfile requires DataWedge 6.8 or higher");
            return;
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.SET_DEFAULT_PROFILE", profileName);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Set default profile to: " + profileName);
        call.resolve();
    }
    
    @PluginMethod
    public void setReportingOptions(PluginCall call) {
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 8)) {
            call.reject("setReportingOptions requires DataWedge 6.8 or higher");
            return;
        }
        
        Bundle reportingBundle = new Bundle();
        
        Boolean enableReporting = call.getBoolean("enableReporting");
        if (enableReporting != null) {
            reportingBundle.putString("ENABLE_REPORTING", enableReporting ? "true" : "false");
        }
        
        Boolean autoImport = call.getBoolean("autoImport");
        if (autoImport != null) {
            reportingBundle.putString("AUTO_IMPORT", autoImport ? "true" : "false");
        }
        
        String reportFilePath = call.getString("reportFilePath");
        if (reportFilePath != null) {
            reportingBundle.putString("REPORT_FILE_PATH", reportFilePath);
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.SET_REPORTING_OPTIONS", reportingBundle);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Set reporting options");
        call.resolve();
    }
    
    @PluginMethod
    public void softRfidTrigger(PluginCall call) {
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(7, 0)) {
            call.reject("softRfidTrigger requires DataWedge 7.0 or higher");
            return;
        }
        
        // Store pending call
        pendingSoftRfidCall = call;
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.SOFT_RFID_TRIGGER", "TOGGLE");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Triggered RFID scan");
    }
    
    @PluginMethod
    public void softScanTrigger(PluginCall call) {
        // Store pending call
        pendingSoftScanCall = call;
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.SOFT_SCAN_TRIGGER", "TOGGLE_SCANNING");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Triggered soft scan");
    }
    
    @PluginMethod
    public void switchScanner(PluginCall call) {
        String scannerIndex = call.getString("scannerIndex");
        if (scannerIndex == null) {
            call.reject("Scanner index is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 3)) {
            call.reject("switchScanner requires DataWedge 6.3 or higher");
            return;
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.SWITCH_SCANNER", scannerIndex);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Switched to scanner: " + scannerIndex);
        call.resolve();
    }
    
    @PluginMethod
    public void switchScannerParams(PluginCall call) {
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 3)) {
            call.reject("switchScannerParams requires DataWedge 6.3 or higher");
            return;
        }
        
        Bundle paramsBundle = new Bundle();
        
        String scannerType = call.getString("scannerType");
        if (scannerType != null) {
            paramsBundle.putString("SCANNER_TYPE", scannerType);
        }
        
        String profileName = call.getString("profileName");
        if (profileName != null) {
            paramsBundle.putString("PROFILE_NAME", profileName);
        }
        
        // Add custom params if provided
        JSObject params = call.getObject("params");
        if (params != null) {
            Bundle customParams = convertJSObjectToBundle(params);
            paramsBundle.putAll(customParams);
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.SWITCH_SCANNER_PARAMS", paramsBundle);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Switched scanner params");
        call.resolve();
    }
    
    @PluginMethod
    public void switchToProfile(PluginCall call) {
        String profileName = call.getString("profileName");
        if (profileName == null) {
            call.reject("Profile name is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 8)) {
            call.reject("switchToProfile requires DataWedge 6.8 or higher");
            return;
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.SWITCH_TO_PROFILE", profileName);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Switched to profile: " + profileName);
        call.resolve();
    }
    
    // ==================== End Runtime Operations Methods ====================
    
    // ==================== Notification Management Methods ====================
    
    @PluginMethod
    public void registerForNotification(PluginCall call) {
        String notificationType = call.getString("notificationType");
        if (notificationType == null) {
            call.reject("Notification type is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 4)) {
            call.reject("registerForNotification requires DataWedge 6.4 or higher");
            return;
        }
        
        // Register notification receiver if not already registered
        if (!isNotificationListenerRegistered) {
            registerNotificationReceiver();
            isNotificationListenerRegistered = true;
        }
        
        Bundle bundle = new Bundle();
        bundle.putString("com.symbol.datawedge.api.APPLICATION_NAME", getContext().getPackageName());
        bundle.putString("com.symbol.datawedge.api.NOTIFICATION_TYPE", notificationType);
        
        // Add profile name if specified
        String profileName = call.getString("profileName");
        if (profileName != null) {
            bundle.putString("PROFILE_NAME", profileName);
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.REGISTER_FOR_NOTIFICATION", bundle);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Registered for notification: " + notificationType);
        call.resolve();
    }
    
    @PluginMethod
    public void unRegisterForNotification(PluginCall call) {
        String notificationType = call.getString("notificationType");
        if (notificationType == null) {
            call.reject("Notification type is required");
            return;
        }
        
        // Check version compatibility
        if (detectedVersion != null && !detectedVersion.isAtLeast(6, 4)) {
            call.reject("unRegisterForNotification requires DataWedge 6.4 or higher");
            return;
        }
        
        Bundle bundle = new Bundle();
        bundle.putString("com.symbol.datawedge.api.APPLICATION_NAME", getContext().getPackageName());
        bundle.putString("com.symbol.datawedge.api.NOTIFICATION_TYPE", notificationType);
        
        // Add profile name if specified
        String profileName = call.getString("profileName");
        if (profileName != null) {
            bundle.putString("PROFILE_NAME", profileName);
        }
        
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.UNREGISTER_FOR_NOTIFICATION", bundle);
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Unregistered from notification: " + notificationType);
        call.resolve();
    }
    
    private void registerNotificationReceiver() {
        if (notificationReceiver != null) {
            return; // Already registered
        }
        
        notificationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (!"com.symbol.datawedge.api.NOTIFICATION_ACTION".equals(action)) {
                    return;
                }
                
                Bundle bundle = intent.getExtras();
                if (bundle == null) {
                    return;
                }
                
                // Handle different notification types
                if (bundle.containsKey("com.symbol.datawedge.api.NOTIFICATION")) {
                    Bundle notificationBundle = bundle.getBundle("com.symbol.datawedge.api.NOTIFICATION");
                    if (notificationBundle != null) {
                        handleNotification(notificationBundle);
                    }
                }
                
                // Handle scanner status notification
                if (bundle.containsKey("SCANNER_STATUS")) {
                    String scannerStatus = bundle.getString("SCANNER_STATUS");
                    String profileName = bundle.getString("PROFILE_NAME");
                    
                    JSObject notification = new JSObject();
                    notification.put("notificationType", "SCANNER_STATUS");
                    notification.put("status", scannerStatus);
                    notification.put("profileName", profileName);
                    
                    notifyListeners("notificationReceived", notification);
                }
                
                // Handle profile switch notification
                if (bundle.containsKey("PROFILE_SWITCH")) {
                    String profileName = bundle.getString("PROFILE_NAME");
                    String previousProfile = bundle.getString("PREVIOUS_PROFILE");
                    
                    JSObject notification = new JSObject();
                    notification.put("notificationType", "PROFILE_SWITCH");
                    notification.put("profileName", profileName);
                    notification.put("previousProfile", previousProfile);
                    
                    notifyListeners("notificationReceived", notification);
                }
                
                // Handle configuration update notification
                if (bundle.containsKey("CONFIGURATION_UPDATE")) {
                    String profileName = bundle.getString("PROFILE_NAME");
                    String status = bundle.getString("STATUS");
                    
                    JSObject notification = new JSObject();
                    notification.put("notificationType", "CONFIGURATION_UPDATE");
                    notification.put("profileName", profileName);
                    notification.put("status", status);
                    
                    notifyListeners("notificationReceived", notification);
                }
                
                // Handle workflow status notification
                if (bundle.containsKey("WORKFLOW_STATUS")) {
                    String status = bundle.getString("STATUS");
                    String workflowName = bundle.getString("WORKFLOW_NAME");
                    
                    JSObject notification = new JSObject();
                    notification.put("notificationType", "WORKFLOW_STATUS");
                    notification.put("status", status);
                    notification.put("workflowName", workflowName);
                    
                    notifyListeners("notificationReceived", notification);
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.symbol.datawedge.api.NOTIFICATION_ACTION");
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        getContext().registerReceiver(notificationReceiver, filter);
        
        Log.d(TAG, "Notification receiver registered");
    }
    
    private void handleNotification(Bundle notificationBundle) {
        try {
            String notificationType = notificationBundle.getString("NOTIFICATION_TYPE");
            
            JSObject notification = new JSObject();
            notification.put("notificationType", notificationType);
            
            // Add all bundle data to notification
            for (String key : notificationBundle.keySet()) {
                Object value = notificationBundle.get(key);
                if (value instanceof String) {
                    notification.put(key, (String) value);
                } else if (value instanceof Boolean) {
                    notification.put(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    notification.put(key, (Integer) value);
                } else if (value != null) {
                    notification.put(key, value.toString());
                }
            }
            
            notifyListeners("notificationReceived", notification);
            Log.d(TAG, "Notification received: " + notificationType);
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling notification", e);
        }
    }
    
    // ==================== End Notification Management Methods ====================
    
    @PluginMethod
    public void getVersionInfo(PluginCall call) {
        if (detectedVersion != null) {
            JSObject result = new JSObject();
            result.put("version", detectedVersion.getOriginalString());
            result.put("major", detectedVersion.getMajor());
            result.put("minor", detectedVersion.getMinor());
            result.put("patch", detectedVersion.getPatch());
            result.put("build", detectedVersion.getBuild());
            
            // Add supported features
            JSONArray features = new JSONArray();
            for (DataWedgeFeature feature : DataWedgeFeature.values()) {
                if (detectedVersion.supports(feature)) {
                    features.put(feature.toString());
                }
            }
            result.put("supportedFeatures", features);
            
            call.resolve(result);
        } else {
            // Try to detect version if not already done
            if (!versionDetectionAttempted) {
                detectDataWedgeVersion();
            }
            
            JSObject result = new JSObject();
            result.put("version", "unknown");
            result.put("error", "Version not detected yet. Please try again.");
            call.resolve(result);
        }
    }

    private void registerResultReceiver() {
        if (resultReceiver != null) {
            return; // Already registered
        }
        
        resultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (!"com.symbol.datawedge.api.RESULT_ACTION".equals(action)) {
                    return;
                }
                
                Bundle bundle = intent.getExtras();
                if (bundle == null) {
                    return;
                }
                
                // Handle enumerate scanners response
                if (bundle.containsKey("com.symbol.datawedge.api.RESULT_ENUMERATE_SCANNERS")) {
                    handleEnumerateScannersResult(bundle);
                }
                
                // Handle active profile response
                if (bundle.containsKey("com.symbol.datawedge.api.RESULT_GET_ACTIVE_PROFILE")) {
                    handleActiveProfileResult(bundle);
                }
                
                // Handle profiles list response
                if (bundle.containsKey("com.symbol.datawedge.api.RESULT_GET_PROFILES_LIST")) {
                    handleProfilesListResult(bundle);
                }
                
                // Handle scanner status response
                if (bundle.containsKey("com.symbol.datawedge.api.RESULT_SCANNER_STATUS")) {
                    handleScannerStatusResult(bundle);
                }
                
                // Handle get config response (for associated apps and config)
                if (bundle.containsKey("com.symbol.datawedge.api.RESULT_GET_CONFIG")) {
                    handleGetConfigResult(bundle);
                }
                
                // Handle disabled app list response
                if (bundle.containsKey("com.symbol.datawedge.api.RESULT_GET_DISABLED_APP_LIST")) {
                    handleDisabledAppListResult(bundle);
                }
                
                // Handle ignore disabled profiles response
                if (bundle.containsKey("com.symbol.datawedge.api.RESULT_GET_IGNORE_DISABLED_PROFILES")) {
                    handleIgnoreDisabledProfilesResult(bundle);
                }
                
                // Handle enumerate triggers response
                if (bundle.containsKey("com.symbol.datawedge.api.RESULT_ENUMERATE_TRIGGERS")) {
                    handleEnumerateTriggersResult(bundle);
                }
                
                // Handle soft scan result
                if (bundle.containsKey("com.symbol.datawedge.api.RESULT_ACTION")) {
                    String resultAction = bundle.getString("com.symbol.datawedge.api.RESULT_ACTION");
                    if ("SOFT_SCAN_TRIGGER".equals(resultAction) && pendingSoftScanCall != null) {
                        handleSoftScanResult();
                    } else if ("SOFT_RFID_TRIGGER".equals(resultAction) && pendingSoftRfidCall != null) {
                        handleSoftRfidResult();
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.symbol.datawedge.api.RESULT_ACTION");
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        getContext().registerReceiver(resultReceiver, filter);
        
        Log.d(TAG, "Result receiver registered");
    }
    
    private void handleEnumerateScannersResult(Bundle bundle) {
        if (pendingEnumerateCall == null) return;
        
        try {
            JSONArray scannerArray = new JSONArray();
            
            // Debug: Log bundle keys
            for (String key : bundle.keySet()) {
                Log.d(TAG, "Bundle key: " + key + " = " + bundle.get(key));
            }
            
            // The scanners are returned directly as an array
            Parcelable[] scannerList = bundle.getParcelableArray("com.symbol.datawedge.api.RESULT_ENUMERATE_SCANNERS");
            
            if (scannerList == null) {
                // Try alternate keys
                scannerList = bundle.getParcelableArray("SCANNER_LIST");
            }
            
            if (scannerList != null) {
                Log.d(TAG, "Processing " + scannerList.length + " scanners");
                for (Parcelable parcelable : scannerList) {
                    if (parcelable instanceof Bundle) {
                        Bundle scanner = (Bundle) parcelable;
                        JSObject scannerObj = new JSObject();
                        scannerObj.put("scannerName", scanner.getString("SCANNER_NAME", ""));
                        scannerObj.put("scannerIndex", scanner.getString("SCANNER_INDEX", ""));
                        scannerObj.put("scannerConnectionState", scanner.getBoolean("SCANNER_CONNECTION_STATE", false));
                        scannerObj.put("scannerIdentifier", scanner.getString("SCANNER_IDENTIFIER", ""));
                        scannerObj.put("isDefaultScanner", scanner.getBoolean("SCANNER_DEFAULT", false));
                        
                        // Create simplified properties for frontend
                        scannerObj.put("name", scanner.getString("SCANNER_NAME", ""));
                        scannerObj.put("index", scanner.getInt("SCANNER_INDEX", -1));
                        scannerObj.put("connected", scanner.getBoolean("SCANNER_CONNECTION_STATE", false));
                        
                        scannerArray.put(scannerObj);
                    }
                }
                
                JSObject result = new JSObject();
                result.put("scanners", scannerArray);
                pendingEnumerateCall.resolve(result);
            } else {
                pendingEnumerateCall.reject("Failed to enumerate scanners");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling enumerate scanners result", e);
            pendingEnumerateCall.reject("Error enumerating scanners: " + e.getMessage());
        }
        
        pendingEnumerateCall = null;
    }
    
    private void handleActiveProfileResult(Bundle bundle) {
        if (pendingProfileCall == null) return;
        
        try {
            String profileName = bundle.getString("com.symbol.datawedge.api.RESULT_GET_ACTIVE_PROFILE");
            
            JSObject result = new JSObject();
            result.put("profileName", profileName != null ? profileName : "");
            pendingProfileCall.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error handling active profile result", e);
            pendingProfileCall.reject("Error getting active profile: " + e.getMessage());
        }
        
        pendingProfileCall = null;
    }
    
    private void handleProfilesListResult(Bundle bundle) {
        if (pendingProfilesListCall == null) return;
        
        try {
            String[] profilesList = bundle.getStringArray("com.symbol.datawedge.api.RESULT_GET_PROFILES_LIST");
            
            JSONArray profilesArray = new JSONArray();
            if (profilesList != null) {
                for (String profile : profilesList) {
                    profilesArray.put(profile);
                }
            }
            
            JSObject result = new JSObject();
            result.put("profiles", profilesArray);
            pendingProfilesListCall.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error handling profiles list result", e);
            pendingProfilesListCall.reject("Error getting profiles list: " + e.getMessage());
        }
        
        pendingProfilesListCall = null;
    }
    
    private void handleScannerStatusResult(Bundle bundle) {
        if (pendingScannerStatusCall == null) return;
        
        try {
            String status = bundle.getString("com.symbol.datawedge.api.RESULT_SCANNER_STATUS");
            
            JSObject result = new JSObject();
            result.put("status", status != null ? status : "UNKNOWN");
            pendingScannerStatusCall.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error handling scanner status result", e);
            pendingScannerStatusCall.reject("Error getting scanner status: " + e.getMessage());
        }
        
        pendingScannerStatusCall = null;
    }
    
    private void handleGetConfigResult(Bundle bundle) {
        try {
            Bundle configBundle = bundle.getBundle("com.symbol.datawedge.api.RESULT_GET_CONFIG");
            
            // Check if this is for associated apps
            if (pendingAssociatedAppsCall != null && configBundle != null) {
                if (configBundle.containsKey("APP_LIST")) {
                    Bundle[] appList = (Bundle[]) configBundle.getParcelableArray("APP_LIST");
                    JSONArray appsArray = new JSONArray();
                    
                    if (appList != null) {
                        for (Bundle app : appList) {
                            JSObject appObj = new JSObject();
                            appObj.put("packageName", app.getString("PACKAGE_NAME", ""));
                            
                            String[] activities = app.getStringArray("ACTIVITY_LIST");
                            if (activities != null) {
                                JSONArray activityArray = new JSONArray();
                                for (String activity : activities) {
                                    activityArray.put(activity);
                                }
                                appObj.put("activityList", activityArray);
                            }
                            
                            appsArray.put(appObj);
                        }
                    }
                    
                    JSObject result = new JSObject();
                    result.put("apps", appsArray);
                    pendingAssociatedAppsCall.resolve(result);
                    pendingAssociatedAppsCall = null;
                    return;
                }
            }
            
            // Otherwise it's a general config request
            if (pendingConfigCall != null && configBundle != null) {
                // Convert bundle to JSObject
                JSObject configResult = bundleToJSObject(configBundle);
                pendingConfigCall.resolve(configResult);
                pendingConfigCall = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling get config result", e);
            if (pendingAssociatedAppsCall != null) {
                pendingAssociatedAppsCall.reject("Error getting associated apps: " + e.getMessage());
                pendingAssociatedAppsCall = null;
            }
            if (pendingConfigCall != null) {
                pendingConfigCall.reject("Error getting config: " + e.getMessage());
                pendingConfigCall = null;
            }
        }
    }
    
    private void handleDisabledAppListResult(Bundle bundle) {
        if (pendingDisabledAppListCall == null) return;
        
        try {
            Bundle[] appList = (Bundle[]) bundle.getParcelableArray("com.symbol.datawedge.api.RESULT_GET_DISABLED_APP_LIST");
            JSONArray appsArray = new JSONArray();
            
            if (appList != null) {
                for (Bundle app : appList) {
                    JSObject appObj = new JSObject();
                    appObj.put("packageName", app.getString("PACKAGE_NAME", ""));
                    
                    String[] activities = app.getStringArray("ACTIVITY_LIST");
                    if (activities != null) {
                        JSONArray activityArray = new JSONArray();
                        for (String activity : activities) {
                            activityArray.put(activity);
                        }
                        appObj.put("activityList", activityArray);
                    }
                    
                    appsArray.put(appObj);
                }
            }
            
            JSObject result = new JSObject();
            result.put("apps", appsArray);
            pendingDisabledAppListCall.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error handling disabled app list result", e);
            pendingDisabledAppListCall.reject("Error getting disabled app list: " + e.getMessage());
        }
        
        pendingDisabledAppListCall = null;
    }
    
    private void handleIgnoreDisabledProfilesResult(Bundle bundle) {
        if (pendingIgnoreDisabledProfilesCall == null) return;
        
        try {
            boolean ignoreDisabledProfiles = bundle.getBoolean("com.symbol.datawedge.api.RESULT_GET_IGNORE_DISABLED_PROFILES", false);
            
            JSObject result = new JSObject();
            result.put("ignoreDisabledProfiles", ignoreDisabledProfiles);
            pendingIgnoreDisabledProfilesCall.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error handling ignore disabled profiles result", e);
            pendingIgnoreDisabledProfilesCall.reject("Error getting ignore disabled profiles: " + e.getMessage());
        }
        
        pendingIgnoreDisabledProfilesCall = null;
    }
    
    private void handleEnumerateTriggersResult(Bundle bundle) {
        if (pendingTriggersCall == null) return;
        
        try {
            String[] triggers = bundle.getStringArray("com.symbol.datawedge.api.RESULT_ENUMERATE_TRIGGERS");
            
            JSONArray triggersArray = new JSONArray();
            if (triggers != null) {
                for (String trigger : triggers) {
                    triggersArray.put(trigger);
                }
            }
            
            JSObject result = new JSObject();
            result.put("triggers", triggersArray);
            pendingTriggersCall.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error handling enumerate triggers result", e);
            pendingTriggersCall.reject("Error getting triggers: " + e.getMessage());
        }
        
        pendingTriggersCall = null;
    }
    
    private void handleSoftScanResult() {
        if (pendingSoftScanCall == null) return;
        
        // For soft scan trigger, we typically won't get immediate scan data
        // The actual scan data will come through the scan receiver
        // So we just acknowledge the trigger was sent
        JSObject result = new JSObject();
        result.put("triggered", true);
        result.put("message", "Soft scan triggered. Listen for scan events to receive data.");
        pendingSoftScanCall.resolve(result);
        pendingSoftScanCall = null;
    }
    
    private void handleSoftRfidResult() {
        if (pendingSoftRfidCall == null) return;
        
        // Similar to soft scan, RFID trigger won't return immediate data
        JSObject result = new JSObject();
        result.put("triggered", true);
        result.put("message", "RFID scan triggered. Listen for scan events to receive data.");
        pendingSoftRfidCall.resolve(result);
        pendingSoftRfidCall = null;
    }
    
    private JSObject bundleToJSObject(Bundle bundle) {
        JSObject jsObject = new JSObject();
        
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            
            if (value instanceof String) {
                jsObject.put(key, (String) value);
            } else if (value instanceof Boolean) {
                jsObject.put(key, (Boolean) value);
            } else if (value instanceof Integer) {
                jsObject.put(key, (Integer) value);
            } else if (value instanceof Double) {
                jsObject.put(key, (Double) value);
            } else if (value instanceof Bundle) {
                jsObject.put(key, bundleToJSObject((Bundle) value));
            } else if (value != null) {
                jsObject.put(key, value.toString());
            }
        }
        
        return jsObject;
    }

    private void detectDataWedgeVersion() {
        versionDetectionAttempted = true;
        
        // Register receiver for version info
        if (versionReceiver == null) {
            versionReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if ("com.symbol.datawedge.api.RESULT_ACTION".equals(action)) {
                        Bundle bundle = intent.getExtras();
                        if (bundle != null && bundle.containsKey("com.symbol.datawedge.api.RESULT_GET_VERSION_INFO")) {
                            Bundle versionInfo = bundle.getBundle("com.symbol.datawedge.api.RESULT_GET_VERSION_INFO");
                            if (versionInfo != null) {
                                String dwVersion = versionInfo.getString("DATAWEDGE");
                                if (dwVersion != null) {
                                    detectedVersion = DataWedgeVersion.parse(dwVersion);
                                    Log.d(TAG, "DataWedge version detected: " + dwVersion);
                                }
                            }
                        }
                    }
                }
            };
            
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.symbol.datawedge.api.RESULT_ACTION");
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            getContext().registerReceiver(versionReceiver, filter);
        }
        
        // Request version info
        Intent i = new Intent();
        i.setAction("com.symbol.datawedge.api.ACTION");
        i.putExtra("com.symbol.datawedge.api.GET_VERSION_INFO", "");
        getContext().sendBroadcast(i);
        
        Log.d(TAG, "Requested DataWedge version info");
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        
        if (scanReceiver != null && isListenerRegistered) {
            try {
                getContext().unregisterReceiver(scanReceiver);
                Log.d(TAG, "Scan broadcast receiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering scan receiver", e);
            }
            scanReceiver = null;
            isListenerRegistered = false;
        }
        
        if (versionReceiver != null) {
            try {
                getContext().unregisterReceiver(versionReceiver);
                Log.d(TAG, "Version broadcast receiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering version receiver", e);
            }
            versionReceiver = null;
        }
        
        if (resultReceiver != null) {
            try {
                getContext().unregisterReceiver(resultReceiver);
                Log.d(TAG, "Result broadcast receiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering result receiver", e);
            }
            resultReceiver = null;
        }
        
        if (notificationReceiver != null && isNotificationListenerRegistered) {
            try {
                getContext().unregisterReceiver(notificationReceiver);
                Log.d(TAG, "Notification broadcast receiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering notification receiver", e);
            }
            notificationReceiver = null;
            isNotificationListenerRegistered = false;
        }
    }
}