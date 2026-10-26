package com.sklabs1037.sklabs1037.ops.service;

import com.sklabs1037.sklabs1037.client.ApiCallLog;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * 🔌 메일(네이버 SMTP) 발송도 연동 기록에 남기기
 *
 * 메일을 보내는 곳이 5군데(회원가입 인증, 계정 찾기, 가격 알림, 아침 브리핑, 관리자 장애 메일)라
 * 하나하나 고치지 않고, 스프링이 만든 JavaMailSender를 "기록하는 대리인"으로 한 번 감쌈
 *   - BeanPostProcessor: 스프링이 빈을 만든 직후 끼어들어 다른 객체로 바꿔 끼울 수 있는 자리
 *   - Proxy: JavaMailSender 모양 그대로인 대리 객체 → send…() 가 불리면 시간을 재고 진짜 객체에 넘김
 *   → 메일을 쓰는 코드는 아무것도 몰라도 됨 (주입받는 타입이 그대로 JavaMailSender)
 *
 * 실패 이유에는 예외 종류만 남김 (메시지에 받는 사람 이메일이 섞일 수 있어서)
 */
@Component
public class MailCallRecorder implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {

        if (!(bean instanceof JavaMailSender real)) return bean;

        return Proxy.newProxyInstance(JavaMailSender.class.getClassLoader(), new Class<?>[]{JavaMailSender.class}, (proxy, method, args) -> {

            boolean sending = method.getName().startsWith("send");
            long start = ApiCallLog.start();

            try {

                Object result = method.invoke(real, args);
                if (sending) ApiCallLog.record("SMTP", "send", true, null, ApiCallLog.elapsedMs(start), null);
                return result;

            } catch (InvocationTargetException e) {

                Throwable cause = e.getCause();
                if (sending) ApiCallLog.record("SMTP", "send", false, null, ApiCallLog.elapsedMs(start), errorKind(cause));
                throw cause;

            }

        });

    }

    // "MailAuthenticationException ← AuthenticationFailedException" 처럼 종류만
    static String errorKind(Throwable e) {

        if (e == null) return "Unknown";

        String text = e.getClass().getSimpleName();
        if (e.getCause() != null && e.getCause() != e) text += " ← " + e.getCause().getClass().getSimpleName();

        return text;

    }
}
