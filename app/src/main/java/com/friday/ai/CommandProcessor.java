package com.friday.ai;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.database.Cursor;
import android.telephony.SmsManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandProcessor {

    public interface Callback {
        void onResult(String response);
    }

    private final Context ctx;
    private final SharedPreferences prefs;
    private final JSONArray conversationHistory;

    public CommandProcessor(Context ctx, SharedPreferences prefs) {
        this.ctx = ctx;
        this.prefs = prefs;
        this.conversationHistory = new JSONArray();
    }

    public void process(String input, Callback cb) {
        String cmd = input.toLowerCase().trim();
        String name = prefs.getString("user_name", "Boss");

        // ── TIME ──
        if (cmd.matches(".*(what.?s the time|current time|what time is it).*")) {
            String time = android.text.format.DateFormat.getTimeFormat(ctx).format(new java.util.Date());
            cb.onResult("It's " + time + ", " + name + ".");
            return;
        }

        // ── DATE ──
        if (cmd.matches(".*(what.?s today|what day|today.?s date|current date).*")) {
            String date = android.text.format.DateFormat.getLongDateFormat(ctx).format(new java.util.Date());
            cb.onResult("Today is " + date + ".");
            return;
        }

        // ── CALL ──
        Pattern callPattern = Pattern.compile("call (.+)", Pattern.CASE_INSENSITIVE);
        Matcher callMatcher = callPattern.matcher(input);
        if (callMatcher.find()) {
            String target = callMatcher.group(1).trim();
            String number = lookupContact(target);
            if (number != null) {
                makeCall(number);
                cb.onResult("Calling " + target + " now!");
            } else {
                // Try as number
                String digits = target.replaceAll("[^0-9+]", "");
                if (digits.length() >= 7) {
                    makeCall(digits);
                    cb.onResult("Calling " + digits + "!");
                } else {
                    cb.onResult("I couldn't find " + target + " in your contacts.");
                }
            }
            return;
        }

        // ── WHATSAPP MESSAGE ──
        Pattern waPattern = Pattern.compile("(?:whatsapp|send whatsapp|message on whatsapp).+?(?:to\\s+)?(.+?)(?:\\s+(?:and say|saying|message|text)\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
        Matcher waMatcher = waPattern.matcher(input);
        if (cmd.contains("whatsapp")) {
            // Extract contact and message
            Pattern wa2 = Pattern.compile("(?:whatsapp|message)\\s+(.+?)\\s+(?:and say|saying|message|and send|and write|send)\\s+(.+)", Pattern.CASE_INSENSITIVE);
            Matcher wa2m = wa2.matcher(input);
            if (wa2m.find()) {
                String contact = wa2m.group(1).trim();
                String message = wa2m.group(2).trim();
                String number = lookupContact(contact);
                if (number == null) number = contact.replaceAll("[^0-9+]", "");
                openWhatsApp(number, message);
                cb.onResult("Opening WhatsApp to send \"" + message + "\" to " + contact + "!");
            } else {
                openWhatsApp(null, null);
                cb.onResult("Opening WhatsApp!");
            }
            return;
        }

        // ── SMS ──
        Pattern smsPattern = Pattern.compile("(?:text|sms|send sms|send text)\\s+(.+?)\\s+(?:and say|saying|message|send)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher smsMatcher = smsPattern.matcher(input);
        if (smsMatcher.find()) {
            String contact = smsMatcher.group(1).trim();
            String message = smsMatcher.group(2).trim();
            String number = lookupContact(contact);
            if (number == null) number = contact.replaceAll("[^0-9+]", "");
            sendSMS(number, message);
            cb.onResult("Sending SMS to " + contact + ": \"" + message + "\"");
            return;
        }

        // ── SET ALARM ──
        Pattern alarmPattern = Pattern.compile("(?:set alarm|alarm|wake me).*?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE);
        Matcher alarmMatcher = alarmPattern.matcher(input);
        if (alarmMatcher.find()) {
            int hour = Integer.parseInt(alarmMatcher.group(1));
            int minute = alarmMatcher.group(2) != null ? Integer.parseInt(alarmMatcher.group(2)) : 0;
            String ampm = alarmMatcher.group(3);
            if (ampm != null && ampm.equalsIgnoreCase("pm") && hour < 12) hour += 12;
            if (ampm != null && ampm.equalsIgnoreCase("am") && hour == 12) hour = 0;
            setAlarm(hour, minute);
            cb.onResult("Alarm set for " + alarmMatcher.group(1) + ":" + String.format("%02d", minute) + (ampm != null ? " " + ampm.toUpperCase() : "") + "!");
            return;
        }

        // ── REMINDER IN X MINUTES ──
        Pattern reminderPattern = Pattern.compile("remind me.*?(\\d+)\\s*(minute|hour|second)", Pattern.CASE_INSENSITIVE);
        Matcher reminderMatcher = reminderPattern.matcher(input);
        if (reminderMatcher.find()) {
            int val = Integer.parseInt(reminderMatcher.group(1));
            String unit = reminderMatcher.group(2).toLowerCase();
            // Extract what to remind about
            Pattern aboutPattern = Pattern.compile("remind me (?:to |about )?(.+?)(?:\\s+in\\s+|$)", Pattern.CASE_INSENSITIVE);
            Matcher aboutMatcher = aboutPattern.matcher(input);
            String subject = aboutMatcher.find() ? aboutMatcher.group(1) : "reminder";
            setTimedReminder(val, unit, subject);
            cb.onResult("Got it! I'll remind you about \"" + subject + "\" in " + val + " " + unit + "(s).");
            return;
        }

        // ── OPEN APP ──
        Pattern openPattern = Pattern.compile("(?:open|launch|start)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher openMatcher = openPattern.matcher(input);
        if (openMatcher.find() && !cmd.contains("whatsapp")) {
            String appName = openMatcher.group(1).trim();
            if (openApp(appName)) {
                cb.onResult("Opening " + appName + "!");
            } else {
                cb.onResult("I couldn't find " + appName + " on your phone. Make sure it's installed!");
            }
            return;
        }

        // ── GOOGLE SEARCH ──
        Pattern searchPattern = Pattern.compile("(?:search|google|find|look up)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher searchMatcher = searchPattern.matcher(input);
        if (searchMatcher.find()) {
            String query = searchMatcher.group(1).trim();
            openUrl("https://www.google.com/search?q=" + Uri.encode(query));
            cb.onResult("Searching Google for \"" + query + "\"!");
            return;
        }

        // ── YOUTUBE ──
        if (cmd.contains("youtube") || cmd.startsWith("play ")) {
            Pattern ytPattern = Pattern.compile("(?:play|search youtube for|youtube)\\s+(.+)", Pattern.CASE_INSENSITIVE);
            Matcher ytMatcher = ytPattern.matcher(input);
            if (ytMatcher.find()) {
                String query = ytMatcher.group(1).trim();
                openUrl("https://www.youtube.com/results?search_query=" + Uri.encode(query));
                cb.onResult("Searching YouTube for \"" + query + "\"!");
            } else {
                openUrl("https://youtube.com");
                cb.onResult("Opening YouTube!");
            }
            return;
        }

        // ── WEATHER ──
        if (cmd.contains("weather") || cmd.contains("temperature") || cmd.contains("forecast")) {
            openUrl("https://www.google.com/search?q=weather+today");
            cb.onResult("Opening weather for you! For precise results, give me a weather API key in settings.");
            return;
        }

        // ── NEWS ──
        if (cmd.contains("news") || cmd.contains("headlines")) {
            openUrl("https://news.google.com");
            cb.onResult("Opening Google News!");
            return;
        }

        // ── MAPS / NAVIGATE ──
        Pattern mapsPattern = Pattern.compile("(?:navigate to|directions to|take me to|go to|maps?)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher mapsMatcher = mapsPattern.matcher(input);
        if (mapsMatcher.find()) {
            String place = mapsMatcher.group(1).trim();
            openUrl("https://maps.google.com/?q=" + Uri.encode(place));
            cb.onResult("Opening Maps to " + place + "!");
            return;
        }

        // ── INSTAGRAM ──
        if (cmd.contains("instagram")) { openApp("instagram"); cb.onResult("Opening Instagram!"); return; }

        // ── TWITTER / X ──
        if (cmd.contains("twitter") || cmd.contains("x app")) { openApp("twitter"); cb.onResult("Opening X!"); return; }

        // ── SPOTIFY ──
        if (cmd.contains("spotify")) {
            if (!openApp("spotify")) openUrl("https://open.spotify.com");
            cb.onResult("Opening Spotify!");
            return;
        }

        // ── GMAIL ──
        if (cmd.contains("gmail") || cmd.contains("email")) { openApp("gmail"); cb.onResult("Opening Gmail!"); return; }

        // ── BATTERY ──
        if (cmd.contains("battery")) {
            android.os.BatteryManager bm = (android.os.BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            int pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
            cb.onResult("Battery is at " + pct + "%.");
            return;
        }

        // ── AI FALLBACK — Ask Gemini ──
        askGemini(input, cb);
    }

    // ─── HELPERS ───────────────────────────────────────────────

    private void makeCall(String number) {
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    private void openWhatsApp(String number, String message) {
        try {
            Intent intent;
            if (number != null && !number.isEmpty()) {
                String url = "https://api.whatsapp.com/send?phone=" + number +
                        (message != null ? "&text=" + Uri.encode(message) : "");
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            } else {
                intent = ctx.getPackageManager().getLaunchIntentForPackage("com.whatsapp");
                if (intent == null) intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me"));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            openUrl("https://wa.me/" + (number != null ? number : ""));
        }
    }

    private void sendSMS(String number, String message) {
        try {
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(number, null, message, null, null);
            Toast.makeText(ctx, "SMS sent!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // Fallback: open SMS app
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number));
            intent.putExtra("sms_body", message);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        }
    }

    private void setAlarm(int hour, int minute) {
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
        intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    private void setTimedReminder(int val, String unit, String subject) {
        long ms = unit.startsWith("s") ? val * 1000L :
                  unit.startsWith("m") ? val * 60_000L : val * 3_600_000L;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ctx, ReminderReceiver.class);
        intent.putExtra("subject", subject);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) System.currentTimeMillis(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ms, pi);
    }

    private boolean openApp(String name) {
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String nameLower = name.toLowerCase().trim();
        for (ApplicationInfo app : apps) {
            String label = pm.getApplicationLabel(app).toString().toLowerCase();
            if (label.contains(nameLower) || app.packageName.toLowerCase().contains(nameLower)) {
                Intent launch = pm.getLaunchIntentForPackage(app.packageName);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(launch);
                    return true;
                }
            }
        }
        return false;
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    private String lookupContact(String name) {
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(name));
            Cursor cursor = ctx.getContentResolver().query(uri,
                    new String[]{ContactsContract.PhoneLookup.NUMBER}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String number = cursor.getString(0);
                cursor.close();
                return number;
            }
            // Try fuzzy search
            Cursor c2 = ctx.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                                 ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                    new String[]{"%" + name + "%"}, null);
            if (c2 != null && c2.moveToFirst()) {
                String number = c2.getString(0);
                c2.close();
                return number;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // ─── GEMINI AI ─────────────────────────────────────────────

    private void askGemini(String userText, Callback cb) {
        String apiKey = prefs.getString("gemini_key", "");
        if (apiKey.isEmpty()) {
            cb.onResult("Please add your Gemini API key in Settings so I can answer that!");
            return;
        }
        String userName = prefs.getString("user_name", "Boss");

        new Thread(() -> {
            try {
                // Build conversation
                JSONObject newMsg = new JSONObject();
                newMsg.put("role", "user");
                JSONArray parts = new JSONArray();
                parts.put(new JSONObject().put("text", userText));
                newMsg.put("parts", parts);
                conversationHistory.put(newMsg);

                // System prompt
                String systemPrompt = "You are Friday (F.R.I.D.A.Y), a warm, witty, intelligent female AI assistant running on " + userName + "'s Android phone. " +
                        "Speak naturally and conversationally. Be playful, caring, and confident. " +
                        "Keep responses SHORT and spoken-friendly — 1 to 3 sentences. No bullet points. " +
                        "Call the user " + userName + " occasionally. Current time: " + new java.util.Date();

                // Build request
                JSONObject body = new JSONObject();
                body.put("system_instruction", new JSONObject().put("parts",
                        new JSONArray().put(new JSONObject().put("text", systemPrompt))));
                body.put("contents", conversationHistory);
                body.put("generationConfig", new JSONObject()
                        .put("maxOutputTokens", 300)
                        .put("temperature", 0.88));

                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code == 200 ? conn.getInputStream() : conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject response = new JSONObject(sb.toString());
                String text = response.getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content")
                        .getJSONArray("parts").getJSONObject(0).getString("text");

                // Add to history
                JSONObject assistantMsg = new JSONObject();
                assistantMsg.put("role", "model");
                JSONArray aParts = new JSONArray();
                aParts.put(new JSONObject().put("text", text));
                assistantMsg.put("parts", aParts);
                conversationHistory.put(assistantMsg);

                cb.onResult(text);

            } catch (Exception e) {
                e.printStackTrace();
                cb.onResult("Sorry, I couldn't connect to Gemini. Check your internet and API key!");
            }
        }).start();
    }
          }
          
