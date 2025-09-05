package gr.connectit.datawedge.plugin;

import android.util.Log;

/**
 * Represents a DataWedge version and provides version comparison utilities
 */
public class DataWedgeVersion {
    private static final String TAG = "DataWedgeVersion";
    
    private final int major;
    private final int minor;
    private final int patch;
    private final String build;
    private final String originalString;
    
    public DataWedgeVersion(int major, int minor, int patch, String build, String originalString) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.build = build;
        this.originalString = originalString;
    }
    
    /**
     * Parse a version string like "11.2.50" or "6.9.49.BUILD001"
     */
    public static DataWedgeVersion parse(String versionString) {
        if (versionString == null || versionString.isEmpty()) {
            return null;
        }
        
        try {
            // Remove any "DATAWEDGE " prefix if present
            String cleanVersion = versionString.replaceAll("^DATAWEDGE\\s+", "");
            
            // Split by dots
            String[] parts = cleanVersion.split("\\.");
            
            int major = 0, minor = 0, patch = 0;
            String build = "";
            
            if (parts.length > 0) {
                major = Integer.parseInt(parts[0]);
            }
            if (parts.length > 1) {
                minor = Integer.parseInt(parts[1]);
            }
            if (parts.length > 2) {
                // The third part might contain patch number and build
                String patchPart = parts[2];
                
                // Check if it contains non-numeric characters (build info)
                if (patchPart.matches("\\d+")) {
                    patch = Integer.parseInt(patchPart);
                } else {
                    // Extract numeric part as patch
                    String numericPart = patchPart.replaceAll("[^0-9].*", "");
                    if (!numericPart.isEmpty()) {
                        patch = Integer.parseInt(numericPart);
                    }
                    // Rest is build info
                    build = patchPart.replaceAll("^\\d+", "");
                }
            }
            
            // Collect any additional parts as build info
            if (parts.length > 3) {
                StringBuilder buildInfo = new StringBuilder(build);
                for (int i = 3; i < parts.length; i++) {
                    if (buildInfo.length() > 0) {
                        buildInfo.append(".");
                    }
                    buildInfo.append(parts[i]);
                }
                build = buildInfo.toString();
            }
            
            Log.d(TAG, String.format("Parsed version: %d.%d.%d (build: %s) from '%s'", 
                major, minor, patch, build, versionString));
            
            return new DataWedgeVersion(major, minor, patch, build, versionString);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse version string: " + versionString, e);
            return null;
        }
    }
    
    /**
     * Check if this version is at least the specified version
     */
    public boolean isAtLeast(int requiredMajor, int requiredMinor) {
        if (major > requiredMajor) {
            return true;
        }
        if (major < requiredMajor) {
            return false;
        }
        return minor >= requiredMinor;
    }
    
    /**
     * Check if this version is at least the specified version (including patch)
     */
    public boolean isAtLeast(int requiredMajor, int requiredMinor, int requiredPatch) {
        if (major > requiredMajor) {
            return true;
        }
        if (major < requiredMajor) {
            return false;
        }
        if (minor > requiredMinor) {
            return true;
        }
        if (minor < requiredMinor) {
            return false;
        }
        return patch >= requiredPatch;
    }
    
    /**
     * Check if this version supports a specific feature
     */
    public boolean supports(DataWedgeFeature feature) {
        switch (feature) {
            // DataWedge 6.0+ (Base features)
            case SOFT_SCAN_TRIGGER:
            case ENABLE_DATAWEDGE:
            case DISABLE_DATAWEDGE:
                return isAtLeast(6, 0);
                
            // DataWedge 6.3+
            case ENUMERATE_SCANNERS:
            case SWITCH_SCANNER:
            case SWITCH_SCANNER_PARAMS:
            case GET_SCANNER_STATUS:
                return isAtLeast(6, 3);
                
            // DataWedge 6.4+
            case CREATE_PROFILE:
            case REGISTER_FOR_NOTIFICATION:
            case UNREGISTER_FOR_NOTIFICATION:
                return isAtLeast(6, 4);
                
            // DataWedge 6.5+
            case SET_CONFIG:
            case GET_CONFIG:
            case CLONE_PROFILE:
            case GET_ACTIVE_PROFILE:
                return isAtLeast(6, 5);
                
            // DataWedge 6.6+
            case DELETE_PROFILE:
            case RENAME_PROFILE:
            case GET_PROFILES_LIST:
            case ENABLE_SCANNER_INPUT_PLUGIN:
            case DISABLE_SCANNER_INPUT_PLUGIN:
                return isAtLeast(6, 6);
                
            // DataWedge 6.7+
            case IMPORT_CONFIG:
            case RESTORE_CONFIG:
            case GET_DATAWEDGE_STATUS:
                return isAtLeast(6, 7);
                
            // DataWedge 6.8+
            case SET_REPORTING_OPTIONS:
            case SWITCH_TO_PROFILE:
            case SET_DEFAULT_PROFILE:
            case RESET_DEFAULT_PROFILE:
                return isAtLeast(6, 8);
                
            // DataWedge 6.9+
            case SET_DISABLED_APP_LIST:
            case GET_DISABLED_APP_LIST:
                return isAtLeast(6, 9);
                
            // DataWedge 7.0+
            case SOFT_RFID_TRIGGER:
                return isAtLeast(7, 0);
                
            // DataWedge 7.1+
            case SET_IGNORE_DISABLED_PROFILES:
            case GET_IGNORE_DISABLED_PROFILES:
                return isAtLeast(7, 1);
                
            // DataWedge 8.0+
            case ENUMERATE_TRIGGERS:
                return isAtLeast(8, 0);
                
            // DataWedge 11.0+
            case SWITCH_DATA_CAPTURE:
            case NOTIFY:
                return isAtLeast(11, 0);
                
            // DataWedge 11.3+
            case SET_MULTIPLE_CONFIGS:
            case GET_VERSION_INFO_EXT:
                return isAtLeast(11, 3);
                
            // DataWedge 11.4+ (Latest supported)
            case ADVANCED_CONFIG_OPTIONS:
            case ENHANCED_REPORTING:
                return isAtLeast(11, 4);
                
            default:
                return false;
        }
    }
    
    // Getters
    public int getMajor() { return major; }
    public int getMinor() { return minor; }
    public int getPatch() { return patch; }
    public String getBuild() { return build; }
    public String getOriginalString() { return originalString; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append(".").append(minor).append(".").append(patch);
        if (build != null && !build.isEmpty()) {
            sb.append(".").append(build);
        }
        return sb.toString();
    }
    
    /**
     * Enumeration of DataWedge features by version
     */
    public enum DataWedgeFeature {
        // DataWedge 6.0+ (Base features)
        SOFT_SCAN_TRIGGER,
        ENABLE_DATAWEDGE,
        DISABLE_DATAWEDGE,
        
        // DataWedge 6.3+
        ENUMERATE_SCANNERS,
        SWITCH_SCANNER,
        SWITCH_SCANNER_PARAMS,
        GET_SCANNER_STATUS,
        
        // DataWedge 6.4+
        CREATE_PROFILE,
        REGISTER_FOR_NOTIFICATION,
        UNREGISTER_FOR_NOTIFICATION,
        
        // DataWedge 6.5+
        SET_CONFIG,
        GET_CONFIG,
        CLONE_PROFILE,
        GET_ACTIVE_PROFILE,
        
        // DataWedge 6.6+
        DELETE_PROFILE,
        RENAME_PROFILE,
        GET_PROFILES_LIST,
        ENABLE_SCANNER_INPUT_PLUGIN,
        DISABLE_SCANNER_INPUT_PLUGIN,
        
        // DataWedge 6.7+
        IMPORT_CONFIG,
        RESTORE_CONFIG,
        GET_DATAWEDGE_STATUS,
        
        // DataWedge 6.8+
        SET_REPORTING_OPTIONS,
        SWITCH_TO_PROFILE,
        SET_DEFAULT_PROFILE,
        RESET_DEFAULT_PROFILE,
        
        // DataWedge 6.9+
        SET_DISABLED_APP_LIST,
        GET_DISABLED_APP_LIST,
        
        // DataWedge 7.0+
        SOFT_RFID_TRIGGER,
        
        // DataWedge 7.1+
        SET_IGNORE_DISABLED_PROFILES,
        GET_IGNORE_DISABLED_PROFILES,
        
        // DataWedge 8.0+
        ENUMERATE_TRIGGERS,
        
        // DataWedge 11.0+
        SWITCH_DATA_CAPTURE,
        NOTIFY,
        
        // DataWedge 11.3+
        SET_MULTIPLE_CONFIGS,
        GET_VERSION_INFO_EXT,
        
        // DataWedge 11.4+ (Latest supported)
        ADVANCED_CONFIG_OPTIONS,
        ENHANCED_REPORTING
    }
}