package dev.opencode.terminal;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.view.Gravity;

public class MainActivity extends Activity {

    private static final int REQ_SETUP = 1;

    private FrameLayout mContainer;
    private TermController mTerm;
    private SettingsStore.PrefsSettingsStore mStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mStore = new SettingsStore.PrefsSettingsStore(this);
        mContainer = new FrameLayout(this);
        setContentView(mContainer);

        if (!mStore.setupDone()) {
            startActivityForResult(new Intent(this, SetupActivity.class), REQ_SETUP);
            return;
        }
        startTerminal();
    }

    private void startTerminal() {
        if (mTerm != null) return;
        mTerm = new TermController(this, mContainer);
        mTerm.start(
                new String[] { "/system/bin/sh" },
                EnvBuilder.build(getFilesDir(), mStore.provider(), mStore.apiKey()),
                null /* cwd */);

        ExtraKeysView extraKeys = new ExtraKeysView(this, mTerm);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        mContainer.addView(extraKeys, lp);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SETUP) {
            startTerminal();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mTerm != null) mTerm.onResume();
    }

    @Override
    protected void onPause() {
        if (mTerm != null) mTerm.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mTerm != null) mTerm.onDestroy();
        super.onDestroy();
    }
}
