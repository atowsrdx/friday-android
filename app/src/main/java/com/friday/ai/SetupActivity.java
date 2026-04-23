package com.friday.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SetupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        SharedPreferences prefs = getSharedPreferences("friday_prefs", MODE_PRIVATE);
        EditText keyInput = findViewById(R.id.geminiKeyInput);
        EditText nameInput = findViewById(R.id.userNameInput);
        EditText wakeInput = findViewById(R.id.wakeWordInput);
        Button saveBtn = findViewById(R.id.saveButton);

        // Pre-fill saved values
        String savedKey = prefs.getString("gemini_key", "");
        if (!savedKey.isEmpty()) keyInput.setHint("API key saved — leave blank to keep");
        nameInput.setText(prefs.getString("user_name", ""));
        wakeInput.setText(prefs.getString("wake_word", "friday"));

        saveBtn.setOnClickListener(v -> {
            String key = keyInput.getText().toString().trim();
            String name = nameInput.getText().toString().trim();
            String wake = wakeInput.getText().toString().trim().toLowerCase();

            String finalKey = key.isEmpty() ? prefs.getString("gemini_key", "") : key;
            if (finalKey.isEmpty()) {
                Toast.makeText(this, "Please enter your Gemini API key!", Toast.LENGTH_LONG).show();
                return;
            }

            prefs.edit()
                .putString("gemini_key", finalKey)
                .putString("user_name", name.isEmpty() ? "Boss" : name)
                .putString("wake_word", wake.isEmpty() ? "friday" : wake)
                .putBoolean("setup_done", true)
                .apply();

            Toast.makeText(this, "Saved! Friday is ready.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
