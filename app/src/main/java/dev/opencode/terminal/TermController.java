package dev.opencode.terminal;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.io.OutputStream;

import jackpal.androidterm.TermExec;
import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;

public final class TermController {

    private static final String TAG = "TermController";

    private final Activity mActivity;
    private final ViewGroup mContainer;
    private final TextView mOverlay;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private TermSession mSession;
    private EmulatorView mView;
    private ParcelFileDescriptor mPtmx;
    private ParcelFileDescriptor.AutoCloseOutputStream mOutput;
    private Runnable mSessionEndListener;
    private boolean mStarted;

    public TermController(Activity activity, ViewGroup container) {
        mActivity = activity;
        mContainer = container;

        mOverlay = new TextView(activity);
        mOverlay.setText("Sessão encerrada");
        mOverlay.setTextColor(0xFFFFFFFF);
        mOverlay.setBackgroundColor(0xE0000000);
        mOverlay.setTextSize(20);
        mOverlay.setGravity(Gravity.CENTER);
        mOverlay.setPadding(64, 64, 64, 64);
        mOverlay.setVisibility(View.GONE);
        mContainer.addView(mOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
    }

    /**
     * Spawns the terminal child process and wires it to the EmulatorView.
     *
     * @param argv command line (argv[0] is the program)
     * @param env  environment variables to apply; {@code null} means default
     * @param cwd  requested working directory.
     *             <strong>Currently ignored.</strong> Upstream {@code jackpal.termexec.TermExec}
     *             spawns via {@code execv} which inherits the parent process working
     *             directory; there is no chdir support in TermExec (nor in this
     *             wrapper). The parameter is retained for the Task 7 / Task 10
     *             public API so callers can pass a directory without breaking
     *             compatibility, but wiring it must wait for upstream to gain
     *             chdir support — do not invent native chdir here.
     */
    public void start(String[] argv, Map<String, String> env, File cwd) {
        if (mStarted) {
            throw new IllegalStateException("TermController é de uso único");
        }
        if (argv == null || argv.length == 0) {
            throw new IllegalArgumentException("argv vazio");
        }
        mStarted = true;

        try {
            mPtmx = ParcelFileDescriptor.open(new File("/dev/ptmx"),
                    ParcelFileDescriptor.MODE_READ_WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir /dev/ptmx", e);
        }

        mOutput = new ParcelFileDescriptor.AutoCloseOutputStream(mPtmx);
        TermSession session = new TermSession(false);
        session.setTermIn(new ParcelFileDescriptor.AutoCloseInputStream(mPtmx));
        session.setTermOut(mOutput);
        mSession = session;

        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mView = new EmulatorView(mActivity, session, metrics);
        // IME "cooked": o Gboard entrega texto via commitText, caminho em que
        // o Ctrl one-shot é aplicado. Com TYPE_NULL ele manda key events e o
        // toggle não vale (allowToggle=false para o teclado virtual).
        mView.setUseCookedIME(true);
        mContainer.addView(mView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mContainer.bringChildToFront(mOverlay);
        mView.requestFocus();

        final TermExec termExec = new TermExec(argv);
        if (env != null) {
            for (Map.Entry<String, String> entry : env.entrySet()) {
                termExec.environment().put(entry.getKey(), entry.getValue());
            }
        }

        Thread watcher = new Thread(() -> {
            try {
                int pid = termExec.start(mPtmx);
                int status = TermExec.waitFor(pid);
                android.util.Log.i(TAG, "filho saiu: pid=" + pid + " status=" + status);
            } catch (IOException e) {
                android.util.Log.e(TAG, "spawn falhou", e);
            } catch (RuntimeException e) {
                android.util.Log.e(TAG, "watcher abortado", e);
            }
            mMainHandler.post(this::handleSessionEnd);
        }, "TermController watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    public void setOnSessionEndListener(Runnable listener) {
        mSessionEndListener = listener;
    }

    /**
     * Raw PTY master output stream used by the extra-keys row to inject bytes.
     * Returns {@code null} until {@link #start} has been called. Callers must
     * guard against {@code null}. The stream is owned by this controller; do
     * NOT close it from outside.
     */
    public OutputStream outputStream() {
        return mOutput;
    }

    /**
     * Arms the terminal's built-in Ctrl modifier (one-shot): the next character
     * typed by the IME is converted to its control equivalent. Used by the
     * extra-keys row's Ctrl button.
     */
    public void sendCtrl() {
        if (mView != null) {
            mView.sendControlKey();
        }
    }

    /**
     * Abre o teclado virtual para o EmulatorView. Sem isso o usuário de celular
     * (sem teclado físico) não consegue digitar comandos nem usar o TUI.
     */
    public void showSoftKeyboard() {
        if (mView == null) {
            return;
        }
        mView.requestFocus();
        InputMethodManager imm = (InputMethodManager)
                mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mView, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void onResume() {
        if (mView != null) {
            mView.onResume();
        }
    }

    public void onPause() {
        if (mView != null) {
            mView.onPause();
        }
    }

    public void onDestroy() {
        if (mSession != null && mSession.isRunning()) {
            mSession.finish();
        } else {
            closeQuietly(mPtmx);
        }
    }

    private void handleSessionEnd() {
        if (mSession != null && mSession.isRunning()) {
            mSession.finish();
        } else {
            closeQuietly(mPtmx);
        }
        mOverlay.setVisibility(View.VISIBLE);
        mContainer.bringChildToFront(mOverlay);
        if (mSessionEndListener != null) {
            mSessionEndListener.run();
        }
    }

    private static void closeQuietly(ParcelFileDescriptor fd) {
        if (fd == null) {
            return;
        }
        try {
            fd.close();
        } catch (IOException e) {
            android.util.Log.w(TAG, "fechamento do ptmx falhou (ignored)", e);
        }
    }
}