package com.example.flymestatusbarsizer;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Shared RGB palette. Applied colors enter history; explicit pins are never evicted. */
final class ColorPaletteHistory {
    static final int RECENT_LIMIT = 8;
    private final SharedPreferences prefs;
    private final List<Integer> pinned = new ArrayList<>();
    private final List<Integer> recent = new ArrayList<>();

    ColorPaletteHistory(SharedPreferences prefs) {
        this.prefs = prefs;
        String saved = SettingsStore.readString(prefs, SettingsStore.KEY_COLOR_PICKER_PALETTE, "{}");
        try {
            JSONObject json = new JSONObject(saved);
            readColors(json.optJSONArray("pinned"), pinned);
            readColors(json.optJSONArray("recent"), recent);
            recent.removeAll(pinned);
            trimRecent();
        } catch (JSONException ignored) {
            // Old or malformed imported settings start with an empty palette.
        }
    }

    List<Integer> pinnedColors() { return Collections.unmodifiableList(pinned); }
    List<Integer> recentColors() { return Collections.unmodifiableList(recent); }
    boolean isPinned(int color) { return pinned.contains(color | 0xFF000000); }

    void recordApplied(int color) {
        int rgb = color | 0xFF000000;
        recent.remove(Integer.valueOf(rgb));
        if (!isPinned(rgb)) recent.add(0, rgb);
        trimRecent();
        save();
    }

    void togglePin(int color) {
        int rgb = color | 0xFF000000;
        if (pinned.remove(Integer.valueOf(rgb))) {
            recordApplied(rgb);
        } else {
            recent.remove(Integer.valueOf(rgb));
            pinned.add(0, rgb);
            save();
        }
    }

    private void trimRecent() {
        while (recent.size() > RECENT_LIMIT) recent.remove(recent.size() - 1);
    }

    private static void readColors(JSONArray values, List<Integer> target) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) {
            String text = values.optString(i, "");
            if (!text.matches("#[0-9a-fA-F]{6}")) continue;
            int rgb = 0xFF000000 | Integer.parseInt(text.substring(1), 16);
            if (!target.contains(rgb)) target.add(rgb);
        }
    }

    private void save() {
        try {
            JSONObject json = new JSONObject();
            json.put("pinned", colorsJson(pinned));
            json.put("recent", colorsJson(recent));
            prefs.edit().putString(SettingsStore.KEY_COLOR_PICKER_PALETTE, json.toString()).apply();
        } catch (JSONException e) {
            throw new IllegalStateException("Cannot encode color palette", e);
        }
    }

    private static JSONArray colorsJson(List<Integer> colors) {
        JSONArray result = new JSONArray();
        for (int color : colors) result.put(String.format(Locale.US, "#%06X", color & 0xFFFFFF));
        return result;
    }
}
