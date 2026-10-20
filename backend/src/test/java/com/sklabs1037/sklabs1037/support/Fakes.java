package com.sklabs1037.sklabs1037.support;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;

/**
 * 테스트용 가짜 객체 도우미 (DB·외부 API 없이 서비스 로직만 검사)
 * - fake(인터페이스, 메서드별 동작): Dao 같은 인터페이스를 가짜로 만듦 (정하지 않은 메서드는 0 / null / 빈 목록)
 * - set(객체, 필드, 값): @Autowired 필드에 가짜를 넣음
 * - calls: 가짜가 받은 호출 기록 (메서드 이름, 인자)
 */
public final class Fakes {

    private Fakes() {
    }

    public record Call(String method, Object[] args) {}

    @SuppressWarnings("unchecked")
    public static <T> T fake(Class<T> type, Map<String, Function<Object[], Object>> behavior, List<Call> calls) {

        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (proxy, method, args) -> {

            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "Fake(" + type.getSimpleName() + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }

            Object[] a = args == null ? new Object[0] : args;
            if (calls != null) calls.add(new Call(method.getName(), a));

            Function<Object[], Object> f = behavior.get(method.getName());
            if (f != null) return f.apply(a);

            Class<?> r = method.getReturnType();
            if (r == int.class) return 0;
            if (r == long.class) return 0L;
            if (r == boolean.class) return false;
            if (r == List.class) return List.of();

            return null;

        });

    }

    public static void set(Object target, String field, Object value) {

        Class<?> c = target.getClass();

        while (c != null) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        throw new IllegalArgumentException("필드 없음: " + field);

    }

    public static long count(List<Call> calls, String method) {

        return calls.stream().filter(c -> c.method().equals(method)).count();

    }
}
