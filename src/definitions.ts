export interface DataWedgeVersionInfo {
  version: string;
  major: number;
  minor: number;
  patch: number;
  build?: string;
  supportedFeatures: string[];
  error?: string;
}

export interface DataWedgeScanner {
  scannerName: string;
  scannerIndex: string;
  scannerConnectionState: boolean;
  scannerIdentifier: string;
  isDefaultScanner: boolean;
}

export interface DataWedgeProfileInfo {
  profileName: string;
  profileEnabled: boolean;
  configMode?: string;
  modifiedDate?: string;
}

export enum ScannerStatus {
  IDLE = "IDLE",
  WAITING = "WAITING",
  SCANNING = "SCANNING",
  DISABLED = "DISABLED",
  ERROR = "ERROR"
}

export interface DataWedgeAppConfig {
  packageName: string;
  activityList?: string[];
}

export interface CloneProfileOptions {
  sourceProfileName: string;
  destinationProfileName: string;
}

export interface CreateProfileOptions {
  profileName: string;
  profileEnabled?: boolean;
}

export interface DeleteProfileOptions {
  profileName: string;
}

export interface ImportConfigOptions {
  configFile: string;
  importMode?: 'OVERWRITE' | 'MERGE';
}

export interface RenameProfileOptions {
  currentProfileName: string;
  newProfileName: string;
}

export interface SetConfigOptions {
  profileName: string;
  profileEnabled?: boolean;
  configMode?: 'UPDATE' | 'CREATE_IF_NOT_EXIST' | 'OVERWRITE';
  config?: any;
}

export interface SetDisabledAppListOptions {
  apps: DataWedgeAppConfig[];
  mode?: 'ADD' | 'REMOVE' | 'SET';
}

export interface NotifyOptions {
  notificationType?: 'BEEP' | 'VIBRATE' | 'LED';
  ledColor?: string;
  ledOnDuration?: number;
  ledOffDuration?: number;
  beepVolume?: number;
  vibrateDuration?: number;
}

export interface SetReportingOptions {
  enableReporting?: boolean;
  autoImport?: boolean;
  reportFilePath?: string;
}

export interface SwitchScannerOptions {
  scannerIndex: string;
}

export interface SwitchScannerParamsOptions {
  scannerType?: string;
  profileName?: string;
  params?: any;
}

export interface ScanResult {
  data: string;
  labelType: string;
  source: string;
  timestamp: number;
}

export interface RegisterForNotificationOptions {
  notificationType: 'SCANNER_STATUS' | 'PROFILE_SWITCH' | 'CONFIGURATION_UPDATE' | 'WORKFLOW_STATUS';
  profileName?: string;
}

export interface NotificationEvent {
  notificationType: string;
  profileName?: string;
  status?: string;
  data?: any;
}

export interface DataWedgePlugin {
  /**
   * Send an intent to DataWedge API
   */
  sendIntent(options: { action: string; extras: any }): Promise<void>;

  /**
   * Get the last received scan intent data
   */
  getLastScanIntent(): Promise<{ data?: string; labelType?: string; timestamp?: number }>;

  /**
   * Check if DataWedge is available
   */
  isDataWedgeAvailable(): Promise<{ available: boolean }>;

  /**
   * Get diagnostic information about DataWedge detection
   */
  getDiagnosticInfo(): Promise<{ 
    isAvailable: boolean; 
    packageCheck: any; 
    officialApiReceiversFound?: number; 
    officialApiReceiversError?: string;
    versionApiReceiversFound?: number;
    versionApiReceiversError?: string;
  }>;

  /**
   * Query DataWedge status using official API
   */
  queryDataWedgeStatus(): Promise<{
    available: boolean;
    queryStatus?: string;
    error?: string;
  }>;

  /**
   * Get DataWedge version information
   */
  getVersionInfo(): Promise<DataWedgeVersionInfo>;

  /**
   * Enumerate available scanners on the device
   * @requires DataWedge 6.5+
   */
  enumerateScanners(): Promise<{ scanners: DataWedgeScanner[] }>;

  /**
   * Get the currently active profile name
   * @requires DataWedge 6.5+
   */
  getActiveProfile(): Promise<{ profileName: string }>;

  /**
   * Get list of all DataWedge profiles
   * @requires DataWedge 6.5+
   */
  getProfilesList(): Promise<{ profiles: string[] }>;

  /**
   * Get current scanner status
   * @requires DataWedge 6.3+
   */
  getScannerStatus(): Promise<{ status: ScannerStatus }>;

  /**
   * Get DataWedge enabled/disabled status
   * @requires DataWedge 6.3+
   */
  getDatawedgeStatus(): Promise<{ isEnabled: boolean }>;

  /**
   * Get apps associated with specified Profile
   * @requires DataWedge 6.5+
   */
  getAssociatedApps(options: { profileName: string }): Promise<{ apps: DataWedgeAppConfig[] }>;

  /**
   * Get configuration for specified profile
   * @requires DataWedge 6.5+
   */
  getConfig(options: { profileName: string; configType?: string }): Promise<any>;

  /**
   * Get list of apps/activities blocked from using DataWedge
   * @requires DataWedge 6.9+
   */
  getDisabledAppList(): Promise<{ apps: DataWedgeAppConfig[] }>;

  /**
   * Get status of 'Ignore Disabled Profiles' parameter
   * @requires DataWedge 7.1+
   */
  getIgnoreDisabledProfiles(): Promise<{ ignoreDisabledProfiles: boolean }>;

  // Configuration Management Methods

  /**
   * Clone an existing DataWedge profile
   * @requires DataWedge 6.5+
   */
  cloneProfile(options: CloneProfileOptions): Promise<void>;

  /**
   * Create a new DataWedge profile
   * @requires DataWedge 6.4+
   */
  createProfile(options: CreateProfileOptions): Promise<void>;

  /**
   * Delete an existing DataWedge profile
   * @requires DataWedge 6.6+
   */
  deleteProfile(options: DeleteProfileOptions): Promise<void>;

  /**
   * Import configuration from file
   * @requires DataWedge 6.7+
   */
  importConfig(options: ImportConfigOptions): Promise<void>;

  /**
   * Rename an existing DataWedge profile
   * @requires DataWedge 6.6+
   */
  renameProfile(options: RenameProfileOptions): Promise<void>;

  /**
   * Restore DataWedge to factory defaults
   * @requires DataWedge 6.7+
   */
  restoreConfig(): Promise<void>;

  /**
   * Set configuration for a DataWedge profile
   * @requires DataWedge 6.5+
   */
  setConfig(options: SetConfigOptions): Promise<void>;

  /**
   * Set list of disabled apps
   * @requires DataWedge 6.9+
   */
  setDisabledAppList(options: SetDisabledAppListOptions): Promise<void>;

  /**
   * Set whether to ignore disabled profiles
   * @requires DataWedge 7.1+
   */
  setIgnoreDisabledProfiles(options: { ignoreDisabledProfiles: boolean }): Promise<void>;

  // Runtime Operations Methods

  /**
   * Disable DataWedge on device
   * @requires DataWedge 6.0+
   */
  disableDatawedge(): Promise<void>;

  /**
   * Enable DataWedge on device
   * @requires DataWedge 6.0+
   */
  enableDatawedge(): Promise<void>;

  /**
   * Disable Scanner Input Plugin for current profile
   * @requires DataWedge 6.6+
   */
  disableScannerInput(): Promise<void>;

  /**
   * Enable Scanner Input Plugin for current profile
   * @requires DataWedge 6.6+
   */
  enableScannerInput(): Promise<void>;

  /**
   * Get list of supported triggers for the device
   * @requires DataWedge 8.0+
   */
  enumerateTriggers(): Promise<{ triggers: string[] }>;

  /**
   * Play notification sound/vibration/LED after scan
   * @requires DataWedge 11.0+
   */
  notify(options?: NotifyOptions): Promise<void>;

  /**
   * Reset default profile to Profile0
   * @requires DataWedge 6.8+
   */
  resetDefaultProfile(): Promise<void>;

  /**
   * Set specified profile as default
   * @requires DataWedge 6.8+
   */
  setDefaultProfile(options: { profileName: string }): Promise<void>;

  /**
   * Configure reporting options for import/export
   * @requires DataWedge 6.8+
   */
  setReportingOptions(options: SetReportingOptions): Promise<void>;

  /**
   * Trigger RFID scanning programmatically
   * @requires DataWedge 7.0+
   */
  softRfidTrigger(): Promise<ScanResult>;

  /**
   * Trigger barcode scanning programmatically
   * @requires DataWedge 6.0+
   */
  softScanTrigger(): Promise<ScanResult>;

  /**
   * Switch to a specific scanner
   * @requires DataWedge 6.3+
   */
  switchScanner(options: SwitchScannerOptions): Promise<void>;

  /**
   * Temporarily update scanner parameters
   * @requires DataWedge 6.3+
   */
  switchScannerParams(options: SwitchScannerParamsOptions): Promise<void>;

  /**
   * Switch to specified profile
   * @requires DataWedge 6.8+
   */
  switchToProfile(options: { profileName: string }): Promise<void>;

  // Notification Management Methods

  /**
   * Register for DataWedge status change notifications
   * @requires DataWedge 6.4+
   */
  registerForNotification(options: RegisterForNotificationOptions): Promise<void>;

  /**
   * Unregister from DataWedge notifications
   * @requires DataWedge 6.4+
   */
  unRegisterForNotification(options: RegisterForNotificationOptions): Promise<void>;

  /**
   * Register for scan intent notifications
   */
  registerScanListener(): Promise<void>;

  /**
   * Add a listener for scan events
   */
  addListener(
    eventName: 'scanReceived',
    listenerFunc: (event: { data: string; labelType: string; timestamp: number }) => void,
  ): Promise<any>;

  /**
   * Add a listener for notification events
   */
  addListener(
    eventName: 'notificationReceived',
    listenerFunc: (event: NotificationEvent) => void,
  ): Promise<any>;

  /**
   * Remove all listeners
   */
  removeAllListeners(): Promise<void>;
}