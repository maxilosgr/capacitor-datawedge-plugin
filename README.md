# Capacitor DataWedge Plugin

Comprehensive Android implementation of Zebra DataWedge API for Capacitor applications.

## Installation

```bash
npm install github:maxilosgr/capacitor-datawedge-plugin#main/plugin
npx cap sync
```

## Features

### 35+ Implemented Methods

#### Configuration Management (9 methods)
- `createProfile()` - Create new DataWedge profile
- `cloneProfile()` - Clone existing profile with all settings
- `renameProfile()` - Rename existing profile
- `deleteProfile()` - Delete profile
- `importConfig()` - Import configuration from file
- `restoreConfig()` - Reset to factory defaults
- `setConfig()` - Update profile configuration
- `setDisabledAppList()` - Manage blocked apps
- `setIgnoreDisabledProfiles()` - Control disabled profile behavior

#### Query Operations (10 methods)
- `getVersionInfo()` - Get DataWedge version details
- `getDatawedgeStatus()` - Check enabled/disabled status
- `getScannerStatus()` - Get scanner state
- `getActiveProfile()` - Get current profile name
- `getProfilesList()` - List all profiles
- `enumerateScanners()` - List available scanners
- `getAssociatedApps()` - Get apps linked to profile
- `getConfig()` - Get profile configuration
- `getDisabledAppList()` - Get blocked apps list
- `getIgnoreDisabledProfiles()` - Get ignore setting

#### Runtime Operations (14 methods)
- `enableDatawedge()` / `disableDatawedge()` - Control DataWedge service
- `enableScannerInput()` / `disableScannerInput()` - Control scanner input
- `softScanTrigger()` - Trigger barcode scan programmatically
- `softRfidTrigger()` - Trigger RFID scan programmatically
- `switchToProfile()` - Switch active profile
- `switchScanner()` - Change active scanner
- `switchScannerParams()` - Temporarily modify scanner settings
- `setDefaultProfile()` - Set default profile
- `resetDefaultProfile()` - Reset to Profile0
- `enumerateTriggers()` - List hardware triggers
- `notify()` - Play notification (beep/vibrate/LED)
- `setReportingOptions()` - Configure reporting

#### Notification Management (2 methods)
- `registerForNotification()` - Register for status notifications
- `unRegisterForNotification()` - Unregister from notifications

## Usage Example

```typescript
import { DataWedge } from 'capacitor-datawedge-plugin';

// Initialize scanner
await DataWedge.enableDatawedge();
await DataWedge.enableScannerInput();

// Register scan listener
await DataWedge.registerScanListener();

// Listen for scan events
DataWedge.addListener('scanReceived', (event) => {
  console.log('Barcode:', event.data);
  console.log('Type:', event.labelType);
});

// Create and configure profile
await DataWedge.createProfile({ profileName: 'MyApp' });

const config = {
  profileName: 'MyApp',
  profileEnabled: true,
  configMode: 'UPDATE',
  config: {
    PLUGIN_CONFIG: {
      PLUGIN_NAME: 'INTENT',
      PARAM_LIST: {
        intent_output_enabled: 'true',
        intent_action: 'com.yourapp.SCAN',
        intent_delivery: '2'
      }
    }
  }
};
await DataWedge.setConfig(config);

// Trigger scan programmatically
await DataWedge.softScanTrigger({ action: 'START_SCANNING' });
```

## Events

### scanReceived
Triggered when barcode/RFID data is scanned:
```typescript
{
  data: string;        // Scanned data
  labelType: string;   // Barcode type (EAN13, CODE128, etc.)
  timestamp: number;   // Scan timestamp
}
```

### notificationReceived
Status and configuration change notifications:
```typescript
{
  type: string;  // SCANNER_STATUS, PROFILE_SWITCH, etc.
  data: any;     // Notification-specific data
}
```

## DataWedge Configuration

Configure a DataWedge profile with:
1. **Intent Output**: Enable with action matching your app (e.g., `com.yourapp.SCAN`)
2. **Intent Delivery**: Set to Broadcast (2)
3. **Associated App**: Your app's package name
4. **Scanner Input**: Enable desired scanner types

## Requirements

- **Android**: API 22+ (Android 5.1+)
- **DataWedge**: 6.0+ (11.4 recommended)
- **Capacitor**: 7.0.0+
- **Device**: Zebra/Symbol/Motorola with DataWedge

## Version Compatibility

| DataWedge | Features |
|-----------|----------|
| 6.0+ | Basic scanning, enable/disable |
| 6.3+ | Scanner enumeration |
| 6.4+ | Profile creation |
| 6.5+ | Configuration management |
| 6.6+ | Scanner input control |
| 6.7+ | Import/export |
| 7.0+ | RFID support |
| 11.4+ | Full feature set |

## API Documentation

See the [TypeScript definitions](src/definitions.ts) for complete API documentation.

## Developer

Connect I.T - Gregorios Machairidis 2025

## License

MIT