package dev.opencode.terminal;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
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
     */
    private static final String RUNTIME_MARKER_VERSION = "1.0";

    private FrameLayout mContainer;
    private TermController mTerm;
    private SettingsStore mStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mStore = new PrefsSettingsStore(this);
        mContainer = new FrameLayout(this);
        setContentView(mContainer);

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
            result = RuntimeInstaller.InstallResult.EXTRACTED;
        } finally {
            progress.dismiss();
        }

        Log.i(TAG, "install result=" + result);

        File wrapper = new File(getFilesDir(), "opencode/opencode2");
        boolean wrapperPresent = wrapper.isFile();

        // Política de ABI (primary only): o runtime pinado é arm64-v8a only
        // (ver vendor/libtermexec/VENDORED.md — ABI arm64-v8a/x86_64 do NDK,
        // proc nativo arm64). Uma propaganda de arm64-v8a como ABI *secundária*
        // (android.os.Build.SUPPORTED_ABIS contém arm64-v8a em emuladores x86,
        // via camada de tradução) NÃO implica que o processo rode nativo em
        // arm64 — tentar spawnar o binário arm64 em hardware x86_64 secundário
        // falha com status != 0 (observado na Task 10). Por isso o plano usa
        // apenas o ABI primário (SUPPORTED_ABIS[0]): planFor recebe
        // arm64-v8a ∈ {abis} apenas quando o ABI primário é efetivamente
        // arm64-v8a.
        String primaryAbi = (android.os.Build.SUPPORTED_ABIS != null
                && android.os.Build.SUPPORTED_ABIS.length > 0)
                ? android.os.Build.SUPPORTED_ABIS[0] : null;
        // Envolvemos o ABI primário como array de 1 elemento: TermBridge.planFor
        // preserva a API (arm64-v8a ∈ abis); aqui "abis" = [primaryAbi].
        TermBridge.Plan plan = TermBridge.planFor(
                primaryAbi == null ? new String[0] : new String[] {primaryAbi},
                wrapperPresent);
        Log.i(TAG, "plan=" + plan.mode + " reason=" + plan.reason);

        if (mTerm != null) return;
        mTerm = new TermController(this, mContainer);

        String[] argv;
        if (plan.mode == TermBridge.Mode.RUNTIME) {
            argv = new String[] {"sh", new File(getFilesDir(), "opencode/opencode2").getAbsolutePath()};
        } else {
            argv = new String[] {"/system/bin/sh"};
        }

        mTerm.start(argv, EnvBuilder.build(getFilesDir(), mStore.provider(), mStore.apiKey()), null);

        if (plan.mode == TermBridge.Mode.FALLBACK) {
            TextView banner = new TextView(this);
            banner.setText("Fallback: " + plan.reason);
            banner.setTextColor(0xFFFFFFFF);
            banner.setBackgroundColor(0xA0000000);
            banner.setTextSize(14f);
            banner.setPadding(24, 12, 24, 12);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP);
            mContainer.addView(banner, blp);
            mContainer.bringChildToFront(banner);
        }

        ExtraKeysView extraKeys = new ExtraKeysView(this, mTerm);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        mContainer.addView(extraKeys, lp);
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
            runFlow();
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
