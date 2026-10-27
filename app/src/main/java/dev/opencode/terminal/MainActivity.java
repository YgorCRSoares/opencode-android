package dev.opencode.terminal;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

/** Placeholder da UI inicial — reescrito na Task 6. */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("OpenCode Terminal\nABIs suportadas:\n"
                + String.join("\n", Build.SUPPORTED_ABIS));
        tv.setTextColor(0xFF212121);
        // 400px de topo: a ActionBar cobre y=0..283 (content em overlay)
        tv.setPadding(64, 400, 64, 64);
        setContentView(tv);
    }
}
