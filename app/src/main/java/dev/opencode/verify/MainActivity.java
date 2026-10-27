package dev.opencode.verify;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** App mínimo para validar o toolchain Android (SDK + Gradle + AGP + JDK 21). */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Toolchain OK — opencode-android\nBuild validado\nAPI "
                + android.os.Build.VERSION.SDK_INT + " ("
                + android.os.Build.VERSION.RELEASE + ")");
        tv.setTextColor(0xFF212121);
        // 400px de topo: a ActionBar cobre y=0..283 (content em overlay)
        tv.setPadding(64, 400, 64, 64);
        setContentView(tv);
    }
}
