package com.bookstore.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api") // ✅ 이게 있어야 /api/hello 작동해
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello, Bookstore!";
    }
}
