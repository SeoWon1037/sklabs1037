package com.sklabs1037.sklabs1037.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    @GetMapping("/hello")

    public String hello() {

        return "RDS 연결 성공. ✅ PostgreSQL과 Spring Boot 테스트";

    }

}