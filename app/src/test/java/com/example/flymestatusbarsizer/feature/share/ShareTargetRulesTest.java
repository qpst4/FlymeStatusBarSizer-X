package com.example.flymestatusbarsizer.feature.share;

import static org.junit.Assert.*;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public final class ShareTargetRulesTest {
    private static final String FRIEND = "com.tencent.mm/com.tencent.mm.ui.tools.ShareImgUI";
    private static final String MOMENTS = "com.tencent.mm/com.tencent.mm.ui.tools.ShareToTimeLineUI";
    private static final String QQ = "com.tencent.mobileqq/com.tencent.mobileqq.activity.JumpActivity";
    private static final String NEW = "com.newapp/com.newapp.Share";

    @Test public void hidesOneComponentWithoutHidingOtherEntrypointsFromSameApp() {
        ShareTargetRules rules = new ShareTargetRules(List.of(), List.of(MOMENTS));
        List<String> input = new ArrayList<>(List.of(FRIEND, MOMENTS, QQ));
        assertEquals(List.of(FRIEND, QQ), rules.apply(input, s -> s));
        assertEquals(List.of(FRIEND, MOMENTS, QQ), input);
    }

    @Test public void manualOrderOverridesIncomingUsageOrderAndNewTargetsRemainStable() {
        ShareTargetRules rules = new ShareTargetRules(List.of(QQ, FRIEND), List.of());
        String other = "com.other/com.other.Share";
        assertEquals(List.of(QQ, FRIEND, NEW, other),
                rules.apply(List.of(NEW, FRIEND, other, QQ), s -> s));
        assertEquals(List.of(QQ, FRIEND, other, NEW),
                rules.apply(List.of(other, QQ, NEW, FRIEND), s -> s));
    }

    @Test public void neverAddsUnsupportedTargetsAndCanFilterEveryTarget() {
        ShareTargetRules rules = new ShareTargetRules(List.of(QQ, FRIEND), List.of(MOMENTS));
        assertEquals(List.of(), rules.apply(List.of(MOMENTS), s -> s));
        assertEquals(List.of(FRIEND), rules.apply(List.of(FRIEND, MOMENTS), s -> s));
    }

    @Test public void importNormalizesRelativeNamesIgnoresMalformedLinesAndDeduplicates() {
        ShareTargetRules rules = ShareTargetRules.parse("pkg/.Share\npkg/pkg.Share\ninvalid\n../x\n",
                "pkg/.Hidden\r\npkg/pkg.Hidden\n");
        assertEquals(List.of("pkg/pkg.Share"), rules.order());
        assertEquals("pkg/pkg.Hidden", rules.encodeHidden());
        assertEquals(List.of("pkg/.Share"), rules.apply(List.of("pkg/.Hidden", "pkg/.Share"), s -> s));
        ShareTargetRules restored = ShareTargetRules.parse(rules.encodeOrder(), rules.encodeHidden());
        assertEquals(rules.order(), restored.order());
        assertEquals(rules.hidden(), restored.hidden());
    }

    @Test public void hidingWithoutDraggingDoesNotFreezeCatalogOrder() {
        ShareTargetsDraft draft = new ShareTargetsDraft(List.of(FRIEND, QQ, MOMENTS), ShareTargetRules.EMPTY);
        draft.setHidden(MOMENTS, true);
        assertTrue(draft.rules().order().isEmpty());
        assertEquals(List.of(QQ, FRIEND), draft.rules().apply(List.of(QQ, FRIEND, MOMENTS), s -> s));
    }

    @Test public void dragSaveAndReloadPreserveHiddenAndTemporarilyMissingTargets() {
        ShareTargetsDraft draft = new ShareTargetsDraft(List.of(FRIEND, MOMENTS, QQ), ShareTargetRules.EMPTY);
        draft.setHidden(MOMENTS, true);
        assertTrue(draft.move(QQ, 0));
        assertEquals(List.of(QQ, FRIEND, MOMENTS), draft.rules().order());
        ShareTargetRules saved = ShareTargetRules.parse(draft.rules().encodeOrder(), draft.rules().encodeHidden());
        ShareTargetsDraft reloaded = new ShareTargetsDraft(List.of(FRIEND, NEW), saved);
        assertEquals(List.of(QQ, FRIEND, MOMENTS, NEW), reloaded.components());
        assertTrue(reloaded.isHidden(MOMENTS));
        assertEquals(List.of(FRIEND, NEW), reloaded.rules().apply(List.of(NEW, FRIEND), s -> s));
    }

    @Test public void resetAndUnhideRestoreSystemOrderingWithoutChangingSavedRules() {
        ShareTargetRules saved = new ShareTargetRules(List.of(QQ, FRIEND), List.of(FRIEND));
        ShareTargetsDraft draft = new ShareTargetsDraft(List.of(FRIEND, QQ), saved);
        draft.setHidden(FRIEND, false);
        assertFalse(draft.isHidden(FRIEND));
        assertTrue(saved.hidden().contains(FRIEND));
        draft.reset();
        assertTrue(draft.rules().isEmpty());
        assertEquals(List.of(FRIEND, QQ), draft.components());
        assertEquals(List.of(QQ, FRIEND), saved.order());
    }

    @Test public void invalidOrCancelledMoveDoesNotEnableFixedOrdering() {
        ShareTargetsDraft draft = new ShareTargetsDraft(List.of(FRIEND, QQ), ShareTargetRules.EMPTY);
        assertFalse(draft.move(FRIEND, -1));
        assertFalse(draft.move(NEW, 0));
        assertFalse(draft.move(FRIEND, 0));
        assertFalse(draft.hasFixedOrder());
        assertEquals(List.of(FRIEND, QQ), draft.components());
    }

    @Test public void followSystemClearsOnlyOrderAndPreservesUnavailableHiddenTargets() {
        ShareTargetsDraft draft = new ShareTargetsDraft(List.of(FRIEND, QQ),
                new ShareTargetRules(List.of(QQ, FRIEND, MOMENTS), List.of(MOMENTS, FRIEND)));
        draft.followSystemOrder();
        assertFalse(draft.hasFixedOrder());
        assertEquals(List.of(FRIEND, QQ, MOMENTS), draft.components());
        assertTrue(draft.rules().order().isEmpty());
        assertTrue(draft.isHidden(MOMENTS));
        assertTrue(draft.isHidden(FRIEND));
        assertEquals(List.of(QQ, NEW), draft.rules().apply(List.of(QQ, FRIEND, NEW), s -> s));
        draft.reset();
        assertEquals(List.of(FRIEND, QQ), draft.components());
        assertTrue(draft.rules().isEmpty());
    }
}
