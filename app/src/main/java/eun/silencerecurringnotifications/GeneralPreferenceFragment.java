package eun.silencerecurringnotifications;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.text.Html;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class GeneralPreferenceFragment extends PreferenceFragment {

    private String mPackageName = "global";
    public static final String EXTRA_PKG = "packageName";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle fragmentArgs = getActivity().getIntent().getBundleExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS);
        if (fragmentArgs != null) {
            String packageName = fragmentArgs.getString(EXTRA_PKG, "");
            if (!packageName.isEmpty()) {
                mPackageName = packageName;
            }
        }
        getPreferenceManager().setSharedPreferencesName(mPackageName);
        addPreferencesFromResource(R.xml.pref_general);
        setHasOptionsMenu(true);


        Preference preferenceBlock = findPreference(getString(R.string.key_block));
        Preference preferenceTimeout = findPreference(getString(R.string.key_timeout));
        Preference preferenceSilent = findPreference(getString(R.string.key_silent));
        Preference preferenceRemove = findPreference(getString(R.string.key_remove));
        Preference preferenceExplanation = findPreference("block_timeout_explanation");

        bindPreferenceSummaryToValue(preferenceBlock, getResources().getStringArray(R.array.pref_default_block));
        bindPreferenceSummaryToValue(preferenceTimeout, getString(R.string.pref_default_timeout));


        ((DescriptionPreference)preferenceExplanation).setDescription(Html.fromHtml(getString(R.string.pref_title_block_timeout_explanation)));

        // if preferenceSilent changed
        // true => enable preferenceRemove
        // false => enable preferenceTimeout, preferenceExplanation
        bindPreferenceDependency(preferenceSilent, getResources().getBoolean(R.bool.pref_default_silent), new Preference[]{preferenceRemove}, new Preference[]{preferenceTimeout, preferenceExplanation});

        // if preferenceRemove changed
        // true =>
        // false => enable preferenceBlock
        bindPreferenceDependency(preferenceRemove, getResources().getBoolean(R.bool.pref_default_remove), new Preference[]{}, new Preference[]{preferenceBlock});


        // cancelNotification is avalable since Lollipop
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            getPreferenceScreen().removePreference(preferenceRemove);
        }
    }

    private SharedPreferences.OnSharedPreferenceChangeListener sPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            SettingsActivity.SendNewSettings(getActivity(), mPackageName);
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(sPreferenceChangeListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        getSharedPreferences().registerOnSharedPreferenceChangeListener(sPreferenceChangeListener);
    }


    private SharedPreferences getSharedPreferences()
    {
        if (mPackageName == null)
            return getActivity().getSharedPreferences("global", Context.MODE_PRIVATE);
        else
            return getActivity().getSharedPreferences(mPackageName, Context.MODE_PRIVATE);

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            getActivity().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void bindPreferenceSummaryToValue(Preference preference, Object defaultValue) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.

        if (preference instanceof MultiSelectListPreference) {

            if (defaultValue instanceof Set)
                sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, getSharedPreferences().getStringSet(preference.getKey(), (Set<String>) defaultValue));
            else if (defaultValue instanceof String[])
                sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, getSharedPreferences().getStringSet(preference.getKey(), new HashSet<String>(Arrays.asList((String[])defaultValue))));
        }
        else {
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, getSharedPreferences().getString(preference.getKey(), (String) defaultValue));
        }
    }

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {

            if (value == null)
                return true;


            if (preference instanceof MultiSelectListPreference) {
                List<String> selectedValues = new ArrayList<>((HashSet<String>)value);
                Collections.sort(selectedValues);
                MultiSelectListPreference listPreference = (MultiSelectListPreference) preference;

                String s = "";

                for (String stringValue : selectedValues)
                {
                    s += (s.equals("") ? "" : ", ") +  listPreference.getEntries()[Integer.valueOf(stringValue)];
                }
                preference.setSummary(s);
            }
            else if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(value.toString());

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(value.toString());
            }
            return true;
        }
    };



    private void bindPreferenceDependency(final Preference preference, boolean defaultValue, final Preference[] truePreferences, final Preference[] falsePreferences) {
        Preference.OnPreferenceChangeListener listener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preferenceChanged, Object value) {
                if (value instanceof Boolean) {

                    boolean bValue = preferenceChanged.isEnabled() && (boolean)value;
                    for (Preference preference : truePreferences)
                    {
                        preference.setEnabled(bValue);
                        Preference.OnPreferenceChangeListener preferenceListener = preference.getOnPreferenceChangeListener();
                        if (preferenceListener != null) {
                            preferenceListener.onPreferenceChange(preference, getSharedPreferences().getAll().get(preference.getKey()));
                        }
                    }

                    for (Preference preference : falsePreferences)
                    {
                        preference.setEnabled(!bValue);
                        Preference.OnPreferenceChangeListener preferenceListener = preference.getOnPreferenceChangeListener();
                        if (preferenceListener != null) {
                            preferenceListener.onPreferenceChange(preference, getSharedPreferences().getAll().get(preference.getKey()));
                        }
                    }
                }
                return true;
            }
        };
        preference.setOnPreferenceChangeListener(listener);
        listener.onPreferenceChange(preference, getSharedPreferences().getBoolean(preference.getKey(), defaultValue));
    }

}
