package com.example.flymestatusbarsizer.ui;

import com.example.flymestatusbarsizer.config.SettingsStore;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class ColorPaletteHistoryTest {
    private SharedPreferences prefs;

    @Before public void setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("palette-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @Test public void appliedColorsAreSharedAcrossDialogsAndOpeningDoesNotChangeHistory() {
        ColorPaletteHistory firstDialog = new ColorPaletteHistory(prefs);
        assertTrue(firstDialog.recentColors().isEmpty());
        assertFalse(prefs.contains(SettingsStore.KEY_COLOR_PICKER_PALETTE));
        firstDialog.recordApplied(0xFF112233);
        firstDialog.recordApplied(0xFF445566);
        ColorPaletteHistory otherStateDialog = new ColorPaletteHistory(prefs);
        assertEquals(Arrays.asList(0xFF445566, 0xFF112233), otherStateDialog.recentColors());
        otherStateDialog.recordApplied(0x00112233);
        assertEquals(Arrays.asList(0xFF112233, 0xFF445566),
                new ColorPaletteHistory(prefs).recentColors());
    }

    @Test public void pinnedColorsSurviveHistoryEvictionAndDoNotDuplicateRecentColors() {
        ColorPaletteHistory history = new ColorPaletteHistory(prefs);
        history.recordApplied(0xFF123456);
        history.togglePin(0xFF123456);
        for (int i = 0; i < 20; i++) history.recordApplied(0xFF000000 | i);
        history = new ColorPaletteHistory(prefs);
        assertEquals(Arrays.asList(0xFF123456), history.pinnedColors());
        assertEquals(8, history.recentColors().size());
        assertEquals(Integer.valueOf(0xFF000013), history.recentColors().get(0));
        assertEquals(Integer.valueOf(0xFF00000C), history.recentColors().get(7));
        history.recordApplied(0xFF123456);
        assertFalse(history.recentColors().contains(0xFF123456));
        assertEquals(8, history.recentColors().size());
    }

    @Test public void pinsKeepTheirOrderWhenReusedAndUnpinReturnsColorToRecent() {
        ColorPaletteHistory history = new ColorPaletteHistory(prefs);
        history.togglePin(0xFF112233);
        history.togglePin(0xFF445566);
        history.recordApplied(0xFF112233);
        assertEquals(Arrays.asList(0xFF445566, 0xFF112233), history.pinnedColors());
        for (int i = 0; i < 8; i++) history.recordApplied(0xFF000000 | i);
        history.togglePin(0x00112233);
        history = new ColorPaletteHistory(prefs);
        assertFalse(history.isPinned(0xFF112233));
        assertEquals(Arrays.asList(0xFF445566), history.pinnedColors());
        assertEquals(Integer.valueOf(0xFF112233), history.recentColors().get(0));
        assertEquals(8, history.recentColors().size());
    }

    @Test public void invalidImportedEntriesDoNotHideValidColorsOrCreateDuplicates() {
        prefs.edit().putString(SettingsStore.KEY_COLOR_PICKER_PALETTE,
                "{\"pinned\":[\"#123abc\",\"#123ABC\",\"bad\",null],"
                + "\"recent\":[\"#123ABC\",\"#445566\",\"#445566\",\"#FF112233\",12]}").commit();
        ColorPaletteHistory history = new ColorPaletteHistory(prefs);
        assertEquals(Arrays.asList(0xFF123ABC), history.pinnedColors());
        assertEquals(Arrays.asList(0xFF445566), history.recentColors());
        prefs.edit().putString(SettingsStore.KEY_COLOR_PICKER_PALETTE, "broken json").commit();
        history = new ColorPaletteHistory(prefs);
        assertTrue(history.pinnedColors().isEmpty());
        assertTrue(history.recentColors().isEmpty());
        history.recordApplied(0xFF123456);
        assertEquals(Arrays.asList(0xFF123456), new ColorPaletteHistory(prefs).recentColors());
    }

    @Test public void backupRoundTripAndResetUseTheRegisteredPreference() {
        String key = SettingsStore.KEY_COLOR_PICKER_PALETTE;
        assertTrue(Arrays.asList(SettingsStore.STRING_KEYS).contains(key));
        assertTrue(SettingsStore.includeInBackup(key));
        ColorPaletteHistory history = new ColorPaletteHistory(prefs);
        history.togglePin(0xFF112233);
        history.recordApplied(0xFF445566);
        String exported = SettingsStore.readString(prefs, key, SettingsStore.defaultString(key));
        prefs.edit().clear().putString(key, exported).commit();
        history = new ColorPaletteHistory(prefs);
        assertEquals(Arrays.asList(0xFF112233), history.pinnedColors());
        assertEquals(Arrays.asList(0xFF445566), history.recentColors());
        prefs.edit().putString(key, SettingsStore.defaultString(key)).commit();
        history = new ColorPaletteHistory(prefs);
        assertTrue(history.pinnedColors().isEmpty());
        assertTrue(history.recentColors().isEmpty());
    }
}
