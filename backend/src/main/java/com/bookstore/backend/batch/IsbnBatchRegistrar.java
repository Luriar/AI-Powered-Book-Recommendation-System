package com.bookstore.backend.batch;

import com.bookstore.backend.model.Book;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.service.AiSummaryService;
import com.bookstore.backend.service.GoogleBooksService;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class IsbnBatchRegistrar implements CommandLineRunner {

    private static final RateLimiter SUMMARY_LIMITER = RateLimiter.create(4.0);

    private final GoogleBooksService googleBooksService;
    private final BookRepository bookRepository;
    private final AiSummaryService aiSummaryService;

    public IsbnBatchRegistrar(GoogleBooksService googleBooksService,
                              BookRepository bookRepository,
                              AiSummaryService aiSummaryService) {
        this.googleBooksService = googleBooksService;
        this.bookRepository = bookRepository;
        this.aiSummaryService = aiSummaryService;
    }

    @Override
    public void run(String... args) {
        final String isbnFilePath = "C:/ai-bookstore/backend/isbn_list.txt";
        final String failedIsbnPath = "C:/ai-bookstore/backend/failed_isbn_list.txt";

        try {
            List<String> isbnList = Files.readAllLines(Paths.get(isbnFilePath));
            isbnList.removeIf(String::isBlank);

            if (isbnList.isEmpty()) {
                System.out.println("无需新增的 ISBN。");
                return;
            }

            System.out.println("[Batch Summary Start] - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            long startNano = System.nanoTime();

            List<String> failedList = new ArrayList<>();
            AtomicInteger okCnt = new AtomicInteger();
            AtomicInteger wrongCnt = new AtomicInteger();
            AtomicInteger failCnt = new AtomicInteger();

            for (String raw : isbnList) {
                String isbn = raw.trim();
                System.out.println("Processing ISBN: " + isbn);

                if (bookRepository.findByIsbn(isbn).isPresent()) {
                    System.out.println("⚠ 已存在: " + isbn);
                    continue;
                }

                try {
                    Book saved = googleBooksService.saveBookByIsbn(isbn);

                    if (saved.getSummary() == null || saved.getSummary().isBlank()) {
                        SUMMARY_LIMITER.acquire();
                        long start = System.nanoTime();
                        aiSummaryService.summarizeAndFill(saved);
                        bookRepository.save(saved);
                        long end = System.nanoTime();
                        long durationMs = (end - start) / 1_000_000;
                        System.out.printf("[✓] Summary generated in %d ms%n", durationMs);
                    } else {
                        System.out.println("[✓] Summary already exists");
                    }

                    okCnt.incrementAndGet();

                } catch (IOException ioe) {
                    String msg = ioe.getMessage();
                    if (msg != null && msg.contains("未找到图书信息")) {
                        System.out.println("? 信息缺失: " + isbn);
                        wrongCnt.incrementAndGet();
                    } else if (msg != null && msg.contains("ISBN 已存在")) {
                        System.out.println("⚠ 检测到重复项: " + isbn);
                    } else {
                        System.out.println("X 失败: " + isbn + " (" + msg + ")");
                        failedList.add(isbn);
                        failCnt.incrementAndGet();
                    }

                } catch (Exception ex) {
                    System.out.println("X 失败: " + isbn + " (" + ex.getMessage() + ")");
                    failedList.add(isbn);
                    failCnt.incrementAndGet();
                }
            }

            Files.write(Paths.get(failedIsbnPath),
                    String.join(System.lineSeparator(), failedList).getBytes());

            Files.write(Paths.get(isbnFilePath), new byte[0]);

            long endNano = System.nanoTime();
            double elapsedSeconds = (endNano - startNano) / 1_000_000_000.0;

            System.out.println("[Batch Summary Completed] - Total: " + isbnList.size() + " books");
            System.out.printf("Total Time Elapsed: %.1f seconds%n", elapsedSeconds);
            System.out.printf("Failed: %d%n", failCnt.get());

        } catch (IOException e) {
            System.out.println("X 读取 ISBN 文件失败: " + e.getMessage());
        }
    }
}
