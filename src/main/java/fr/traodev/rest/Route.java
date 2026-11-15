package fr.traodev.rest;

public record Route(String method, String path, String template, String major) {
    public static Route of(String method, String template, Object... args) {
        StringBuilder sb = new StringBuilder(template.length() + 24);
        int a = 0, i = 0;
        while (i < template.length()) {
            int p = template.indexOf("{}", i);
            if (p < 0) {
                sb.append(template, i, template.length());
                break;
            }
            sb.append(template, i, p).append(args[a++]);
            i = p + 2;
        }
        String major = "";
        if (args.length > 0) {
            if (template.startsWith("/channels/") || template.startsWith("/guilds/")) major = String.valueOf(args[0]);
            else if ((template.startsWith("/webhooks/") || template.startsWith("/interactions/")) && args.length > 1)
                major = args[0] + ":" + args[1];
        }
        return new Route(method, sb.toString(), template, major);
    }

    public static Route get(String t, Object... a) {
        return of("GET", t, a);
    }

    public static Route post(String t, Object... a) {
        return of("POST", t, a);
    }

    public static Route put(String t, Object... a) {
        return of("PUT", t, a);
    }

    public static Route patch(String t, Object... a) {
        return of("PATCH", t, a);
    }

    public static Route delete(String t, Object... a) {
        return of("DELETE", t, a);
    }

    public Route query(String key, Object value) {
        if (value == null) return this;
        String enc = java.net.URLEncoder.encode(String.valueOf(value), java.nio.charset.StandardCharsets.UTF_8);
        return new Route(method, path + (path.contains("?") ? "&" : "?") + key + "=" + enc, template, major);
    }

    String templateKey() {
        return method + " " + template;
    }

    String bucketKey() {
        return method + " " + template + " " + major;
    }
}
