package eun.silencerecurringnotifications;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by tsalzmann on 12.04.2016.
 */
public class DescriptionPreference extends Preference {

    private CharSequence mDescription;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public DescriptionPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public DescriptionPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public DescriptionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DescriptionPreference(Context context) {
        super(context);
        init();
    }

    private void init()
    {
        this.setPersistent(false);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected View onCreateView(ViewGroup parent) {
        LayoutInflater li = (LayoutInflater)getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE );
        return li.inflate(R.layout.description_preference, parent, false);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        ((TextView)view.findViewById(android.R.id.text1)).setText(mDescription);
    }

    public void setDescription(CharSequence description) {
        mDescription = description;
    }

    public CharSequence getDescription() {
        return mDescription;
    }
}
