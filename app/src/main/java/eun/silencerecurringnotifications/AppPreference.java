package eun.silencerecurringnotifications;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Created by tsalzmann on 11.04.2016.
 */
public class AppPreference extends Preference {

    private String packageName;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AppPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public AppPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AppPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AppPreference(Context context) {
        super(context);
    }


    private OnPreferenceLongClickListener mOnPreferenceLongClickListener;

    @Override
    protected View onCreateView(ViewGroup parent) {
        ListView listView = (ListView)parent;

        listView.setOnItemLongClickListener(new ListView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                ListView listView = (ListView) parent;
                ListAdapter listAdapter = listView.getAdapter();
                Object obj = listAdapter.getItem(position);
                if (obj != null && obj instanceof AppPreference) {
                    return ((AppPreference)obj).mOnPreferenceLongClickListener.onPreferenceLongClick((AppPreference)obj);
                }
                return false;
            }
        });
        return super.onCreateView(parent);
    }

    public void setOnPreferenceLongClickListener(OnPreferenceLongClickListener onPreferenceLongClickListener) {
        mOnPreferenceLongClickListener = onPreferenceLongClickListener;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPackageName() {
        return packageName;
    }

    public interface OnPreferenceLongClickListener {
        boolean onPreferenceLongClick(AppPreference preference);
    }
}
