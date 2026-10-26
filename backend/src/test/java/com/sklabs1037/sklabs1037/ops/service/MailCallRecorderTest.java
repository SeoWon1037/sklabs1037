package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.client.ApiCallLog;
import com.sklabs1037.sklabs1037.support.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🔌 메일 발송 기록 (JavaMailSender를 감싼 대리인)
 * - 감싼 뒤에도 진짜 send가 그대로 불림
 * - 성공·실패가 SMTP로 한 줄씩, 실패 이유는 예외 종류만 (받는 사람 이메일이 남지 않게)
 * - 메일과 상관없는 빈은 그대로
 */
class MailCallRecorderTest {

    @BeforeEach
    void clear() {
        while (!ApiCallLog.drain(10_000).isEmpty()) { }
    }

    @Test
    void 성공과_실패를_기록() {

        List<Fakes.Call> calls = new ArrayList<>();
        int[] n = {0};
        Map<String, Function<Object[], Object>> b = new HashMap<>();
        b.put("send", a -> {
            if (n[0]++ == 1) throw new MailAuthenticationException("bad password for someone@example.com");
            return null;
        });
        JavaMailSender real = Fakes.fake(JavaMailSender.class, b, calls);

        JavaMailSender wrapped = (JavaMailSender) new MailCallRecorder().postProcessAfterInitialization(real, "mailSender");

        wrapped.send(new SimpleMailMessage());
        assertThrows(MailAuthenticationException.class, () -> wrapped.send(new SimpleMailMessage()));

        assertEquals(2, calls.size(), "진짜 send가 두 번 불림");

        List<ApiCallLog.Entry> got = ApiCallLog.drain(10);
        assertEquals(2, got.size());
        assertEquals("SMTP", got.get(0).service());
        assertTrue(got.get(0).ok());
        assertFalse(got.get(1).ok());
        assertEquals("MailAuthenticationException", got.get(1).error());
        assertFalse(got.get(1).error().contains("@"), "이메일 주소는 남기지 않음");

    }

    @Test
    void 메일이_아닌_빈은_그대로() {

        Object other = new Object();
        assertSame(other, new MailCallRecorder().postProcessAfterInitialization(other, "x"));

    }
}
