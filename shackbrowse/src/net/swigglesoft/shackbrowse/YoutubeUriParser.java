package net.swigglesoft.shackbrowse;

import android.net.Uri;

import java.util.List;

public class YoutubeUriParser {
    private String uri = null;

    public YoutubeUriParser(String href) {
        uri = href;
    }

    public boolean isYoutube() {
        Uri parsedUri = Uri.parse(uri);
        String host = parsedUri.getHost().toLowerCase();
        if (uri.toLowerCase().contains("/clip/")) {
            return false;
        }
        return (host.equals("youtu.be") || host.equals("www.youtube.com") || host.equals("youtube.com") || host.equals("m.youtube.com"));
    }

    public String getYoutubeId() {
        if (!isYoutube()) {
            return null;
        }

        Uri parsedUri = Uri.parse(uri);
        String host = parsedUri.getHost().toLowerCase();

        List<String> segments = parsedUri.getPathSegments();
        int segSize = segments.size();
        if (host.equals("youtu.be") && segSize == 1) {
            return segments.get(0);
        }

        if (segSize == 1 && segments.get(0).equals("watch")) {
            String vParam = parsedUri.getQueryParameter("v");
            if (vParam != null) {
                return vParam;
            }
            return null;
        }

        if (segSize == 2) {
            String s1 = segments.get(0);
            if (s1.equals("watch") || s1.equals("shorts") || s1.equals("v") || s1.equals("live") || s1.equals("embed")) {
                return segments.get(1);
            }
        }

        return null;
    }

    public int getYoutubeTime() {
        if (!isYoutube()) {
            return 0;
        }

        Uri parsedUri = Uri.parse(uri);
        String tParam = parsedUri.getQueryParameter("t");
        if (tParam == null) {
            return 0;
        }

        int time = 0;
        try {
            time = Integer.parseInt(tParam);
        } catch (Exception e) {
        }
        return time;
    }
}
