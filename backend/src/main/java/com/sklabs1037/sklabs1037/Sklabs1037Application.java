package com.sklabs1037.sklabs1037;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // @Scheduled(정해진 시간마다 자동 실행) 기능 켜기 → 시세 수집 스케줄러용
public class Sklabs1037Application {

    public static void main(String[] args) {
        SpringApplication.run(Sklabs1037Application.class, args);
    }

}