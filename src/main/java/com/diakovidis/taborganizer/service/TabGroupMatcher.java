package com.diakovidis.taborganizer.service;

import com.intellij.openapi.vfs.VirtualFile;
import com.diakovidis.taborganizer.model.TabGroup;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Service responsible for matching files against tab group rules.
 *
 * <p>Patterns are treated as Java regular expressions. As a convenience,
 * glob-style wildcards ({@code *} and {@code ?}) are also supported: if a
 * pattern fails to compile as a regex it is automatically converted from
 * glob syntax (e.g. {@code *.component.ts} becomes {@code .*\.component\.ts}).
 *
 * <p>Matching uses {@code find()} so a pattern does not need to cover the
 * full file path.
 */
public final class TabGroupMatcher {

    private TabGroupMatcher() {
    }

    /**
     * Checks if the given file matches the regex of the specified tab group.
     * Matching is performed against the normalized (forward-slash) full path.
     */
    public static boolean matches(VirtualFile file, TabGroup group) {
        String filePath = file.getPath().replace('\\', '/');

        String pattern = group.getRegex();
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        try {
            Pattern compiled = compilePattern(pattern);
            return compiled.matcher(filePath).find();
        } catch (PatternSyntaxException e) {
            // Pattern could not be compiled even after glob conversion – skip it.
            return false;
        }
    }

    /**
     * Compiles the pattern as a regex. If it is not valid regex syntax the
     * pattern is first converted from glob notation.
     */
    public static Pattern compilePattern(String pattern) throws PatternSyntaxException {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return Pattern.compile(globToRegex(pattern));
        }
    }

    /**
     * Converts a simple glob pattern to an equivalent Java regex string.
     * {@code *} becomes {@code .*}, {@code ?} becomes {@code .},
     * all other regex metacharacters are escaped.
     */
    public static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder(glob.length() * 2);
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                case '.', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' -> {
                    sb.append('\\');
                    sb.append(c);
                }
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Validates the pattern (regex or glob).
     *
     * @return {@code null} if valid; otherwise a human-readable error message.
     */
    public static String validatePattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }
        try {
            compilePattern(pattern);
            return null;
        } catch (PatternSyntaxException e) {
            return e.getDescription();
        }
    }
}
