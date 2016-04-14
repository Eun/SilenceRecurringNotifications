package eun.silencerecurringnotifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Vibrator;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


/**
 * Created by tobias on 23.02.16.
 */

public class Service extends NotificationListenerService {
    public final static String RELOAD_SETTINGS_ACTION = "reload_settings";
    public final static String RELOAD_PACKAGE_EXTRA = "package_name";
    public final static String CHECK_PERMISSIONS_ACTION = "check_permissions";

    public final static String SET_STATE_ACTION = "setstate";
    public final static String SET_STATE_EXTRA = "state";
    // Every hour should be enough
    private final static int CleanUpInterval = 60 * 60 * 1000;
    // Clean all Data older than 10 minutes
    private final static int CleanUpDeleteOlderAs = 10 * 60 * 1000;
    private String TAG = this.getClass().getSimpleName();

    private HashMap<String, AppNotification> mLastNotificationSet = new HashMap<>();
    private HashMap<String, AppSettings> mAppSettings = new HashMap<>();
    private HashMap<Uri, Integer> mNotificationSoundLengths = new HashMap<>();

    private static final String OwnPackageName = Service.class.getPackage().getName();

    private int mDefaultNotificationSoundLength = -1;

    private long mNextCleanUp;

    private AudioManager mAudioManager;
    private Vibrator mVibrator;

    private Handler mSoundHandler;
    private String mSoundHandlerWorkingOn = "";
    private AppSettings mGlobalSettings;
    private boolean mIsEnabled = false;

    private boolean mHasAllRequiredPermissions = false;
    private NotificationManager mNotificationManager;

    private static final int mLightsNotificationId = 100;

    private boolean mCancelLights = false;

    private Notification mBlockLightNotification;

    private BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                mNotificationManager.cancel(mLightsNotificationId);
            }
            else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (mCancelLights) {
                     mNotificationManager.notify(mLightsNotificationId, mBlockLightNotification);
                }
            }
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();
        mSoundHandler = new Handler();
        mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        ReloadSettings();
        if (!(mHasAllRequiredPermissions = Utils.GotAllPermissions(this))) {
            Log.d(TAG, "Got no permission to read sound files.");
        }
        mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        mBlockLightNotification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.transparent)
                .setPriority(Notification.PRIORITY_HIGH)
                .setLights(0,10000,10000)
                .build();
        registerReceiver(mScreenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
        registerReceiver(mScreenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(RELOAD_SETTINGS_ACTION)) {
                String packageName = intent.getStringExtra(RELOAD_PACKAGE_EXTRA);
                if (packageName != null && !packageName.isEmpty())
                    ReloadSettings(packageName);
                else
                    ReloadSettings();
            }
            else if (intent.getAction().equals(SET_STATE_ACTION)) {
                mIsEnabled = intent.getBooleanExtra(SET_STATE_EXTRA, false);
                Log.v(TAG, "State is " + mIsEnabled);
            }
            else if (intent.getAction().equals(CHECK_PERMISSIONS_ACTION)) {
                if (!(mHasAllRequiredPermissions = Utils.GotAllPermissions(this))) {
                    Log.d(TAG, "Got no permission to read sound files.");
                }
            }
        }


        return super.onStartCommand(intent, flags, startId);
    }


    @Override
    public void onDestroy() {
        unregisterReceiver(mScreenReceiver);
        super.onDestroy();
    }


    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {


        if (!mIsEnabled || !mHasAllRequiredPermissions)
            return;

        String packageName = sbn.getPackageName();

        if (packageName.equals(OwnPackageName))
            return;

        Log.d(TAG, "onNotificationPosted: packageName=" + packageName);

        if (mDefaultNotificationSoundLength == -1)
            mDefaultNotificationSoundLength = GetNotificationSoundLength(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));


        long timeStamp = System.currentTimeMillis();
        AppNotification notification = mLastNotificationSet.get(packageName);
        if (notification == null) {
            notification = new AppNotification();
            notification.AppSettings = GetAppSettings(packageName);
            notification.NextNotificationTime = timeStamp + notification.AppSettings.BlockTimeout;
            mLastNotificationSet.put(packageName, notification);
            return;
        }

        if (notification.AppSettings.Cancel && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cancelNotification(sbn.getKey());
            return;
        }

        boolean hasVibration  = notification.AppSettings.BlockVibrate && mVibrator.hasVibrator() && ((sbn.getNotification().defaults & Notification.DEFAULT_VIBRATE) == Notification.DEFAULT_VIBRATE || sbn.getNotification().vibrate != null);
        boolean hasLights = ((sbn.getNotification().flags & Notification.FLAG_SHOW_LIGHTS) == Notification.FLAG_SHOW_LIGHTS);
        int notificationSoundLength = -1;

        if (hasLights == true)
        {
            if (!notification.AppSettings.BlockLights) {
                mNotificationManager.cancel(mLightsNotificationId);
            }
            hasLights = notification.AppSettings.BlockLights;
        }

        if (notification.AppSettings.BlockSound) {
            if ((sbn.getNotification().defaults & Notification.DEFAULT_SOUND) == Notification.DEFAULT_SOUND) {
                notificationSoundLength = mDefaultNotificationSoundLength;
            } else if (sbn.getNotification().sound != null) {
                Integer soundLength = mNotificationSoundLengths.get(sbn.getNotification().sound);
                if (soundLength == null) {
                    soundLength = GetNotificationSoundLength(sbn.getNotification().sound);
                    mNotificationSoundLengths.put(sbn.getNotification().sound, soundLength);
                }
                notificationSoundLength = soundLength;
            }
        }

        if (notificationSoundLength > -1 || hasVibration || hasLights) {
            Log.d(TAG, "onNotificationPosted: packageName=" + packageName + " timeStamp=" + timeStamp + " NextNotificationTime=" + notification.NextNotificationTime);
            if (timeStamp < notification.NextNotificationTime) {
                notification.LastBlockedId = sbn.getId();
                if (notificationSoundLength > -1) {
                    Log.d(TAG, "onNotificationPosted: Muting Sound for " + notificationSoundLength);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        mAudioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0);
                    } else {
                        mAudioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
                    }

                    if (!mSoundHandlerWorkingOn.equals(packageName)) {
                        mSoundHandlerWorkingOn = packageName;
                        mSoundHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, "onNotificationPosted: Unmuting Sound");
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    mAudioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0);
                                } else {
                                    mAudioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false);
                                }
                            }
                        }, notificationSoundLength);
                    }

                }
                if (hasVibration) {
                    Log.d(TAG, "onNotificationPosted: Muting Vibration");
                    mVibrator.vibrate(1);
                }
                if (hasLights) {
                    Log.d(TAG, "onNotificationPosted: Muting Lights");
                    mCancelLights = true;
                }
            } else {
                notification.NextNotificationTime = timeStamp + notification.AppSettings.BlockTimeout;
            }

            // cleanup old stuff
            if (mNextCleanUp < timeStamp) {
                Log.v(TAG, "Running Cleanup");
                mNextCleanUp = timeStamp + CleanUpInterval;
                long deleteAfter = timeStamp - CleanUpDeleteOlderAs;


                for (Iterator<Map.Entry<String, AppNotification>> it = mLastNotificationSet.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, AppNotification> entry = it.next();
                    if (entry.getValue().NextNotificationTime < deleteAfter) {
                        Log.v(TAG, "Removing " + entry.getKey());
                        it.remove();
                    }
                }
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {

        for (StatusBarNotification sbn : getActiveNotifications())
        {
            boolean hasLights = ((sbn.getNotification().flags & Notification.FLAG_SHOW_LIGHTS) == Notification.FLAG_SHOW_LIGHTS);
            if (hasLights)
            {
                AppNotification notification = mLastNotificationSet.get(sbn.getPackageName());
                if (notification != null && notification.LastBlockedId == sbn.getId()) {
                    if (notification.AppSettings.BlockLights) {
                        mCancelLights = true;
                        return;
                    }
                }
            }
            break;
        }
        mCancelLights = false;
    }



    private int GetNotificationSoundLength(Uri sound) {
        MediaPlayer mp = MediaPlayer.create(this, sound);
        if (mp != null) {
            int duration = mp.getDuration();
            mp.release();
            return duration;
        }
        return 2500;
    }


    @Override
    public void onLowMemory() {
        mLastNotificationSet.clear();
        super.onLowMemory();
    }

    private AppSettings GetAppSettings(String packageName)
    {
        AppSettings lastNotification = mAppSettings.get(packageName);
        if (lastNotification == null)
        {
            return mGlobalSettings;
        }
        return lastNotification;
    }

    private void ReloadSettings() {
        mGlobalSettings = ReloadSettings(getString(R.string.pref_global));
        SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.pref_global), Context.MODE_PRIVATE);

        mIsEnabled = sharedPreferences.getBoolean(getString(R.string.key_enabled), getResources().getBoolean(R.bool.pref_default_enabled));
        Log.v(TAG, "State is " + mIsEnabled);

        mAppSettings.clear();
        if (sharedPreferences.getBoolean(getString(R.string.key_apps_enable), false)) {
            sharedPreferences = getSharedPreferences(getString(R.string.pref_apps), Context.MODE_PRIVATE);
            Set<String> packages = sharedPreferences.getStringSet(getString(R.string.key_apps_list), new HashSet<String>());
            for (String packageAppName : packages) {
                mAppSettings.put(packageAppName, ReloadSettings(packageAppName));
            }
        }
    }


    private AppSettings ReloadSettings(String packageName)
    {
        Log.d(TAG, "Loading Settings for " + packageName);
        SharedPreferences sharedPreferences = getSharedPreferences(packageName, Context.MODE_PRIVATE);
        AppSettings appSettings = new AppSettings();
        try {
            appSettings.BlockTimeout = Integer.parseInt(sharedPreferences.getString(getString(R.string.key_timeout), getResources().getString(R.string.pref_default_timeout))) * 1000;
        }
        catch (Exception e)
        {
            appSettings.BlockTimeout = Integer.parseInt(getResources().getString(R.string.pref_default_timeout)) * 1000;
        }
        Set<String> blockTypeSet = sharedPreferences.getStringSet(getString(R.string.key_block), new HashSet<String>(Arrays.asList(getResources().getStringArray(R.array.pref_default_block))));

        appSettings.BlockSound = blockTypeSet.contains("0");
        appSettings.BlockVibrate = blockTypeSet.contains("1");
        appSettings.BlockLights = blockTypeSet.contains("2");

        Log.v(TAG, "Settings for " + packageName);
        Log.v(TAG, "> BlockSound = " + appSettings.BlockSound);
        Log.v(TAG, "> BlockVibrate = " + appSettings.BlockVibrate);
        Log.v(TAG, "> BlockLights = " + appSettings.BlockLights);
        Log.v(TAG, "> BlockTimeout = " + appSettings.BlockTimeout);
        Log.v(TAG, "> BlockAlways = " + appSettings.BlockAlways);
        Log.v(TAG, "> Cancel = " + appSettings.Cancel);


        return appSettings;
    }
}
