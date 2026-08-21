package ac.voidac.manager.config.update;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigPatcher {

    private final File file;
    private final List<String> lines;
    private final Map<String, NodePosition> locationMap;

    // ... (formatValue and YAML_KEYWORDS/SPECIAL_CHARS constants are unchanged) ...
    private static final Set<String> YAML_KEYWORDS_TO_QUOTE = new HashSet<>(Arrays.asList(
            "y", "Y", "yes", "Yes", "YES", "n", "N", "no", "No", "NO",
            "true", "True", "TRUE", "false", "False", "FALSE",
            "on", "On", "ON", "off", "Off", "OFF",
            "null", "Null", "NULL", "~"
    ));
    private static final String YAML_SPECIAL_CHARS = ":{}[]#|>&*!%@`'\",-?!^$";

    public ConfigPatcher(File configFile) throws IOException {
        this.file = configFile;
        this.lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
        this.locationMap = new HashMap<>();
        mapFile();
    }

    public NodePosition getNodePosition(String path) {
        return locationMap.get(path);
    }

    public void applyChange(PendingChange change) {
        if (change.value() instanceof List) {
            replaceListBlock(change.position(), (List<?>) change.value());
        } else {
            replaceScalarValue(change.position(), change.value());
        }
    }

    /**
     * One pending YAML value-replacement, sortable by descending line index
     * so list-block replacements at low line numbers don't invalidate the
     * cached positions of changes at higher line numbers.
     */
    public record PendingChange(NodePosition position, Object value) implements Comparable<PendingChange> {
        @Override
        public int compareTo(PendingChange other) {
            return Integer.compare(other.position.lineIndex(), this.position.lineIndex());
        }
    }

    private void mapFile() {
        List<String> pathStack = new ArrayList<>();
        List<Integer> indentStack = new ArrayList<>();
        Pattern keyPattern = Pattern.compile("^(\\s*)([^#:]+):(.*)");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            Matcher matcher = keyPattern.matcher(line);
            if (matcher.matches()) {
                int currentIndent = matcher.group(1).length();
                String key = matcher.group(2).trim();
                while (!indentStack.isEmpty() && currentIndent <= indentStack.get(indentStack.size() - 1)) {
                    pathStack.remove(pathStack.size() - 1);
                    indentStack.remove(indentStack.size() - 1);
                }
                pathStack.add(key);
                indentStack.add(currentIndent);
                String currentPath = String.join(".", pathStack);
                locationMap.put(currentPath, new NodePosition(i, currentIndent));
            }
        }
    }

    /** {@code |}, {@code >}, and their chomping/indent variants: {@code |-}, {@code >+}, {@code |2}. */
    private static final Pattern BLOCK_SCALAR = Pattern.compile("^[|>][+-]?[0-9]?[+-]?$");

    private void replaceScalarValue(NodePosition position, Object value) {
        String line = lines.get(position.lineIndex);
        Pattern linePattern = Pattern.compile("^(\\s*[^:]+:\\s*)(.*?)(\\s*#.*)?$");
        Matcher matcher = linePattern.matcher(line);
        if (!matcher.matches()) return;

        // A `reason: |` line holds an indicator, not a value. Overwrite it and
        // the `|` is gone while the body underneath stays put, which is how a
        // colour code turns into a YAML anchor and takes the whole config down.
        // Anything that was not a block stays inline: the templates write their
        // multi-line values as quoted strings with \n, and formatValue keeps them
        // that way. Promoting one to a block here would change what it parses to.
        String indicator = matcher.group(2).trim();
        if (BLOCK_SCALAR.matcher(indicator).matches()) {
            replaceBlockScalar(position, value == null ? "" : value.toString(), indicator);
            return;
        }

        lines.set(position.lineIndex, matcher.group(1) + formatValue(value)
                + (matcher.group(3) != null ? matcher.group(3) : ""));
    }

    /**
     * Rewrites a block scalar in place: the indicator line keeps whatever
     * chomping it already had, and the body underneath is replaced with the new
     * text at the body's own indent.
     */
    private void replaceBlockScalar(NodePosition position, String text, String indicator) {
        String keyLine = lines.get(position.lineIndex);
        int colon = keyLine.indexOf(':');
        if (colon < 0) return;
        lines.set(position.lineIndex, keyLine.substring(0, colon + 1) + " " + indicator);

        int bodyStart = position.lineIndex + 1;
        int bodyEnd = bodyStart;
        int bodyIndent = -1;
        for (int i = bodyStart; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.trim().isEmpty()) {
                bodyEnd = i + 1;
                continue;
            }
            if (getIndentation(l) <= position.indent) break;
            if (bodyIndent == -1) bodyIndent = getIndentation(l);
            bodyEnd = i + 1;
        }
        if (bodyIndent == -1) bodyIndent = position.indent + 4;

        List<String> body = new ArrayList<>();
        for (String l : text.split("\n", -1)) body.add(" ".repeat(bodyIndent) + l);
        // a value ending in a newline leaves an empty last element behind
        while (body.size() > 1 && body.get(body.size() - 1).isBlank()) body.remove(body.size() - 1);

        if (bodyEnd > bodyStart) lines.subList(bodyStart, bodyEnd).clear();
        lines.addAll(bodyStart, body);
    }

    private void replaceListBlock(NodePosition keyPosition, List<?> newList) {
        // If the key has an inline list on its own line (e.g. `chain: [a, b]`),
        // strip the inline value off the line first so it doesn't co-exist with
        // the block-list items we're about to insert below.
        scrubInlineListFromKeyLine(keyPosition.lineIndex);

        int blockStartLine = -1, blockEndLine = keyPosition.lineIndex + 1, listIndent = -1;
        for (int i = keyPosition.lineIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                if (blockStartLine != -1) blockEndLine = i + 1; // Include comments/blank lines within the block
                continue;
            }
            int currentIndent = getIndentation(line);
            if (currentIndent <= keyPosition.indent) {
                blockEndLine = i;
                break;
            }
            if (line.trim().startsWith("-")) {
                if (blockStartLine == -1) {
                    blockStartLine = i;
                    listIndent = currentIndent;
                }
                blockEndLine = i + 1;
            }
        }
        if (blockStartLine == -1) {
            blockStartLine = keyPosition.lineIndex + 1;
            blockEndLine = blockStartLine;
            listIndent = keyPosition.indent + 2;
        }
        if (blockEndLine > blockStartLine) {
            lines.subList(blockStartLine, blockEndLine).clear();
        }
        if (newList.isEmpty()) {
            // a key with no block under it reads back as null, so an empty
            // list has to keep the inline form to stay an empty list
            String keyLine = lines.get(keyPosition.lineIndex);
            int colon = keyLine.indexOf(':');
            if (colon >= 0) lines.set(keyPosition.lineIndex, keyLine.substring(0, colon + 1) + " []");
            return;
        }
        String newBlock = generateListBlock(newList, listIndent);
        if (!newBlock.isEmpty()) {
            lines.addAll(blockStartLine, Arrays.asList(newBlock.split("\r?\n")));
        }
    }

    // ... (generateListBlock, formatValue, getIndentation, save, NodePosition are unchanged from previous correct version) ...
    private String generateListBlock(List<?> list, int indentation) {
        if (list.isEmpty()) return "";
        StringBuilder block = new StringBuilder();
        String indentStr = " ".repeat(indentation);
        for (int i = 0; i < list.size(); i++) {
            block.append(indentStr).append("- ").append(formatValue(list.get(i)));
            if (i < list.size() - 1) block.append(System.lineSeparator());
        }
        return block.toString();
    }
    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        String s = value.toString();
        if (s.isEmpty()) return "\"\"";
        boolean needsQuotes = false;
        if (YAML_KEYWORDS_TO_QUOTE.contains(s) || s.startsWith(" ") || s.endsWith(" ")
                || s.indexOf('\n') != -1 || s.indexOf('\r') != -1 || s.indexOf('\t') != -1) {
            needsQuotes = true;
        } else {
            try {
                if (s.matches("^[+-]?([0-9]*[.])?[0-9]+$")) {
                    Double.parseDouble(s);
                    needsQuotes = true;
                }
            } catch (NumberFormatException e) { /* Good */ }
        }
        if (!needsQuotes) {
            for (char c : YAML_SPECIAL_CHARS.toCharArray()) {
                if (s.indexOf(c) != -1) {
                    needsQuotes = true;
                    break;
                }
            }
        }
        if (needsQuotes) {
            // Double-quoted YAML scalars interpret backslash escapes, so a
            // raw `\d` from a regex round-trips as an "unknown escape"
            // parser error. Escape `\` first so the `\` we inject ahead of
            // any embedded `"` doesn't get re-doubled by the second pass.
            // Newlines go in as escapes too: a real one inside double quotes
            // gets folded back into a space and the message loses its layout.
            return "\"" + s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "\"";
        }
        return s;
    }
    private int getIndentation(String line) {
        for (int i = 0; i < line.length(); i++) if (line.charAt(i) != ' ') return i;
        return 0;
    }

    /**
     * If the key on {@code lineIndex} has an inline list value
     * ({@code key: [a, b, c]}), rewrite the line to {@code key:} so the
     * block-list rewrite below the key doesn't end up doubling the values.
     * Trailing comments are preserved. Lines without an inline-list shape
     * are left untouched.
     */
    private void scrubInlineListFromKeyLine(int lineIndex) {
        String line = lines.get(lineIndex);
        int colonIdx = line.indexOf(':');
        if (colonIdx < 0) return;
        String afterColon = line.substring(colonIdx + 1);
        // Split off any trailing comment so the bracket detection is local
        // to the value portion only.
        int hashIdx = -1;
        boolean inString = false;
        char quote = 0;
        for (int i = 0; i < afterColon.length(); i++) {
            char c = afterColon.charAt(i);
            if (inString) {
                if (c == quote && (i == 0 || afterColon.charAt(i - 1) != '\\')) inString = false;
            } else if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
            } else if (c == '#') {
                hashIdx = i;
                break;
            }
        }
        String valuePart = (hashIdx >= 0 ? afterColon.substring(0, hashIdx) : afterColon).trim();
        String trailingComment = hashIdx >= 0 ? "  " + afterColon.substring(hashIdx).trim() : "";
        if (valuePart.startsWith("[") && valuePart.endsWith("]")) {
            // Inline-list form. Keep "key:" + any trailing comment; let the
            // block-list rewrite happen below.
            lines.set(lineIndex, line.substring(0, colonIdx + 1) + trailingComment);
        }
    }
    public void save() throws IOException {
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }
    public record NodePosition(int lineIndex, int indent) {}
}
