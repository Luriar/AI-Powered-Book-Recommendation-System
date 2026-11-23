package com.bookstore.backend.controller;

import com.bookstore.backend.dto.UserRequest;
import com.bookstore.backend.model.User;
import com.bookstore.backend.service.AuthService;
import com.bookstore.backend.util.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthService authService, JwtUtil jwtUtil) {
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public Map<String, String> register(@RequestBody @Valid UserRequest userRequest,
                                        BindingResult result) {
        Map<String, String> response = new HashMap<>();

        if (result.hasErrors()) {
            List<String> passwordErrors = new ArrayList<>();

            for (FieldError error : result.getFieldErrors("password")) {
                String msg = error.getDefaultMessage();
                if (msg.contains("8자 이상") && !passwordErrors.contains(msg)) {
                    passwordErrors.add(msg);
                }
            }
            for (FieldError error : result.getFieldErrors("password")) {
                String msg = error.getDefaultMessage();
                if (msg.contains("영문자와 숫자를") && !passwordErrors.contains(msg)) {
                    passwordErrors.add(msg);
                }
            }

            List<String> otherErrors = result.getFieldErrors().stream()
                    .filter(e -> !e.getField().equals("password"))
                    .map(FieldError::getDefaultMessage)
                    .distinct()
                    .collect(Collectors.toList());

            String errorMessage = Stream.concat(passwordErrors.stream(), otherErrors.stream())
                    .collect(Collectors.joining("\n"));

            response.put("error", errorMessage);
            return response;
        }

        authService.register(userRequest);
        response.put("message", "회원가입이 완료되었습니다.");
        return response;
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        User user = authService.login(email, password);
        // ✅ role 포함하여 토큰 생성
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        return response;
    }
}
