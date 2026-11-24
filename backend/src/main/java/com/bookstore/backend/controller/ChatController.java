package com.bookstore.backend.controller;

import com.bookstore.backend.dto.ChatRequest;
import com.bookstore.backend.dto.ChatResponse;
import com.bookstore.backend.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/chatbot")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse askChatbot(@RequestBody ChatRequest request) {
        System.out.println("📩 SpringBoot: 사용자 질문 수신 = " + request.getQuestion());
        ChatResponse response = chatService.getRecommendation(request);
        System.out.println("📩 SpringBoot: 추천 응답 반환 = " + response.getAnswer());
        return response;
    }
}
