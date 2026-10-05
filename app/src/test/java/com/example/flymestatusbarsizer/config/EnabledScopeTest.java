package com.example.flymestatusbarsizer.config;

import android.os.RemoteException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.libxposed.service.XposedService;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class EnabledScopeTest {
    private Field serviceField;
    private Object previousService;

    @Before public void setUp() throws Exception {
        serviceField = RemoteSettingsSync.class.getDeclaredField("xposedService");
        serviceField.setAccessible(true);
        previousService = serviceField.get(null);
        serviceField.set(null, null);
    }

    @After public void tearDown() throws Exception {
        serviceField.set(null, previousService);
    }

    @Test public void disconnectedAndFailedReadsAreUnavailableRatherThanEmpty() throws Exception {
        assertNull(RemoteSettingsSync.readEnabledScope());
        bind(null, true);
        assertNull(RemoteSettingsSync.readEnabledScope());
        bind(List.of(), false);
        assertEquals(Set.of(), RemoteSettingsSync.readEnabledScope());
    }

    @Test public void scopeIsReadFreshAndSnapshotsCannotBeMutated() throws Exception {
        List<String> scope = new ArrayList<>(List.of("com.android.systemui", "android"));
        bind(scope, false);
        Set<String> first = RemoteSettingsSync.readEnabledScope();
        assertEquals(Set.of("com.android.systemui", "android"), first);
        scope.clear();
        scope.add("com.meizu.media.gallery");
        assertEquals(Set.of("com.meizu.media.gallery"), RemoteSettingsSync.readEnabledScope());
        assertEquals(Set.of("com.android.systemui", "android"), first);
        assertThrows(UnsupportedOperationException.class, () -> first.add("com.example.app"));
    }

    private void bind(List<String> scope, boolean fail) throws Exception {
        // The AIDL interface is an internal runtime dependency of the service library.
        Class<?> binderType = Class.forName("io.github.libxposed.service.IXposedService");
        Object binder = Proxy.newProxyInstance(
                binderType.getClassLoader(), new Class<?>[]{binderType},
                (proxy, method, args) -> {
                    if (!method.getName().equals("getScope")) {
                        throw new AssertionError("Unexpected framework operation: " + method.getName());
                    }
                    if (fail) throw new RemoteException("Service disconnected");
                    return scope;
                });
        var constructor = XposedService.class.getDeclaredConstructor(binderType);
        constructor.setAccessible(true);
        serviceField.set(null, constructor.newInstance(binder));
    }
}
