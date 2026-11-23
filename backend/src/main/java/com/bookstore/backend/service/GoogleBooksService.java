package com.bookstore.backend.service;

import com.bookstore.backend.model.Book;
import com.bookstore.backend.repository.BookRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Service
public class GoogleBooksService {

    private static final RateLimiter GOOGLE_LIMITER = RateLimiter.create(1.0);
    private static final String WRONG_ISBN_FILE = "C:/ai-bookstore/backend/wrong_isbn_list.txt";

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final BookRepository bookRepository;
    private final Path wrongIsbnPath = Paths.get(WRONG_ISBN_FILE);

    private final String apiKey = System.getenv("GOOGLE_BOOKS_API_KEY");

    public GoogleBooksService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public Book saveBookByIsbn(String isbn) throws IOException {
        if (isbn == null || isbn.length() < 12) {
            throw new IllegalArgumentException("잘못된 ISBN입니다. ISBN은 정확히 13자리 숫자여야 합니다.");
        }

        if (!isbn.matches("^\\d{13}$")) {
            throw new IllegalArgumentException("잘못된 ISBN입니다. ISBN은 정확히 13자리 숫자여야 합니다.");
        }

        String prefix = isbn.substring(0, 12);

        if (bookRepository.existsByIsbnPrefix(prefix)) {
            throw new IllegalArgumentException("이미 같은 책이 존재합니다 (ISBN 앞 12자리 기준).");
        }

        String correctedIsbn = correctIsbn(isbn);

        GOOGLE_LIMITER.acquire();

        String url = "https://www.googleapis.com/books/v1/volumes?q=isbn:" + correctedIsbn +
                ((apiKey != null && !apiKey.isBlank()) ? "&key=" + apiKey.trim() : "");

        Request req = new Request.Builder().url(url).get().build();

        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                throw new IOException("Google Books API 응답 실패: " + res.code());
            }

            String body = res.body() != null ? res.body().string() : "";
            JsonNode root = mapper.readTree(body);

            if (!root.has("items") || root.get("items").isEmpty()) {
                recordWrongIsbn(correctedIsbn);
                throw new IOException("도서 정보를 찾을 수 없습니다: " + correctedIsbn);
            }

            JsonNode info = root.get("items").get(0).get("volumeInfo");
            Book book = parseBook(info, correctedIsbn, prefix);
            return bookRepository.save(book);
        }
    }

    private Book parseBook(JsonNode info, String isbn, String prefix) {
        Book b = new Book();
        b.setTitle(info.path("title").asText("").trim());
        b.setAuthor(extractAuthor(info));
        b.setPublisher(info.path("publisher").asText("출판사 정보 없음").trim());
        b.setDescription(info.path("description").asText(""));
        b.setPrice(0);
        b.setIsbn(isbn);
        b.setIsbnPrefix(prefix);
        b.setCategory("기타");
        b.setEdition("1판");
        return b;
    }

    private String extractAuthor(JsonNode info) {
        if (info.has("authors") && info.get("authors").isArray() && !info.get("authors").isEmpty()) {
            return info.get("authors").get(0).asText();
        }
        return "저자 정보 없음";
    }

    private void recordWrongIsbn(String isbn) {
        try {
            synchronized (GoogleBooksService.class) {
                Files.writeString(wrongIsbnPath,
                        isbn + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            System.out.println("❗ wrong_isbn_list.txt 기록 실패: " + e.getMessage());
        }
    }

    private String correctIsbn(String isbn) {
        if (isbn.length() != 13) return isbn;
        String prefix = isbn.substring(0, 12);
        int sum = 0;
        for (int i = 0; i < prefix.length(); i++) {
            int digit = Character.getNumericValue(prefix.charAt(i));
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return prefix + checkDigit;
    }
}
