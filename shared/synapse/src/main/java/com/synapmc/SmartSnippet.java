package com.synapmc;

import java.util.ArrayList;
import java.util.List;

/** Builds a single undoable insertion while retaining the surrounding source. */
public final class SmartSnippet {
    private SmartSnippet() {}

    public record Insertion(int start, int length, String text, int cursor) {}
    public record Completion(int start, String text) {}

    public static List<Completion> completions(String source, int cursor) {
        if (cursor <= 0 || cursor > source.length()) return List.of();
        // Native completion replaces only the prefix before the cursor. Do not
        // offer it in the middle of a token, where its suffix would be duplicated.
        if (cursor < source.length() && Character.isJavaIdentifierPart(source.charAt(cursor))) return List.of();
        int start = cursor;
        while (start > 0 && Character.isLetter(source.charAt(start - 1))) start--;
        if (start == 0 || source.charAt(start - 1) != '#') return List.of();
        start--;
        String prefix = source.substring(start, cursor);
        List<Completion> completions = new ArrayList<>();
        for (String marker : List.of("#indent", "#mouse", "#space"))
            if (marker.startsWith(prefix) && !marker.equals(prefix)) completions.add(new Completion(start, marker));
        return completions;
    }

    public static Insertion insert(String source, int start, int end, String snippet) {
        if (start < 0 || end < start || end > source.length()) throw new IllegalArgumentException("Invalid selection");
        boolean smartIndent = snippet.contains("#indent");
        String code = snippet.replace("\r\n", "\n").replace('\r', '\n');
        // Wrapper markers can sit beside code or on their own lines. Remove their
        // separators without stripping the first line's relative indentation.
        code = code.replaceFirst("^#indent[ \\t\\n]?", "")
                .replaceFirst("[ \\t\\n]?#indent$", "").replace("#indent", "");
        int lineStart = source.lastIndexOf('\n', Math.max(-1, start - 1)) + 1;
        int lineEnd = source.indexOf('\n', end);
        if (lineEnd < 0) lineEnd = source.length();
        String before = source.substring(lineStart, start);
        String after = source.substring(end, lineEnd);
        int indentLength = 0;
        while (indentLength < before.length() && (before.charAt(indentLength) == ' ' || before.charAt(indentLength) == '\t')) indentLength++;
        String indent = before.substring(0, indentLength);
        if (smartIndent) code = dedent(code);
        // Expand explicit spaces after dedenting so they survive at either end.
        code = code.replace("#space", " ");
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder text = new StringBuilder();
        if (smartIndent && !before.isBlank()) text.append(newline).append(indent);
        int cursor = -1;
        for (int i = 0; i < code.length();) {
            if (code.startsWith("#mouse", i)) {
                if (cursor < 0) cursor = text.length();
                i += 6;
            } else if (code.charAt(i) == '\n') {
                text.append(newline);
                if (smartIndent) text.append(indent);
                i++;
            } else text.append(code.charAt(i++));
        }
        if (cursor < 0) cursor = text.length();
        if (smartIndent && !after.isBlank()) text.append(newline).append(indent);
        return new Insertion(start, end - start, text.toString(), start + cursor);
    }

    private static String dedent(String code) {
        String[] lines = code.split("\n", -1);
        int common = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.isBlank() && !line.contains("#mouse")) continue;
            int count = 0;
            while (count < line.length() && (line.charAt(count) == ' ' || line.charAt(count) == '\t')) count++;
            common = Math.min(common, count);
        }
        if (common == Integer.MAX_VALUE || common == 0) return code;
        for (int i = 0; i < lines.length; i++) {
            int count = 0;
            while (count < Math.min(common, lines[i].length())
                    && (lines[i].charAt(count) == ' ' || lines[i].charAt(count) == '\t')) count++;
            lines[i] = lines[i].substring(count);
        }
        return String.join("\n", lines);
    }
}
