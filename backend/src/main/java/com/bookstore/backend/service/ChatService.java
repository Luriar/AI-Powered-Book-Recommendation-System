package com.bookstore.backend.service;

import com.bookstore.backend.dto.ChatRequest;
import com.bookstore.backend.dto.ChatResponse;
import com.bookstore.backend.model.Book;
import com.bookstore.backend.model.User;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class ChatService {

    private final String PYTHON_SERVER_URL = "http://127.0.0.1:5000/api/chat";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    public ChatService() {
        restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    public ChatResponse getRecommendation(ChatRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);

        try {
            System.out.println("️ Spring Boot → Flask 요청: " + PYTHON_SERVER_URL);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    PYTHON_SERVER_URL,
                    entity,
                    String.class
            );
            System.out.println(" Flask 응답 코드: " + response.getStatusCode());
            System.out.println(" Flask 응답 본문: " + response.getBody());

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Flask 서버 응답 에러: " + response.getStatusCode());
            }

            String body = response.getBody();
            if (body == null) {
                throw new RuntimeException("Flask 서버 응답이 null입니다.");
            }

            //  JSON 파싱 후 answer 추출
            JsonNode jsonNode = objectMapper.readTree(body);
            String rawAnswer = jsonNode.path("answer").asText();

            //  유니코드 이스케이프 처리
            String decodedAnswer = StringEscapeUtils.unescapeJava(rawAnswer);

            //  도서 제목 추출
            String title = null;
            int idx = decodedAnswer.indexOf("추천도서 : ");
            if (idx != -1) {
                int end = decodedAnswer.indexOf("\n", idx);
                if (end == -1) end = decodedAnswer.length();
                title = decodedAnswer.substring(idx + "추천도서 : ".length(), end).trim();
                System.out.println(" 추천 도서 제목 추출: " + title);
            }

            //  현재 로그인한 사용자 이메일 가져오기
            String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            System.out.println(" 로그인 사용자 이메일: " + userEmail);

            //  추천 도서 저장
            if (title != null && userEmail != null) {
                logRecommendation(title, userEmail);
            }

            return new ChatResponse(decodedAnswer);

        } catch (Exception e) {
            System.err.println("X Flask 서버 호출 실패 또는 파싱 중 예외 발생");
            e.printStackTrace();
            throw new RuntimeException("Flask 서버 호출 중 예외 발생", e);
        }
    }

    //  추천 도서 로그 저장
    private void logRecommendation(String bookTitle, String userEmail) {
        Optional<User> userOpt = userRepository.findByEmail(userEmail);
        Optional<Book> bookOpt = bookRepository.findByTitle(bookTitle);

        if (userOpt.isPresent() && bookOpt.isPresent()) {
            User user = userOpt.get();
            Book book = bookOpt.get();

            if (!user.getRecommendedBooks().contains(book)) {
                user.getRecommendedBooks().add(book);
                userRepository.save(user);
                System.out.println(" 사용자 추천 도서 저장 완료: " + bookTitle);
            } else {
                System.out.println("️ 이미 추천된 도서입니다: " + bookTitle);
            }
        } else {
            System.out.println("X 도서 또는 사용자 찾기 실패: " + bookTitle + ", " + userEmail);
        }
    }
}
