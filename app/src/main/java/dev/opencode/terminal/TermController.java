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
import java.io.OutputStream;
import java.util.Map;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;


public final class TermController {

    private static final String TAG = "TermController";

    private final Activity mActivity;
    private final ViewGroup mContainer;
    private final TextView mOverlay;
    private android.widget.LinearLayout mCopyBar;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private TerminalSession mSession;
    private TerminalView mView;
    private ParcelFileDescriptor mPtmx;
    private OutputStream mOutput;
    private Runnable mSessionEndListener;
    private boolean mStarted;
    private volatile boolean mPtyClosed;

    // Latches dos botões ExtraKeys (Ctrl/Alt): o terminal-view consulta
    // readControlKey()/readAltKey() ao processar cada tecla, então
    // o estado do latch é visível lá. Zerados após compor uma tecla.
    private boolean mCtrlLatched;
    private boolean mAltLatched;

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
     * Spawns the terminal child process and wires it to the TerminalView.
     *
     * @param argv command line (argv[0] is the program)
     * @param env  environment variables to apply; {@code null} means default
     * @param cwd  requested working directory.
     */
    public void start(String[] argv, Map<String, String> env, File cwd) {
        if (mStarted) {
            throw new IllegalStateException("TermController é de uso único");
        }
        if (argv == null || argv.length == 0) {
            throw new IllegalArgumentException("argv vazio");
        }
        mStarted = true;

        String shellPath = argv[0];
        String[] args = new String[argv.length - 1];
        System.arraycopy(argv, 1, args, 0, argv.length - 1);

        String[] envp = null;
        if (env != null) {
            envp = new String[env.size()];
            int i = 0;
            for (Map.Entry<String, String> entry : env.entrySet()) {
                envp[i++] = entry.getKey() + "=" + entry.getValue();
            }
        }

        final String cwdPath = cwd != null ? cwd.getAbsolutePath() : "/";

        mSession = new TerminalSession(shellPath, cwdPath, args, envp, null, new com.termux.terminal.TerminalSessionClient() {
            @Override
            public void onTextChanged(com.termux.terminal.TerminalSession changedSession) {
                if (mView != null) mView.onScreenUpdated();
            }

            @Override
            public void onTitleChanged(com.termux.terminal.TerminalSession changedSession) {
            }

            @Override
            public void onSessionFinished(com.termux.terminal.TerminalSession finishedSession) {
                mMainHandler.post(TermController.this::handleSessionEnd);
            }

            @Override
            public void onCopyTextToClipboard(com.termux.terminal.TerminalSession session, String text) {
            }

            @Override
            public void onPasteTextFromClipboard(com.termux.terminal.TerminalSession session) {
            }

            @Override
            public void onBell(com.termux.terminal.TerminalSession session) {
            }

            @Override
            public void onColorsChanged(com.termux.terminal.TerminalSession session) {
            }

            @Override
            public void onTerminalCursorStateChange(boolean state) {
            }

            @Override
            public void setTerminalShellPid(com.termux.terminal.TerminalSession session, int pid) {
            }

            @Override
            public Integer getTerminalCursorStyle() {
                return null;
            }

            @Override
            public void logError(String tag, String message) {
                android.util.Log.e(tag, message);
            }

            @Override
            public void logWarn(String tag, String message) {
                android.util.Log.w(tag, message);
            }

            @Override
            public void logInfo(String tag, String message) {
                android.util.Log.i(tag, message);
            }

            @Override
            public void logDebug(String tag, String message) {
                android.util.Log.d(tag, message);
            }

            @Override
            public void logVerbose(String tag, String message) {
                android.util.Log.v(tag, message);
            }

            @Override
            public void logStackTraceWithMessage(String tag, String message, Exception e) {
                android.util.Log.e(tag, message, e);
            }

            @Override
            public void logStackTrace(String tag, Exception e) {
                android.util.Log.e(tag, "Exception", e);
            }
        });

        mView = new TerminalView(mActivity, null);
        mView.setTerminalViewClient(new com.termux.view.TerminalViewClient() {
            @Override
            public float onScale(float scale) {
                return scale;
            }

            @Override
            public void onSingleTapUp(android.view.MotionEvent e) {
                if (mView == null) return;
                // Detecta URL no ponto tocado
                int[] colRow = mView.getColumnAndRow(e, true);
                if (colRow != null) {
                    String word = mView.getWordAt(colRow[0], colRow[1]);
                    if (word != null) {
                        // Limpa pontuação final comum nas URLs (ponto, vírgula, parênteses)
                        word = word.replaceAll("[.,\\)]+$", "");
                        if (word.startsWith("www.")) {
                            word = "https://" + word;
                        }
                        if (word.matches("^https?://\\S+")) {
                            try {
                                android.content.Intent intent = new android.content.Intent(
                                        android.content.Intent.ACTION_VIEW, android.net.Uri.parse(word));
                                mActivity.startActivity(intent);
                                return; // Link aberto, não abre o IME
                            } catch (android.content.ActivityNotFoundException ex) {
                                android.util.Log.w(TAG, "Nenhum navegador encontrado para a URL: " + word);
                            }
                        }
                    }
                }
                // Se não for URL, exibe o teclado do sistema (IME)
                mView.post(() -> showSoftKeyboard());
            }

            @Override
            public boolean shouldBackButtonBeMappedToEscape() {
                return false;
            }

            @Override
            public boolean shouldEnforceCharBasedInput() {
                return true;
            }

            @Override
            public boolean shouldUseCtrlSpaceWorkaround() {
                return false;
            }

            @Override
            public boolean isTerminalViewSelected() {
                return true;
            }

            @Override
            public void copyModeChanged(boolean copyMode) {
                mMainHandler.post(() -> {
                    if (copyMode) {
                        showCopyBar();
                    } else {
                        hideCopyBar();
                    }
                });
            }

            @Override
            public boolean onKeyDown(int keyCode, android.view.KeyEvent e, com.termux.terminal.TerminalSession session) {
                return false;
            }

            @Override
            public boolean onKeyUp(int keyCode, android.view.KeyEvent e) {
                return false;
            }

            @Override
            public boolean onLongPress(android.view.MotionEvent event) {
                return false;
            }

            @Override
            public boolean readControlKey() {
                boolean val = mCtrlLatched;
                mCtrlLatched = false;
                return val;
            }

            @Override
            public boolean readAltKey() {
                boolean val = mAltLatched;
                mAltLatched = false;
                return val;
            }

            @Override
            public boolean readShiftKey() {
                // sem latch de Shift: o Modo usa shift+tab via ESC [Z
                return false;
            }

            @Override
            public boolean readFnKey() {
                return false;
            }

            @Override
            public boolean onCodePoint(int codePoint, boolean ctrlDown, com.termux.terminal.TerminalSession session) {
                return false;
            }

            @Override
            public void onEmulatorSet() {
            }

            @Override
            public void logError(String tag, String message) {
                android.util.Log.e(tag, message);
            }

            @Override
            public void logWarn(String tag, String message) {
                android.util.Log.w(tag, message);
            }

            @Override
            public void logInfo(String tag, String message) {
                android.util.Log.i(tag, message);
            }

            @Override
            public void logDebug(String tag, String message) {
                android.util.Log.d(tag, message);
            }

            @Override
            public void logVerbose(String tag, String message) {
                android.util.Log.v(tag, message);
            }

            @Override
            public void logStackTraceWithMessage(String tag, String message, Exception e) {
                android.util.Log.e(tag, message, e);
            }

            @Override
            public void logStackTrace(String tag, Exception e) {
                android.util.Log.e(tag, "Exception", e);
            }
        });

        mView.attachSession(mSession);
        // A API do TerminalView exige setTextSize() ANTES de setTypeface():
        // setTypeface() lê mRenderer.mTextSize, e o renderer só é criado por
        // setTextSize() (que trata o caso null usando MONOSPACE). Sem essa
        // chamada, setTypeface() lança NullPointerException.
        // Mantém a escala visual do EmulatorView antigo (10px * density).
        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mView.setTextSize(Math.round(10 * metrics.density));
        mView.setTypeface(TerminalTypeface.load(mActivity));
        mView.setFocusable(true);
        mView.setFocusableInTouchMode(true);
        mView.requestFocus();

        mContainer.addView(mView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mContainer.bringChildToFront(mOverlay);

        mOutput = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b});
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if (mSession != null) {
                    mSession.write(b, off, len);
                }
            }
        };
    }

    public void setOnSessionEndListener(Runnable listener) {
        mSessionEndListener = listener;
    }

    public OutputStream outputStream() {
        return mOutput;
    }

    public void write(byte[] data, int offset, int count) {
        if (mSession != null) {
            mSession.write(data, offset, count);
        }
    }

    public void sendCtrl() {
        // Implement Ctrl by writing the appropriate control code
        try {
            if (mSession != null) {
                // Write Ctrl code (ASCII 0x1B for ESC, but we need to implement proper Ctrl handling)
                mSession.writeCodePoint(false, 0x1B); // Example: ESC as Ctrl-[
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to send Ctrl", e);
        }
    }

    public void showSoftKeyboard() {
        if (mView == null) return;
        mView.requestFocus();
        InputMethodManager imm = (InputMethodManager)
                mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            // Usamos 0 (sem flags como SHOW_IMPLICIT) para garantir que o teclado do sistema
            // abra de forma confiável ao tocar no terminal ou interagir com a fila de teclas extras.
            imm.showSoftInput(mView, 0);
        }
    }

    public void setCtrlLatched(boolean latched) {
        mCtrlLatched = latched;
    }

    public boolean isCtrlLatched() {
        return mCtrlLatched;
    }

    public void setAltLatched(boolean latched) {
        mAltLatched = latched;
    }

    public boolean isAltLatched() {
        return mAltLatched;
    }

    public void sendKeyEvent(int keyCode) {
        if (mView == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        int metaState = 0;
        if (mCtrlLatched) metaState |= android.view.KeyEvent.META_CTRL_ON | android.view.KeyEvent.META_CTRL_LEFT_ON;
        if (mAltLatched) metaState |= android.view.KeyEvent.META_ALT_ON | android.view.KeyEvent.META_ALT_LEFT_ON;

        android.view.KeyEvent downEvent = new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0, metaState);
        android.view.KeyEvent upEvent = new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, keyCode, 0, metaState);

        mView.onKeyDown(keyCode, downEvent);
        mView.onKeyUp(keyCode, upEvent);

        // Zera os latches após compor a tecla, conforme especificado
        mCtrlLatched = false;
        mAltLatched = false;
    }

    public void onResume() {
    }

    public void onPause() {
    }

    public void onDestroy() {
        if (mSession != null) {
            mSession.finishIfRunning();
        }
    }

    private void handleSessionEnd() {
        mOverlay.setVisibility(View.VISIBLE);
        mContainer.bringChildToFront(mOverlay);
        if (mSessionEndListener != null) {
            mSessionEndListener.run();
        }
    }

    private void showCopyBar() {
        if (mCopyBar != null) return;
        mCopyBar = new android.widget.LinearLayout(mActivity);
        mCopyBar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        mCopyBar.setBackgroundColor(0xFF1E1E1E);
        mCopyBar.setGravity(android.view.Gravity.CENTER);
        int padding = Math.round(8 * mActivity.getResources().getDisplayMetrics().density);
        mCopyBar.setPadding(padding, padding, padding, padding);

        android.widget.Button btnCopy = new android.widget.Button(mActivity);
        btnCopy.setText("Copiar");
        btnCopy.setTextColor(0xFFFFFFFF);
        btnCopy.setBackgroundColor(0xFF007ACC);
        btnCopy.setOnClickListener(v -> {
            String text = mView.getSelectedText();
            if (text != null && !text.isEmpty()) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text));
                    android.widget.Toast.makeText(mActivity, "Copiado", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
            mView.stopTextSelectionMode();
        });

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mCopyBar.addView(btnCopy, lp);

        mContainer.addView(mCopyBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP));
    }

    private void hideCopyBar() {
        if (mCopyBar != null) {
            mContainer.removeView(mCopyBar);
            mCopyBar = null;
        }
    }

    /**
     * Marca o PTY como fechado antes de encerrar a sessão e fecha o master.
     *
     * <p>{@link TermSession#finish()} fecha os streams que compartilham o mesmo
     * {@code ParcelFileDescriptor}; marcar a flag antes evita que um
     * {@code updateSize} tardio tente usar um fd já fechado.</p>
     */
    private void closePty() {
        mPtyClosed = true;
        if (mSession != null && mSession.isRunning()) {
            mSession.finishIfRunning();
        } else {
            closeQuietly(mPtmx);
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