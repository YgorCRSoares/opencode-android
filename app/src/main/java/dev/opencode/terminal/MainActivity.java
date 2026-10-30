package dev.opencode.terminal;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
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
        // Fundo opaco evita que o wallpaper apareça através de células que a
        // TUI deixa sem pintura (por exemplo, o espaço do logo no home).
        mRoot.setBackgroundColor(0xFF000000);
        mContainer = new FrameLayout(this);
        mContainer.setBackgroundColor(0xFF000000);
        mRoot.addView(mContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(mRoot);
        // O DecorView/WindowInsetsController só existe depois de setContentView;
        // chamar antes provoca NPE em getWindow().getInsetsController().
        applySystemBarAppearance();
        ContentInsets.apply(this);

        if (!mStore.setupDone()) {
            startActivityForResult(new Intent(this, SetupActivity.class), REQ_SETUP);
            return;
        }
        runFlow();
    }

    /**
     * Status/navigation bar pretas com ícones claros, para o conjunto (barra +
     * terminal) parecer uma tela preta contínua. A status bar do sistema não é
     * removida. O tema {@code TerminalTheme} já define as cores; aqui apenas
     * garantimos a aparência das luzes também no caminho edge-to-edge forçado
     * (API 35+, onde as cores do tema são ignoradas pela plataforma).
     */
    private void applySystemBarAppearance() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            // API 28–29: flags legadas (LIGHT_* já desligadas pelo tema escuro;
            // reafirma para o caso de a ROM iniciar com aparência clara).
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    & ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Sem scrim translúcido da barra de 3 botões sobre o preto.
            getWindow().setNavigationBarContrastEnforced(false);
        }
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

        // Os executáveis do runtime ficam no diretório de bibliotecas nativas
        // do APK, não em filesDir: desde o Android 10 (targetSdk >= 29) o
        // domínio untrusted_app não pode executar arquivos graváveis do app.
        // O diretório nativo é extraído pelo PackageManager (extractNativeLibs)
        // e tem contexto/permissão de execução.
        String nativeLibDir = getApplicationInfo().nativeLibraryDir;
        File runtimeBin = new File(nativeLibDir, "libopencode2.so");
        File runtimeLib = new File(nativeLibDir, "libopentui.so");
        boolean runtimePresent = runtimeBin.isFile() && runtimeLib.isFile();

        // O runtime pinado é arm64-v8a. Só o usamos quando arm64 é a ABI
        // primária. No AVD x86_64, arm64-v8a aparece como ABI secundária via
        // libndk_translation: o shell executa o binário, mas o execve a partir
        // do processo do app é negado com EACCES. Nesse caso, shell de
        // diagnóstico; no telefone arm64 nativo o runtime é usado.
        String[] supportedAbis = android.os.Build.SUPPORTED_ABIS != null
                ? android.os.Build.SUPPORTED_ABIS : new String[0];
        TermBridge.Plan plan = TermBridge.planFor(supportedAbis, runtimePresent);
        // A extração do zip legado não é o caminho de execução (os binários vêm
        // de nativeLibraryDir). Só caímos no diagnóstico se o runtime nativo
        // também estiver ausente; uma falha do zip não pode bloquear o APK atual.
        if (result == RuntimeInstaller.InstallResult.FAILED && !runtimePresent) {
            plan = new TermBridge.Plan(TermBridge.Mode.FALLBACK, "install_failed");
        }
        Log.i(TAG, "plan=" + plan.mode + " reason=" + plan.reason);

        if (mTerm != null) return;
        mTerm = new TermController(this, mContainer);

        String[] argv;
        if (plan.mode == TermBridge.Mode.RUNTIME) {
            // Caminho absoluto direto: TermExec usa execv(3), que não pesquisa
            // PATH. O binário é PIE e está no diretório nativo executável.
            argv = new String[] {runtimeBin.getAbsolutePath()};
            try {
                RuntimeLinks.ensureRipgrepLink(getFilesDir(), new File(nativeLibDir));
            } catch (IOException e) {
                Log.w(TAG, "não foi possível criar o link do rg; a busca pode falhar", e);
            }
        } else {
            argv = new String[] {"/system/bin/sh"};
        }

        mTerm.start(argv, EnvBuilder.build(
                getFilesDir(), nativeLibDir, mStore.provider(), mStore.apiKey()), null);

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

        // Teclado QWERTY próprio (substitui a ExtraKeysView): abaixo do
        // terminal, nunca sobreposto — o TUI usa a última linha para
        // status/entrada e a linha de teclas não pode escondê-la. A altura
        // fixa do teclado entra no layout; o PTY recebe a altura reduzida do
        // EmulatorView no onSizeChanged (mContainer tem weight=1).
        IKeyboardView keyboard = new IKeyboardView(this, mTerm);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        mRoot.addView(keyboard, lp);
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
