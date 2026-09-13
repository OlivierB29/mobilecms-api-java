package org.mobilecms.api.util;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

public final class PathSafety {

    private static final Pattern TYPE = Pattern.compile("^[A-Za-z0-9_-]+$");

    private PathSafety() {
    }

    public static String requireType(String type) {
        if (type == null || type.isBlank() || !TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("Invalid type");
        }
        return type;
    }

    public static String requireId(String id) {
        if (id == null || id.isBlank() || id.contains("..") || id.contains("/") || id.contains("\\")) {
            throw new IllegalArgumentException("Invalid id");
        }
        return id;
    }

    public static Path resolveUnder(Path root, String relative) {
        if (relative.contains("..")) {
            throw new IllegalArgumentException("Invalid path " + relative);
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root.normalize())) {
            throw new IllegalArgumentException("Invalid path " + relative);
        }
        return resolved;
    }

    public static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
