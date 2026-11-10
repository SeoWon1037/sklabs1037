package com.sklabs1037.sklabs1037.og;

/**
 * 🔗 공유 링크 미리보기용 작은 HTML 만들기 (순수 계산)
 * - 카카오톡 · 슬랙 · 문자 앱은 링크의 HTML에서 og:title · og:description · og:image 만 읽어 카드로 보여줌
 *   (React 화면은 자바스크립트를 돌려야 내용이 생겨서, 이 앱들은 내용을 못 읽음 → 전부 같은 사이트 소개만 뜸)
 * - 그래서 공유용 주소(/api/og/…)는 이 HTML을 돌려주고, 사람이 열면 바로 진짜 화면으로 넘어감
 * - 글자는 전부 HTML 이스케이프 (상품 제목처럼 사용자가 쓴 글이 들어감)
 */
public final class OgPage {

    static final int TITLE_MAX = 70;
    static final int DESC_MAX = 140;

    private OgPage() {
    }

    /**
     * @param site   https://sklabs1037.com (끝에 / 없이)
     * @param path   넘어갈 화면 주소 (/market/3 처럼 /로 시작하는 우리 사이트 안의 주소만)
     * @param image  미리보기 그림 주소 (/로 시작하면 site를 붙임, null이면 기본 그림)
     */
    public static String html(String site, String path, String title, String desc, String image) {

        String base = site == null ? "" : site.replaceAll("/+$", "");
        String to = safePath(path);
        String url = esc(base + to);
        String t = esc(clip(title, TITLE_MAX));
        String d = esc(clip(desc, DESC_MAX));
        String img = esc(image == null || image.isBlank() ? base + "/og-image.png" : image.startsWith("/") ? base + image : image);

        return "<!doctype html>\n<html lang=\"ko\">\n<head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<title>" + t + "</title>\n"
                + "<meta name=\"description\" content=\"" + d + "\">\n"
                + "<meta property=\"og:type\" content=\"website\">\n"
                + "<meta property=\"og:site_name\" content=\"SKLabs\">\n"
                + "<meta property=\"og:title\" content=\"" + t + "\">\n"
                + "<meta property=\"og:description\" content=\"" + d + "\">\n"
                + "<meta property=\"og:url\" content=\"" + url + "\">\n"
                + "<meta property=\"og:image\" content=\"" + img + "\">\n"
                + "<meta property=\"og:locale\" content=\"ko_KR\">\n"
                + "<meta name=\"twitter:card\" content=\"summary_large_image\">\n"
                + "<meta name=\"robots\" content=\"noindex\">\n"
                + "<link rel=\"canonical\" href=\"" + url + "\">\n"
                + "<meta http-equiv=\"refresh\" content=\"0;url=" + esc(to) + "\">\n"
                + "</head>\n<body>\n"
                + "<p><a href=\"" + esc(to) + "\">" + t + "</a></p>\n"
                + "<script>location.replace(" + jsString(to) + ")</script>\n"
                + "</body>\n</html>\n";

    }

    // 우리 사이트 안의 주소만 (//evil.com, http://… 는 첫 화면으로)
    static String safePath(String path) {

        if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("\\") || path.matches(".*[\\r\\n\\t].*")) return "/";
        return path;

    }

    static String esc(String s) {

        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");

    }

    // 줄바꿈을 한 칸으로, 길면 …
    static String clip(String s, int max) {

        String t = s == null ? "" : s.replaceAll("\\s+", " ").strip();
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;

    }

    // <script> 안에 넣을 문자열 (따옴표 · 꺾쇠 · 줄바꿈을 유니코드로)
    static String jsString(String s) {

        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\' || c == '<' || c == '>' || c == '&' || c == '\'' || c < 0x20 || c == 0x2028 || c == 0x2029) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();

    }
}
