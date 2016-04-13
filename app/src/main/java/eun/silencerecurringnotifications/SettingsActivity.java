package eun.silencerecurringnotifications;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SwitchCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;

import java.util.List;

public class SettingsActivity extends AppCompatPreferenceActivity {
    private Snackbar mSnackBarAddService;


    private SwitchCompat mGlobalSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();

        if (getIntent().getBooleanExtra(PreferenceActivity.EXTRA_NO_HEADERS, false)) {
            if (actionBar != null) {
                // Show the Up button in the action bar.
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    @Override
    protected void onResume() {

        UpdateSnackBar();
        super.onResume();
    }

    @Override
    public boolean onNavigateUp() {
        invalidateOptionsMenu();
        return super.onNavigateUp();
    }

    private void UpdateSnackBar()
    {
        if (!IsEnabledNotificationListener(getApplicationContext(), Service.class)) {
            if (mSnackBarAddService == null) {
                mSnackBarAddService = Snackbar.make(this.getListView(), R.string.service_not_enabled, Snackbar.LENGTH_INDEFINITE).setAction(R.string.open_notification_listener_settings, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                        startActivity(intent);
                        mSnackBarAddService.dismiss();
                        mSnackBarAddService = null;
                    }
                });
            }
            mSnackBarAddService.show();
        }
        else if (!Utils.GotAllPermissions(this)) {

            if (mSnackBarAddService == null) {
                mSnackBarAddService = Snackbar.make(this.getListView(), R.string.permission_denied, Snackbar.LENGTH_INDEFINITE).setAction(R.string.open_permission_dialog, new View.OnClickListener() {
                    @SuppressLint("NewApi")
                    @Override
                    public void onClick(View v) {
                        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                        mSnackBarAddService.dismiss();
                        mSnackBarAddService = null;
                    }
                });
            }
            mSnackBarAddService.show();
        }
        else if (getSharedPreferences(getString(R.string.pref_global), Context.MODE_PRIVATE).getBoolean(getString(R.string.key_enabled), getResources().getBoolean(R.bool.pref_default_enabled)) == false)
        {
            if (mSnackBarAddService == null) {
                mSnackBarAddService = Snackbar.make(getListView(), R.string.global_disabled, Snackbar.LENGTH_INDEFINITE).setAction(R.string.global_enable, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        // if the switch is not visible do it manually
                        if (mGlobalSwitch != null)
                            mGlobalSwitch.setChecked(true);
                        else {
                            getSharedPreferences(getString(R.string.pref_global), Context.MODE_PRIVATE).edit().putBoolean(getString(R.string.key_enabled), true).commit();
                            SendEnabledState(SettingsActivity.this, true);
                        }
                        mSnackBarAddService.dismiss();
                        mSnackBarAddService = null;
                    }
                });
            }
            mSnackBarAddService.show();
        }
        else
        {
            if (mSnackBarAddService != null)
            {
                mSnackBarAddService.dismiss();
            }
        }
    }

    private SwitchCompat.OnCheckedChangeListener mSwitchChangedListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            getSharedPreferences(getString(R.string.pref_global), Context.MODE_PRIVATE).edit().putBoolean(getString(R.string.key_enabled), isChecked).commit();
            UpdateSnackBar();
            SendEnabledState(SettingsActivity.this, isChecked);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (hasHeaders() && !onIsHidingHeaders()) {
            getMenuInflater().inflate(R.menu.global_switch, menu);
            MenuItem item = menu.findItem(R.id.global_switch);
            item.setActionView(R.layout.switch_layout);

            mGlobalSwitch = (SwitchCompat) item.getActionView().findViewById(R.id.global_switch);
            mGlobalSwitch.setOnCheckedChangeListener(mSwitchChangedListener);
            mGlobalSwitch.setChecked(getSharedPreferences(getString(R.string.pref_global), Context.MODE_PRIVATE).getBoolean(getString(R.string.key_enabled), getResources().getBoolean(R.bool.pref_default_enabled)));
            mSwitchChangedListener.onCheckedChanged(mGlobalSwitch, mGlobalSwitch.isChecked());
        }
        return true;
    }

    public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                UpdateSnackBar();
                Intent intent = new Intent(SettingsActivity.this, Service.class);
                intent.setAction(Service.CHECK_PERMISSIONS_ACTION);
                SettingsActivity.this.startService(intent);
            }
        }
    }



    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onIsMultiPane() {
        return (getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName)
                || AppPreferenceFragment.class.getName().equals(fragmentName);
    }

    private static boolean IsEnabledNotificationListener(Context pkg, Class<?> cls)
    {
        ComponentName componentName = new ComponentName(pkg, Service.class);
        String flat = Settings.Secure.getString(pkg.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(componentName.flattenToString());
    }

    public static void SendNewSettings(Context context, String packageName) {
        if (IsEnabledNotificationListener(context, Service.class)) {
            Intent intent = new Intent(context, Service.class);
            intent.setAction(Service.RELOAD_SETTINGS_ACTION);
            if (packageName != null)
                intent.putExtra(Service.RELOAD_PACKAGE_EXTRA, packageName);
            context.startService(intent);
        }
    }

    public static void SendEnabledState(Context context, boolean enabled) {
        if (IsEnabledNotificationListener(context, Service.class)) {
            Intent intent = new Intent(context, Service.class);
            intent.setAction(Service.SET_STATE_ACTION);
            intent.putExtra(Service.SET_STATE_EXTRA, enabled);
            context.startService(intent);
        }
    }


}
