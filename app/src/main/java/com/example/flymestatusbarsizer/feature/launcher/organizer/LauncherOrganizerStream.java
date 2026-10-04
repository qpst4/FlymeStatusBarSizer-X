package com.example.flymestatusbarsizer.feature.launcher.organizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LauncherOrganizerStream {
    private static final Pattern GROUPS = Pattern.compile("\"groups\"\\s*:\\s*\\[");

    static void readEvents(Reader input, Predicate<String> event) throws IOException {
        BufferedReader reader = new BufferedReader(input);
        StringBuilder data = new StringBuilder();
        int received = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (received == 0 && line.startsWith("\uFEFF")) line = line.substring(1);
            received += line.length() + 1;
            if (received > 8_388_608 || data.length() + line.length() > 1_048_576) {
                throw new IOException("AI 返回内容过大");
            }
            if (line.isEmpty()) {
                if (data.length() > 0 && !event.test(data.toString())) return;
                data.setLength(0);
            } else if (line.equals("data") || line.startsWith("data:")) {
                String value = line.length() <= 5 ? "" : line.substring(5);
                if (value.startsWith(" ")) value = value.substring(1);
                if (data.length() > 0) data.append('\n');
                data.append(value);
            }
        }
        if (data.length() > 0) event.test(data.toString());
    }

    /** Only return complete group objects; partial JSON is never offered for applying. */
    static List<String> completedGroups(CharSequence text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = GROUPS.matcher(text);
        if (!matcher.find()) return result;
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        int start = -1;
        for (int i = matcher.end(); i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') {
                quoted = true;
            } else if (c == '{') {
                if (depth++ == 0) start = i;
            } else if (c == '}' && depth > 0) {
                if (--depth == 0) result.add(text.subSequence(start, i + 1).toString());
            } else if (c == ']' && depth == 0) {
                break;
            }
        }
        return result;
    }
}
