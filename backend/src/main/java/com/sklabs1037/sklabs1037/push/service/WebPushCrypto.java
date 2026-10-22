package com.sklabs1037.sklabs1037.push.service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Base64;

/**
 * 📲 웹 푸시 암호화 (외부 라이브러리 없이 JDK만)
 *
 * - VAPID (RFC 8292): 서버 키(P-256)로 서명한 JWT(ES256) → 푸시 서비스가 "이 사이트가 보낸 것"인지 확인
 * - 내용 암호화 (RFC 8291, aes128gcm): 브라우저 공개키와 ECDH → HKDF → AES-128-GCM
 *   → 푸시 서비스(구글·애플·모질라)는 알림 내용을 읽을 수 없음
 * - RFC 8291 5장 예시 값으로 테스트 (WebPushCryptoTest)
 */
public final class WebPushCrypto {

    private WebPushCrypto() {
    }

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder D64 = Base64.getUrlDecoder();
    private static final int RECORD_SIZE = 4096;

    public static String b64(byte[] b) {
        return B64.encodeToString(b);
    }

    public static byte[] unb64(String s) {
        return D64.decode(s.strip().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    // ============================================
    // 키
    // ============================================
    private static ECParameterSpec params() {

        try {
            AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
            p.init(new ECGenParameterSpec("secp256r1"));
            return p.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }

    }

    public static KeyPair generate() {

        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }

    }

    // 공개키 → 65바이트 (0x04 + X 32 + Y 32), 브라우저가 쓰는 모양
    public static byte[] rawPublic(PublicKey key) {

        ECPoint w = ((ECPublicKey) key).getW();
        byte[] out = new byte[65];
        out[0] = 4;
        System.arraycopy(fixed(w.getAffineX()), 0, out, 1, 32);
        System.arraycopy(fixed(w.getAffineY()), 0, out, 33, 32);
        return out;

    }

    public static byte[] rawPrivate(PrivateKey key) {

        return fixed(((ECPrivateKey) key).getS());

    }

    public static PublicKey publicFromRaw(byte[] raw) {

        if (raw.length != 65 || raw[0] != 4) throw new IllegalArgumentException("공개키 모양이 아니에요.");
        try {
            ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)), new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
            return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, params()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("공개키를 읽지 못했어요.", e);
        }

    }

    public static PrivateKey privateFromRaw(byte[] raw) {

        try {
            return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), params()));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("개인키를 읽지 못했어요.", e);
        }

    }

    private static byte[] fixed(BigInteger n) {

        byte[] b = n.toByteArray();
        if (b.length == 32) return b;
        byte[] out = new byte[32];
        if (b.length > 32) System.arraycopy(b, b.length - 32, out, 0, 32);
        else System.arraycopy(b, 0, out, 32 - b.length, b.length);
        return out;

    }

    // ============================================
    // VAPID JWT (ES256)
    // ============================================
    public static String vapidJwt(String audience, String subject, long expSeconds, PrivateKey key) {

        String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64(("{\"aud\":\"" + audience + "\",\"exp\":" + expSeconds + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String input = header + "." + payload;

        try {
            Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");   // 바로 R(32)+S(32)
            s.initSign(key);
            s.update(input.getBytes(StandardCharsets.US_ASCII));
            return input + "." + b64(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }

    }

    // ============================================
    // 내용 암호화 (RFC 8291)
    // ============================================
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublicRaw, byte[] authSecret) {

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return encrypt(plaintext, uaPublicRaw, authSecret, generate(), salt);

    }

    // 테스트용: 서버 임시 키와 salt를 정해서
    static byte[] encrypt(byte[] plaintext, byte[] uaPublicRaw, byte[] authSecret, KeyPair asKeys, byte[] salt) {

        if (plaintext.length > RECORD_SIZE - 17 - 1) throw new IllegalArgumentException("알림 내용이 너무 길어요.");

        try {

            byte[] asPublic = rawPublic(asKeys.getPublic());

            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(asKeys.getPrivate());
            ka.doPhase(publicFromRaw(uaPublicRaw), true);
            byte[] ecdh = ka.generateSecret();

            byte[] prkKey = hmac(authSecret, ecdh);
            byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublicRaw, asPublic, new byte[]{1});
            byte[] ikm = hmac(prkKey, keyInfo);

            byte[] prk = hmac(salt, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1})), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1})), 12);

            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] cipher = c.doFinal(concat(plaintext, new byte[]{2}));   // 0x02 = 마지막 묶음

            ByteBuffer out = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + cipher.length);
            out.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic).put(cipher);
            return out.array();

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("알림을 암호화하지 못했어요.", e);
        }

    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);

    }

    private static byte[] concat(byte[]... parts) {

        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;

    }
}
