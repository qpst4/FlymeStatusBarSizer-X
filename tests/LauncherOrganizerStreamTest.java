package com.example.flymestatusbarsizer;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class LauncherOrganizerStreamTest {
    public static void main(String[] args) throws Exception {
        String stream = "\uFEFF: heartbeat\r\n\r\nevent: message\r\nid: 1\r\n"
                + "data: {\"content\":\"中文\"}\r\n\r\n"
                + "data: {\"done\":\ndata: true}\n\n"
                + "data:[DONE]\n\ndata: ignored\n\n";
        List<String> events = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(new FilterInputStream(
                new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8))) {
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                return super.read(bytes, offset, Math.min(length, 1));
            }
        }, StandardCharsets.UTF_8)) {
            LauncherOrganizerStream.readEvents(reader, event -> {
                events.add(event);
                return !event.equals("[DONE]");
            });
        }
        check(events.equals(List.of("{\"content\":\"中文\"}", "{\"done\":\ntrue}", "[DONE]")), "SSE frames, UTF-8, multiline data and termination");
        events.clear();
        LauncherOrganizerStream.readEvents(new StringReader("data: final"), event -> events.add(event));
        check(events.equals(List.of("final")), "final event without trailing newline");
        events.clear();
        LauncherOrganizerStream.readEvents(new StringReader(": keepalive\n\nevent: ping\n\n"), event -> events.add(event));
        check(events.isEmpty(), "heartbeats must not become content");
        try {
            LauncherOrganizerStream.readEvents(new StringReader("data: " + "x".repeat(1_048_577)), event -> true);
            throw new AssertionError("oversized event accepted");
        } catch (IOException expected) { }

        String first = "{\"name\":\"社交\\\"}工具\",\"apps\":[\"a0\",\"a1\"]}";
        String second = "{\"name\":\"阅读\",\"apps\":[\"a2\"]}";
        String text = "```json\n{\"groups\": [" + first + "," + second + "]}\n```";
        int firstEnd = text.indexOf(first) + first.length();
        int secondEnd = text.indexOf(second) + second.length();
        for (int end = 0; end <= text.length(); end++) {
            List<String> groups = LauncherOrganizerStream.completedGroups(text.substring(0, end));
            int expected = (end >= firstEnd ? 1 : 0) + (end >= secondEnd ? 1 : 0);
            check(groups.size() == expected, "only complete objects at fragment boundary " + end);
            if (expected >= 1) check(groups.get(0).equals(first), "escaped quotes and braces in a name");
            if (expected == 2) check(groups.get(1).equals(second), "second group");
        }
        check(LauncherOrganizerStream.completedGroups("{\"groups\": []}").isEmpty(), "empty groups");
        check(LauncherOrganizerStream.completedGroups("{\"groups\": [{\"name\":\"未完成").isEmpty(), "truncated group");
        System.out.println("AI streaming and partial classification checks passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
