package org.mobilecms.api.util;

import java.util.regex.Pattern;

public final class XssFilter {

    private XssFilter() {
    }

    public static String clean(String data) {
        if (data == null) {
            return null;
        }
        data = data.replace("&amp;", "&amp;amp;").replace("&lt;", "&amp;lt;").replace("&gt;", "&amp;gt;");
        data = Pattern.compile("(&#*\\w+)[\\x00-\\x20]+;", Pattern.UNICODE_CASE).matcher(data).replaceAll("$1;");
        data = Pattern.compile("(&#x*[0-9A-F]+);*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(data)
                .replaceAll("$1;");
        data = org.springframework.web.util.HtmlUtils.htmlUnescape(data);
        data = Pattern.compile("(<[^>]+?[\\x00-\\x20\"'])(?:on|xmlns)[^>]*+>", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(data)
                .replaceAll("$1>");
        data = Pattern.compile("([a-z]*)[\\x00-\\x20]*=[\\x00-\\x20]*([`'\"]*)[\\x00-\\x20]*j[\\x00-\\x20]*a[\\x00-\\x20]*v[\\x00-\\x20]*a[\\x00-\\x20]*s[\\x00-\\x20]*c[\\x00-\\x20]*r[\\x00-\\x20]*i[\\x00-\\x20]*p[\\x00-\\x20]*t[\\x00-\\x20]*:", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(data)
                .replaceAll("$1=$2nojavascript...");
        data = Pattern.compile("([a-z]*)[\\x00-\\x20]*=(['\"]*)[\\x00-\\x20]*v[\\x00-\\x20]*b[\\x00-\\x20]*s[\\x00-\\x20]*c[\\x00-\\x20]*r[\\x00-\\x20]*i[\\x00-\\x20]*p[\\x00-\\x20]*t[\\x00-\\x20]*:", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(data)
                .replaceAll("$1=$2novbscript...");
        String previous;
        do {
            previous = data;
            data = Pattern.compile("</*(?:applet|b(?:ase|gsound|link)|embed|frame(?:set)?|i(?:frame|layer)|l(?:ayer|ink)|meta|object|s(?:cript|tyle)|title|xml)[^>]*+>", Pattern.CASE_INSENSITIVE)
                    .matcher(data)
                    .replaceAll("");
        } while (!previous.equals(data));
        return data;
    }
}
