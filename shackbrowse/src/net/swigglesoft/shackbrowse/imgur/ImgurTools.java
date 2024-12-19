package net.swigglesoft.shackbrowse.imgur;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Base64;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import net.swigglesoft.shackbrowse.logging.DebugLogger;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Scanner;

public class ImgurTools {
    private static final String TAG = ImgurTools.class.getSimpleName();
    private static final String UPLOAD_URL = "https://api.imgur.com/3/image";

    public static class RefreshAccessTokenTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... params) {
            String accessToken = ImgurAuthorization.getInstance().requestNewAccessToken();
            if (!TextUtils.isEmpty(accessToken)) {
                DebugLogger.i(TAG, "Got new access token");
            } else {
                DebugLogger.i(TAG, "Could not get new access token");
            }
            return accessToken;
        }
    }

    protected static JSONObject onInput(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        Scanner scanner = new Scanner(in);
        while (scanner.hasNext()) {
            sb.append(scanner.next());
        }

        DebugLogger.i(TAG, "RESPONSE: " + sb);

        JSONObject root = new JSONObject(sb.toString());
        String id = root.getJSONObject("data").getString("id");
        String deletehash = root.getJSONObject("data").getString("deletehash");

        DebugLogger.i(TAG, "new imgur url: http://imgur.com/" + id + " (delete hash: " + deletehash + ")");
        return root;
    }

    private static int copy(InputStream input, OutputStreamWriter output) throws IOException {
        byte[] imageBytes = IOUtils.toByteArray(input);
        String encodedImageData = Base64.encodeToString(imageBytes, Base64.DEFAULT);
        String encodedData = URLEncoder.encode(encodedImageData, "UTF-8");
        output.write(encodedData);
        return encodedData.length();
    }

    public static ImgurUploadResponse uploadImageToImgur(InputStream imageIn) {
        HttpURLConnection conn = null;
        InputStream responseIn = null;
        ImgurUploadResponse responseObject = null;

        try {
            DebugLogger.d(TAG, "Uploading to " + UPLOAD_URL);
            conn = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");

            String imageStartData = URLEncoder.encode("image", "UTF-8") + "=";
            ImgurAuthorization.getInstance().addToHttpURLConnection(conn);

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(imageStartData);
            copy(imageIn, wr);
            wr.flush();
            wr.close();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                DebugLogger.d(TAG, "Imgur upload received HTTP OK status");
                responseIn = conn.getInputStream();
                responseObject = new ImgurUploadResponse(true, onInput(responseIn), null, null);
            } else {
                String responseCode = String.valueOf(conn.getResponseCode());
                DebugLogger.i(TAG, "responseCode=" + responseCode);
                responseIn = conn.getErrorStream();
                StringBuilder sb = new StringBuilder();
                Scanner scanner = new Scanner(responseIn);
                while (scanner.hasNext()) {
                    sb.append(scanner.next());
                }
                String errorMessage = "uploadImageToImgur error responseCode: " + responseCode + ",response: " + sb.toString();
                DebugLogger.i(TAG, errorMessage);
                FirebaseCrashlytics.getInstance().log(errorMessage);
                responseObject = new ImgurUploadResponse(false, null, errorMessage, null);
            }
        } catch (Exception ex) {
            DebugLogger.e(TAG, "Error during POST", ex);
            FirebaseCrashlytics.getInstance().recordException(ex);
            responseObject = new ImgurUploadResponse(false, null, "Error during POST: " + ex.getMessage(), ex);
        } finally {
            try {
                responseIn.close();
            } catch (Exception ignore) {
            }
            try {
                conn.disconnect();
            } catch (Exception ignore) {
            }
            try {
                imageIn.close();
            } catch (Exception ignore) {
            }
        }
        return responseObject;
    }
}
