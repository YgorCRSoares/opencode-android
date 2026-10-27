package dev.opencode.terminal;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Map;

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

        TermSession session = new TermSession(false);
        session.setTermIn(new ParcelFileDescriptor.AutoCloseInputStream(mPtmx));
        session.setTermOut(new ParcelFileDescriptor.AutoCloseOutputStream(mPtmx));
        mSession = session;

        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mView = new EmulatorView(mActivity, session, metrics);
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