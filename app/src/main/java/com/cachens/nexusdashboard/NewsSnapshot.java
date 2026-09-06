package com.cachens.nexusdashboard;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

final class NewsSnapshot {
    final List<NewsItem> items;
    final long fetchedAt;

    NewsSnapshot(List<NewsItem> items, long fetchedAt) {
        this.items = items;
        this.fetchedAt = fetchedAt;
    }

    void save(SharedPreferences preferences) {
        SharedPreferences.Editor editor = preferences.edit()
                .putInt("news_count", items.size())
                .putLong("news_fetched_at", fetchedAt);
        for (int i = 0; i < items.size(); i++) {
            editor.putString("news_source_" + i, items.get(i).source);
            editor.putString("news_title_" + i, items.get(i).title);
        }
        editor.apply();
    }

    static NewsSnapshot load(SharedPreferences preferences) {
        int count = preferences.getInt("news_count", 0);
        if (count == 0) {
            return null;
        }
        List<NewsItem> items = new ArrayList<NewsItem>();
        for (int i = 0; i < count; i++) {
            String source = preferences.getString("news_source_" + i, "");
            String title = preferences.getString("news_title_" + i, "");
            if (title.length() > 0) {
                items.add(new NewsItem(source, title));
            }
        }
        return items.isEmpty() ? null
                : new NewsSnapshot(items, preferences.getLong("news_fetched_at", 0));
    }
}
