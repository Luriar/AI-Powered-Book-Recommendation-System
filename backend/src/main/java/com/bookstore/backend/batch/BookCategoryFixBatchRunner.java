package com.bookstore.backend.batch;

import com.bookstore.backend.model.Book;
import com.bookstore.backend.repository.BookRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.common.util.concurrent.RateLimiter;

@Component
public class BookCategoryFixBatchRunner implements CommandLineRunner {

    private final BookRepository bookRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int THREAD_POOL_SIZE = 12;
    private static final RateLimiter LIMITER = RateLimiter.create(4.0);

    private static final Set<String> VALID_CATEGORIES = Set.of(
            "소설", "시", "에세이", "인문", "가정/육아", "요리", "건강",
            "취미/실용/스포츠", "경제/경영", "자기계발", "정치/사회", "역사/문화",
            "종교", "예술/대중문화", "중/고등참고서", "기술/공학", "외국어", "과학",
            "취업/수험서", "여행", "컴퓨터/IT", "잡지", "청소년", "초등참고서",
            "유아(0~7세)", "어린이(초등)", "만화", "대학교재"
    );

    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(60))
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .build();

    @Value("${deepseek.api.key}")
    private String apiKey;

    public BookCategoryFixBatchRunner(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Override
    public void run(String... args) throws InterruptedException {
        var books = bookRepository.findAll().stream()
                .filter(book -> book.getCategory() == null || !VALID_CATEGORIES.contains(book.getCategory()))
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        var tasks = books.stream().map(book -> (Callable<Void>) () -> {
            try {
                LIMITER.acquire();
                String prompt = buildPrompt(book);
                String responseText = callDeepSeek(prompt);
                String category = extractCategory(responseText);

                if (VALID_CATEGORIES.contains(category)) {
                    book.setCategory(category);
                    bookRepository.save(book);
                    System.out.println("✅ 카테고리 재설정 완료: " + book.getTitle() + " → " + category);
                } else {
                    System.out.println("⚠️ 잘못된 응답: " + book.getTitle() + " → " + category);
                }
            } catch (Exception e) {
                System.err.println("❌ 실패: " + book.getTitle() + " - " + e.getMessage());
            }
            return null;
        }).toList();

        pool.invokeAll(tasks);
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.HOURS);
    }

    private String buildPrompt(Book book) {
        return String.format("""
책 정보를 참고하여 아래 카테고리 목록 중 하나를 정확히 선택해 주세요.
응답은 반드시 아래 형식으로 해주세요:

[카테고리]: (택1)

카테고리 목록:
- 소설
- 시
- 에세이
- 인문
- 가정/육아
- 요리
- 건강
- 취미/실용/스포츠
- 경제/경영
- 자기계발
- 정치/사회
- 역사/문화
- 종교
- 예술/대중문화
- 중/고등참고서
- 기술/공학
- 외국어
- 과학
- 취업/수험서
- 여행
- 컴퓨터/IT
- 잡지
- 청소년
- 초등참고서
- 유아(0~7세)
- 어린이(초등)
- 만화
- 대학교재

제목: %s
저자: %s
출판사: %s
""",
                book.getTitle(),
                book.getAuthor() != null ? book.getAuthor() : "정보 없음",
                book.getPublisher() != null ? book.getPublisher() : "정보 없음"
        );
    }

    private String callDeepSeek(String prompt) throws IOException {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "user");
        message.put("content", prompt);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", "deepseek-reasoner");
        payload.set("messages", objectMapper.createArrayNode().add(message));

        Request request = new Request.Builder()
                .url("https://api.deepseek.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsBytes(payload), MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("DeepSeek 응답 실패: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            return root.get("choices").get(0).get("message").get("content").asText();
        }
    }

    private String extractCategory(String responseText) {
        Matcher matcher = Pattern.compile("\\[카테고리\\]:\\s*(.+)").matcher(responseText);
        return matcher.find() ? matcher.group(1).trim() : "기타";
    }
}
