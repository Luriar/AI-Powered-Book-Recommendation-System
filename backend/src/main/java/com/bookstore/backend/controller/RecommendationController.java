package com.bookstore.backend.controller;

import com.bookstore.backend.model.Book;
import com.bookstore.backend.service.PersonalRecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommend")
public class RecommendationController {

    private final PersonalRecommendationService recommendationService;

    public RecommendationController(PersonalRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/personal")
    public ResponseEntity<List<Book>> getPersonalRecommendations(@RequestHeader("Authorization") String authHeader) {
        List<Book> recommendations = recommendationService.getPersonalRecommendations(authHeader);
        return ResponseEntity.ok(recommendations);
    }
}
