package net.swigglesoft.shackbrowse;

import android.util.Log;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DebugLogger {
    private static final int MAX_MESSAGE_COUNT = 200;
    private static ArrayList<String> messages = new ArrayList<>();

    public static void i(String tag, String message) {
        Log.i(tag, message);
        addMessage(tag, "INFO: " + message);
    }

    public static void d(String tag, String message) {
        d(tag, message, false);
    }

    public static void d(String tag, String message, boolean logToCrashlytics) {
        Log.d(tag, message);
        addMessage(tag, "DEBUG: " + message);
        if(logToCrashlytics) {
            FirebaseCrashlytics.getInstance().log(message);
        }
    }

    public static void e(String tag, String message, Exception e) {
        Log.e(tag, message + ", " + e.getMessage(), e);
        addMessage(tag, "ERROR: " + message + ", error message: " + e.getMessage());
    }

    private static void addMessage(String tag, String message) {
        messages.add((new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())) + " - " + tag + ": " + message);
        while(messages.size() > MAX_MESSAGE_COUNT) {
            messages.remove(0);
        }
    }

    public static ArrayList<String> getMessages() {
        return messages;
    }
}
