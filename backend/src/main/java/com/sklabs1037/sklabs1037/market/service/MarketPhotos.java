package com.sklabs1037.sklabs1037.market.service;

import java.util.Base64;

/**
 * 🛍️ 상품 사진 검사 (순수 계산)
 * - 화면에서 줄여서 보냄: 큰 그림(긴 변 1280px JPEG) + 목록용 작은 그림(360px JPEG)
 * - 서버는 진짜 그림 파일인지(파일 맨 앞 바이트) · 크기 제한만 확인
 */
public final class MarketPhotos {

    public static final int MAX_BYTES = 1_500_000;        // 큰 그림 1.5MB
    public static final int MAX_THUMB_BYTES = 200_000;    // 작은 그림 200KB

    private MarketPhotos() {
    }

    // "data:image/jpeg;base64,...." 또는 base64만 → 바이트 (너무 크거나 못 읽으면 예외)
    public static byte[] decode(String image, int maxBytes) {

        if (image == null || image.isBlank()) throw new IllegalArgumentException("사진을 읽지 못했어요.");
        String b64 = image.contains(",") ? image.substring(image.indexOf(',') + 1) : image;
        if (b64.length() > maxBytes / 3 * 4 + 16) throw new IllegalArgumentException("사진이 너무 커요. 다른 사진으로 해주세요.");
        byte[] data;
        try {
            data = Base64.getDecoder().decode(b64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("사진을 읽지 못했어요.");
        }
        if (data.length > maxBytes) throw new IllegalArgumentException("사진이 너무 커요. 다른 사진으로 해주세요.");
        return data;

    }

    // 파일 맨 앞 몇 바이트로 진짜 그림인지 (JPEG · PNG · WebP) → 아니면 null
    public static String imageType(byte[] b) {

        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;

    }
}
