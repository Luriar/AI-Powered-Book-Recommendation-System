package com.bookstore.backend.batch;

import com.bookstore.backend.model.Book;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.service.AiSummaryService;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.*;

/**
 * 요약이 없는 도서들을 대상으로
 * AI 요약 + 카테고리 자동 추론을 병렬로 처리하는 배치
 */
@Component
public class BookSummaryBatchRunner implements CommandLineRunner {

    private final BookRepository bookRepository;
    private final AiSummaryService aiSummaryService;

    private static final int THREAD_POOL_SIZE = 12;
    private static final RateLimiter LIMITER = RateLimiter.create(4.0); // 초당 4회 DeepSeek 호출 제한

    public BookSummaryBatchRunner(BookRepository bookRepository, AiSummaryService aiSummaryService) {
        this.bookRepository = bookRepository;
        this.aiSummaryService = aiSummaryService;
    }

    @Override
    public void run(String... args) throws InterruptedException {
        List<Book> booksToSummarize = bookRepository.findAll().stream()
                .filter(book -> {
                    String summary = book.getSummary();
                    return summary == null || summary.isBlank() || summary.startsWith("AI 응답 실패:");
                })
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Callable<Void>> tasks = booksToSummarize.stream()
                .map(book -> (Callable<Void>) () -> {
                    try {
                        LIMITER.acquire();
                        aiSummaryService.summarizeAndFill(book); // 요약 + 카테고리 자동 추론
                        bookRepository.save(book);
                        System.out.println("✅ 요약 및 카테고리 저장 완료: " + book.getTitle());
                    } catch (Exception e) {
                        // 실패 시 summary 필드에 그대로 에러 메세지를 남김
                        book.setSummary("AI 응답 실패: " + e.getMessage());
                        bookRepository.save(book);
                        System.err.println("❌ 요약 실패: " + book.getTitle());
                    }
                    return null;
                })
                .toList();

        pool.invokeAll(tasks);
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.HOURS);

        System.out.println("所有图书摘要生成工作已完成。");
    }
}
