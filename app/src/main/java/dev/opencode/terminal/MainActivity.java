package dev.opencode.terminal;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

/**
 * Task 6: shell interativo no AVD (spec T1).
 *
 * <p>Wiring mínimo — toda a lógica de PTY/sessão vive em {@link TermController}.
 * Path de teste: spawn de {@code /system/bin/sh} com o env fixado por
 * {@link EnvBuilder#build}. A integração com a runtime OpenCode (outro argv,
 * chaves de provider) é da Task 10.
 */
public class MainActivity extends Activity {

    private TermController mTerm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // FrameLayout: o overlay "Sessão encerrada" fica por cima da view.
        FrameLayout container = new FrameLayout(this);
        setContentView(container);

        mTerm = new TermController(this, container);
        mTerm.start(
                new String[] { "/system/bin/sh" },
                EnvBuilder.build(getFilesDir(), null, null),
                null /* cwd: ignorado pelo TermExec (sem chdir) */);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mTerm != null) {
            mTerm.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mTerm != null) {
            mTerm.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mTerm != null) {
            mTerm.onDestroy();
        }
        super.onDestroy();
    }
}
