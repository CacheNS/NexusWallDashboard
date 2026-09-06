package com.cachens.nexusdashboard;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

final class NewsClient {
    private static final int ITEMS_PER_SOURCE = 5;

    private NewsClient() {
    }

    static NewsSnapshot fetch() throws Exception {
        List<NewsItem> items = new ArrayList<NewsItem>();
        items.addAll(fetchFeed("https://www.021.rs/rss/all", "021.rs"));
        items.addAll(fetchFeed("https://n1info.rs/feed/", "N1"));
        return new NewsSnapshot(items, System.currentTimeMillis());
    }

    private static List<NewsItem> fetchFeed(String address, String source) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        LegacyTls.configure(connection);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml");
        connection.setRequestProperty("User-Agent", "NexusWallDashboard/1.0");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new java.io.IOException(source + " returned HTTP " + status);
            }
            return parse(connection.getInputStream(), source);
        } finally {
            connection.disconnect();
        }
    }

    private static List<NewsItem> parse(InputStream stream, String source) throws Exception {
        List<NewsItem> items = new ArrayList<NewsItem>();
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(stream, "UTF-8");
        boolean insideItem = false;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT && items.size() < ITEMS_PER_SOURCE) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("item".equalsIgnoreCase(name)) {
                    insideItem = true;
                } else if (insideItem && "title".equalsIgnoreCase(name)) {
                    String title = parser.nextText().trim();
                    if (title.length() > 0) {
                        items.add(new NewsItem(source, title));
                    }
                }
            } else if (event == XmlPullParser.END_TAG && "item".equalsIgnoreCase(parser.getName())) {
                insideItem = false;
            }
            event = parser.next();
        }
        stream.close();
        return items;
    }
}
