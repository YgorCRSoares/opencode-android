package dev.opencode.terminal;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

public class SetupActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        Spinner spinner = (Spinner) findViewById(R.id.provider_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item,
                new String[] { "anthropic", "openai", "google", "openrouter" });
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        final EditText key = (EditText) findViewById(R.id.api_key);
        Button save = (Button) findViewById(R.id.save);
        Button skip = (Button) findViewById(R.id.skip);

        final SettingsStore store = new SettingsStore.PrefsSettingsStore(this);

        save.setOnClickListener(v -> {
            String provider = (String) spinner.getSelectedItem();
            store.save(provider, key.getText().toString());
            setResult(RESULT_OK);
            finish();
        });
        skip.setOnClickListener(v -> {
            store.markSkipped();
            setResult(RESULT_CANCELED);
            finish();
        });
    }
}
