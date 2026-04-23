package com.friday.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST = 100;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private LinearLayout chatContainer;
    private ScrollView scrollView;
    private ImageButton micButton;
    private EditText textInput;
    private TextView statusText;
    private SharedPreferences prefs;
    private boolean isListening = false;
    private boolean isSpeaking = false;
    private CommandProcessor commandProcessor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("friday_prefs", MODE_PRIVATE);

        // Check if setup done
        if (!prefs.getBoolean("setup_done", false)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        initViews();
        initTTS();
        initSpeechRecognizer();
        commandProcessor = new CommandProcessor(this, prefs);
        requestPermissions();
    }

    private void initViews() {
        chatContainer = findViewById(R.id.chatContainer);
        scrollView = findViewById(R.id.scrollView);
        micButton = findViewById(R.id.micButton);
        textInput = findViewById(R.id.textInput);
        statusText = findViewById(R.id.statusText);

        micButton.setOnClickListener(v -> {
            if (isListening) stopListening();
            else startListening();
        });

        findViewById(R.id.sendButton).setOnClickListener(v -> {
            String text = textInput.getText().toString().trim();
            if (!text.isEmpty()) {
                textInput.setText("");
                processInput(text);
            }
        });

        findViewById(R.id.settingsButton).setOnClickListener(v -> {
            startActivity(new Intent(this, SetupActivity.class));
        });
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.setPitch(1.1f);
                tts.setSpeechRate(0.95f);
                // Try to use female voice
                for (java.util.Set<android.speech.tts.Voice> voices = tts.getVoices() != null
                        ? tts.getVoices() : new java.util.HashSet<>();
                     false; ) {}
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        isSpeaking = true;
                        runOnUiThread(() -> setStatus("Speaking…"));
                    }
                    @Override public void onDone(String id) {
                        isSpeaking = false;
                        runOnUiThread(() -> setStatus("Ready — tap mic to speak"));
                    }
                    @Override public void onError(String id) { isSpeaking = false; }
                });
                // Greet on startup
                String name = prefs.getString("user_name", "Boss");
                speak("Hey " + name + "! Friday online. How can I help you?");
            }
        });
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_LONG).show();
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) {
                isListening = true;
                runOnUiThread(() -> {
                    micButton.setImageResource(R.drawable.ic_mic_active);
                    setStatus("Listening…");
                });
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rms) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {
                isListening = false;
                runOnUiThread(() -> micButton.setImageResource(R.drawable.ic_mic));
            }
            @Override public void onError(int error) {
                isListening = false;
                runOnUiThread(() -> {
                    micButton.setImageResource(R.drawable.ic_mic);
                    setStatus("Ready — tap mic to speak");
                });
            }
            @Override public void onResults(Bundle results) {
                isListening = false;
                runOnUiThread(() -> micButton.setImageResource(R.drawable.ic_mic));
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    runOnUiThread(() -> processInput(text));
                }
            }
            @Override public void onPartialResults(Bundle partial) {
                ArrayList<String> partial_matches = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial_matches != null && !partial_matches.isEmpty()) {
                    runOnUiThread(() -> setStatus("Heard: " + partial_matches.get(0)));
                }
            }
            @Override public void onEvent(int type, Bundle params) {}
        });
    }

    public void startListening() {
        if (isSpeaking) { tts.stop(); isSpeaking = false; }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechRecognizer.startListening(intent);
    }

    public void stopListening() {
        speechRecognizer.stopListening();
        isListening = false;
        micButton.setImageResource(R.drawable.ic_mic);
        setStatus("Ready — tap mic to speak");
    }

    public void processInput(String text) {
        addMessage(text, true); // user message
        setStatus("Thinking…");
        commandProcessor.process(text, new CommandProcessor.Callback() {
            @Override public void onResult(String response) {
                runOnUiThread(() -> {
                    addMessage(response, false);
                    speak(response);
                });
            }
        });
    }

    public void speak(String text) {
        if (tts == null) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString());
    }

    public void addMessage(String text, boolean isUser) {
        View msg = getLayoutInflater().inflate(
                isUser ? R.layout.message_user : R.layout.message_friday, chatContainer, false);
        TextView tv = msg.findViewById(R.id.messageText);
        tv.setText(text);
        chatContainer.addView(msg);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    public void setStatus(String text) {
        statusText.setText(text);
    }

    private void requestPermissions() {
        String[] perms = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        };
        List<String> needed = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed.add(p);
        }
        if (!needed.isEmpty())
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) speechRecognizer.destroy();
    }
  }
              
