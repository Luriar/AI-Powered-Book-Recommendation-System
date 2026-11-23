package com.bookstore.backend.service;

import com.bookstore.backend.model.Book;
import com.bookstore.backend.model.User;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.repository.UserRepository;
import com.bookstore.backend.util.JwtUtil;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PersonalRecommendationService {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final RestTemplate restTemplate;
    private final JwtUtil jwtUtil;

    public PersonalRecommendationService(UserRepository userRepository, BookRepository bookRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.jwtUtil = jwtUtil;
        this.restTemplate = new RestTemplate();
    }

    public List<Book> getPersonalRecommendations(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String userEmail = jwtUtil.getEmailFromToken(token);
        User user = userRepository.findByEmail(userEmail).orElseThrow();

        // ✅ lazy-loading 강제 초기화
        user.getFavoriteBooks().size();
        user.getRecommendedBooks().size();

        List<Long> favoriteBookIds = user.getFavoriteBooks().stream()
                .map(Book::getId)
                .toList();

        List<Long> recommendedBookIds = user.getRecommendedBooks().stream()
                .map(Book::getId)
                .toList();

        List<String> preferredCategories = new ArrayList<>();
        if (user.getInterests() != null && !user.getInterests().isBlank()) {
            preferredCategories = Arrays.stream(user.getInterests().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        if (favoriteBookIds.isEmpty() && recommendedBookIds.isEmpty() && preferredCategories.isEmpty()) {
            return List.of();  // 아무 정보 없음 → 추천 불가
        }

        // Flask 호출
        String flaskUrl = "http://127.0.0.1:5000/api/recommend/by_user_data";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("favorite_book_ids", favoriteBookIds);
        requestBody.put("recommended_book_ids", recommendedBookIds);
        requestBody.put("preferred_categories", preferredCategories);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Long[]> response = restTemplate.postForEntity(flaskUrl, entity, Long[].class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            List<Long> bookIds = Arrays.asList(response.getBody());
            return bookRepository.findAllById(bookIds);
        } else {
            return List.of();
        }
    }
}
