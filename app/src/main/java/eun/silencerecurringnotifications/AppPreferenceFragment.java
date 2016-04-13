package eun.silencerecurringnotifications;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This fragment shows data and sync preferences only. It is used when the
 * activity is showing a two-pane settings UI.
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class AppPreferenceFragment extends PreferenceFragment {

    private static final int REQUEST_APP = 0;
    private MenuItem mAddAppMenuItem;

    private Set<String> mPackages;


    private Preference.OnPreferenceChangeListener sBindAppSwitchPreference = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            if (mAddAppMenuItem != null)
                mAddAppMenuItem.setVisible((boolean)value);
            return true;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(getString(R.string.pref_global));
        addPreferencesFromResource(R.xml.pref_apps);
        setHasOptionsMenu(true);

        Preference preference = findPreference(getString(R.string.key_apps_enable));
        preference.setOnPreferenceChangeListener(sBindAppSwitchPreference);
        sBindAppSwitchPreference.onPreferenceChange(preference, PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getBoolean(preference.getKey(), false));
        updateAppList(true);


    }

    private SharedPreferences.OnSharedPreferenceChangeListener sPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // pass null here, so the service reloads the app list
            SettingsActivity.SendNewSettings(getActivity(), null);
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        getActivity().getSharedPreferences(getString(R.string.pref_global), Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(sPreferenceChangeListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().getSharedPreferences(getString(R.string.pref_global), Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(sPreferenceChangeListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            startActivity(new Intent(getActivity(), SettingsActivity.class));
            return true;
        }
        else if (id == R.id.addapp)
        {
            Intent intent = new Intent(getActivity(), AppPicker.class);
            startActivityForResult(intent, REQUEST_APP);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_APP && resultCode == Activity.RESULT_OK)
        {
            addToAppList(data.getAction());
        }
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.apps, menu);
        mAddAppMenuItem = menu.findItem(R.id.addapp);
        mAddAppMenuItem.setVisible(((SwitchPreference)findPreference(getString(R.string.key_apps_enable))).isChecked());
    }

    private void addToAppList(String pkg)
    {
        mPackages.add(pkg);
        getActivity().getSharedPreferences(getString(R.string.pref_apps), Context.MODE_PRIVATE).edit().clear().putStringSet(getString(R.string.key_apps_list), mPackages).commit();
        updateAppList(false);
        SettingsActivity.SendNewSettings(getActivity(), null);
    }

    private void removeApp(String pkg) {
        mPackages.remove(pkg);
        getActivity().getSharedPreferences(getString(R.string.pref_apps), Context.MODE_PRIVATE).edit().clear().putStringSet(getString(R.string.key_apps_list), mPackages).commit();
        updateAppList(false);
        getActivity().getSharedPreferences(pkg, Context.MODE_PRIVATE).edit().clear().commit();
        try {
            new File(getActivity().getFilesDir().getParent() + "/shared_prefs/", pkg + ".xml").delete();
        }
        catch (Exception e)
        {

        }
        SettingsActivity.SendNewSettings(getActivity(), null);
    }

    private void updateAppList(boolean reloadPackages)
    {
        PreferenceCategory category = (PreferenceCategory)findPreference("cat_apps");
        category.setOrderingAsAdded(false);
        Context context = category.getContext();
        category.removeAll();
        if (reloadPackages)
            mPackages = getActivity().getSharedPreferences(getString(R.string.pref_apps), Context.MODE_PRIVATE).getStringSet(getString(R.string.key_apps_list), new HashSet<String>());

        List<Preference> preferenceList = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        for (String pkg : mPackages)
        {
            AppPreference preference = new AppPreference(category.getContext());
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                preference.setTitle(info.loadLabel(pm).toString());
                preference.setIcon(info.loadIcon(pm));
            }
            catch (Exception e)
            {
                preference.setTitle(pkg);
                preference.setIcon(android.R.drawable.sym_def_app_icon);
            }
            preference.setPersistent(false);
            preference.setPackageName(pkg);
            preference.setOnPreferenceClickListener(sOnPreferenceClickListener);
            preference.setOnPreferenceLongClickListener(sOnPreferenceLongClickListener);

            preferenceList.add(preference);
        }
        Collections.sort(preferenceList, sDisplayNameComparator);

        for (Preference preference : preferenceList)
        {
            category.addPreference(preference);
        }
    }


    private Preference.OnPreferenceClickListener sOnPreferenceClickListener = new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (preference instanceof AppPreference)
            {
                Intent intent = new Intent(preference.getContext(), SettingsActivity.class);

                intent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
                intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, GeneralPreferenceFragment.class.getName());

                Bundle fragmentArgs = new Bundle();
                fragmentArgs.putString(GeneralPreferenceFragment.EXTRA_PKG, ((AppPreference) preference).getPackageName());
                intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArgs);

                startActivity(intent);
                return true;
            }
            return false;
        }
    };


    private AppPreference.OnPreferenceLongClickListener sOnPreferenceLongClickListener = new AppPreference.OnPreferenceLongClickListener() {
        @Override
        public boolean onPreferenceLongClick(final AppPreference preference) {
            if (findPreference(getString(R.string.key_apps_enable)).isEnabled()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(preference.getContext())
                        .setTitle(R.string.dialog_delete_title)
                        .setMessage(R.string.dialog_delete_message)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                removeApp(preference.getPackageName());
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null);
                builder.show();
            }
            return true;
        }
    };

    private final static Comparator<Preference> sDisplayNameComparator
            = new Comparator<Preference>() {
        public final int compare(Preference a, Preference b) {
            return collator.compare(a.getTitle(), b.getTitle());
        }

        private final Collator collator = Collator.getInstance();
    };
}
