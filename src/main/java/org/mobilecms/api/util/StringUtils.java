package org.mobilecms.api.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class StringUtils {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private StringUtils() {
    }

    public static boolean startsWith(String haystack, String needle) {
        return haystack != null && needle != null && haystack.startsWith(needle);
    }

    public static boolean endsWith(String haystack, String needle) {
        return haystack != null && needle != null && haystack.endsWith(needle);
    }

    public static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = NON_ALNUM.matcher(normalized).replaceAll("-");
        return trimHyphens(normalized);
    }

    private static String trimHyphens(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }

    /**
     * PHP {@code strnatcmp} approximation for index sorting.
     */
    public static int strnatcmp(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startI = i;
                int startJ = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String na = a.substring(startI, i).replaceFirst("^0+", "");
                String nb = b.substring(startJ, j).replaceFirst("^0+", "");
                if (na.isEmpty()) {
                    na = "0";
                }
                if (nb.isEmpty()) {
                    nb = "0";
                }
                int cmp = Integer.compare(na.length(), nb.length());
                if (cmp == 0) {
                    cmp = na.compareTo(nb);
                }
                if (cmp != 0) {
                    return cmp;
                }
            } else {
                int cmp = Character.compare(ca, cb);
                if (cmp != 0) {
                    return cmp;
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
