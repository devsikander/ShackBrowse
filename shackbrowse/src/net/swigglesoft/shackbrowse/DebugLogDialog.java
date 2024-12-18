package net.swigglesoft.shackbrowse;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.webkit.WebView;

import java.util.List;

public class DebugLogDialog {
    private final Context context;

    public DebugLogDialog(Context context) {
        this(context, PreferenceManager.getDefaultSharedPreferences(context));
    }

    public DebugLogDialog(Context context, SharedPreferences sp) {
        this.context = context;
    }

    public AlertDialog getDialog() {
        WebView wv = new WebView(this.context);
        wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        wv.setBackgroundColor(0);
        wv.loadDataWithBaseURL(null, this.getLog(), "text/html", "UTF-8",
                null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this.context);
        builder.setTitle(R.string.debuglogdialog_title)
                .setView(wv)
                .setCancelable(false)
                .setPositiveButton(
                        context.getResources().getString(
                                R.string.changelog_ok_button),
                        (dialog, which) -> {
                        });
        return builder.create();
    }

    private String getLog() {
        StringBuffer sb = new StringBuffer();
        List<String> messages = DebugLogger.getMessages();

        sb.append("<p style=\"color:white;\">");
        for (int i = 0; i < messages.size(); i++) {
            sb.append("- " + messages.get(i) + "<br/>");
        }
        sb.append("</p>");

        return sb.toString();
    }
}