package dev.opencode.terminal;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQ_SETUP = 1;
    private static final String ASSET_NAME = "runtime-arm64.zip";
    /**
     * Versão gravada em {@code .extracted_marker} pelo {@link RuntimeInstaller}.
     * Ancorada ao {@code versionName} do build; mantida aqui como literal
     * enquanto {@code buildConfig} não é gerado pelo AGP (ver build.gradle.kts).
     *
     * <p>1.1: asset passou a incluir {@code bin/libpcre2-8.so} (dependência do
     * rg) — bump força re-extração em instalações que tinham o asset 1.0.
     */
    private static final String RUNTIME_MARKER_VERSION = "1.1";

    private LinearLayout mRoot;
    private FrameLayout mContainer;
    private TermController mTerm;
    private SettingsStore mStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mStore = new PrefsSettingsStore(this);
        // Banner de fallback fica em uma linha própria (acima do terminal),
        // não sobre a primeira linha do EmulatorView.
        mRoot = new LinearLayout(this);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mContainer = new FrameLayout(this);
        mRoot.addView(mContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(mRoot);
        ContentInsets.apply(this);

        if (!mStore.setupDone()) {
            startActivityForResult(new Intent(this, SetupActivity.class), REQ_SETUP);
            return;
        }
        runFlow();
    }

    private void runFlow() {
        AppDirs.ensure(getFilesDir());

        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Extraindo runtime");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(false);

        RuntimeInstaller.InstallResult result;
        try (AssetHandle asset = openAsset()) {
            progress.show();
            result = RuntimeInstaller.install(
                    getFilesDir(),
                    asset.stream,
                    asset.length,
                    RUNTIME_MARKER_VERSION,
                    (bytesExtracted, totalBytes) -> {
                        if (totalBytes > 0) {
                            progress.setProgress((int) (bytesExtracted * 100L / totalBytes));
                        }
                    });
        } catch (IOException e) {
            Log.e(TAG, "falha na extração do runtime", e);
            result = RuntimeInstaller.InstallResult.FAILED;
        } finally {
            progress.dismiss();
        }

        Log.i(TAG, "install result=" + result);

        File runtimeDir = new File(getFilesDir(), "opencode");
        File wrapper = new File(runtimeDir, "opencode2");
        File runtimeBin = new File(runtimeDir, "opencode2.bin");
        boolean wrapperPresent = wrapper.isFile() && runtimeBin.isFile();

        // O runtime pinado é arm64-v8a. Só o usamos quando arm64 é a ABI
        // primária. No AVD x86_64, arm64-v8a aparece como ABI secundária via
        // libndk_translation: o shell executa o binário, mas o execve a partir
        // do processo do app é negado com EACCES. Nesse caso, shell de
        // diagnóstico; no telefone arm64 nativo o runtime é usado.
        String[] supportedAbis = android.os.Build.SUPPORTED_ABIS != null
                ? android.os.Build.SUPPORTED_ABIS : new String[0];
        TermBridge.Plan plan = TermBridge.planFor(supportedAbis, wrapperPresent);
        if (result == RuntimeInstaller.InstallResult.FAILED) {
            plan = new TermBridge.Plan(TermBridge.Mode.FALLBACK, "install_failed");
        }
        Log.i(TAG, "plan=" + plan.mode + " reason=" + plan.reason);

        if (mTerm != null) return;
        mTerm = new TermController(this, mContainer);

        String[] argv;
        if (plan.mode == TermBridge.Mode.RUNTIME) {
            // TermExec usa execv(3), que NÃO pesquisa o PATH. Com "sh" o
            // execv falha com ENOENT e o filho sai 255 sem escrever nada.
            // Use o mesmo caminho absoluto do fallback.
            argv = new String[] {"/system/bin/sh", wrapper.getAbsolutePath()};
        } else {
            argv = new String[] {"/system/bin/sh"};
        }

        mTerm.start(argv, EnvBuilder.build(getFilesDir(), mStore.provider(), mStore.apiKey()), null);

        if (plan.mode == TermBridge.Mode.FALLBACK) {
            TextView banner = new TextView(this);
            banner.setText(fallbackMessage(plan.reason));
            banner.setTextColor(0xFFFFFFFF);
            banner.setBackgroundColor(0xFF37474F);
            banner.setTextSize(14f);
            banner.setPadding(24, 12, 24, 12);
            mRoot.addView(banner, 0, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        ExtraKeysView extraKeys = new ExtraKeysView(this, mTerm);
        // Abaixo do terminal (não sobreposto): o TUI usa a última linha para
        // status/entrada; a linha de teclas não pode escondê-la. O PTY recebe
        // a altura reduzida no onSizeChanged do EmulatorView.
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mRoot.addView(extraKeys, lp);
    }

    private static String fallbackMessage(String reason) {
        switch (reason) {
            case "install_failed":
                return "OpenCode indisponível: falha ao instalar o runtime. Shell de diagnóstico.";
            case "missing_runtime":
                return "OpenCode indisponível: runtime não encontrado. Shell de diagnóstico.";
            case "arm64_only":
            default:
                return "OpenCode indisponível: o runtime exige arm64 como ABI primário. Shell de diagnóstico.";
        }
    }

    private static final class AssetHandle implements AutoCloseable {
        final InputStream stream;
        final long length;
        AssetHandle(InputStream stream, long length) {
            this.stream = stream;
            this.length = length;
        }
        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    private AssetHandle openAsset() throws IOException {
        AssetManager assets = getAssets();
        try {
            AssetFileDescriptor afd = assets.openFd(ASSET_NAME);
            return new AssetHandle(afd.createInputStream(), afd.getLength());
        } catch (IOException e) {
            // Asset comprimido: copia para cache para obter o tamanho real.
            File cached = new File(getCacheDir(), ASSET_NAME);
            if (!cached.isFile() || cached.length() == 0) {
                copyAsset(assets.open(ASSET_NAME), cached);
            }
            return new AssetHandle(new FileInputStream(cached), cached.length());
        }
    }

    private static void copyAsset(InputStream in, File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        try (InputStream i = in; OutputStream o = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) != -1) {
                o.write(buf, 0, n);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SETUP) {
            // PULAR grava skipped=true e devolve RESULT_CANCELED; SALVAR grava
            // provider. BACK não grava nada — nesse caso o fluxo não pode
            // começar sem configuração (gate do onCreate seria burlado).
            if (mStore.setupDone()) {
                runFlow();
            } else {
                finish();
            }
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
