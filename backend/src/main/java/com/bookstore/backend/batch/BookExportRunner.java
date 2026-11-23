package com.bookstore.backend.batch;

import com.bookstore.backend.model.Book;
import com.bookstore.backend.repository.BookRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

//@Component
public class BookExportRunner implements CommandLineRunner {

    private final BookRepository bookRepository;
    private static final String CSV_PATH = "C:/ai-bookstore/backend/books_for_embedding.csv";

    public BookExportRunner(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Override
    public void run(String... args) {

        List<Book> books = bookRepository.findAll();

        // 1. title + author 로 그룹핑 후, 가장 높은 edition 선택
        Map<String, Book> latestEditionBooks = books.stream()
                .collect(Collectors.toMap(
                        book -> book.getTitle() + "::" + book.getAuthor(),
                        book -> book,
                        (b1, b2) -> compareEdition(b1, b2) >= 0 ? b1 : b2  // edition 높은 책 선택
                ));

        CSVFormat format = CSVFormat.DEFAULT
                .builder()
                .setHeader("id", "title", "summary")
                .setRecordSeparator(System.lineSeparator())
                .build();

        try (Writer bw = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(CSV_PATH), StandardCharsets.UTF_8))) {

            bw.write('\uFEFF');  // BOM 추가

            try (CSVPrinter csv = new CSVPrinter(bw, format)) {
                for (Book book : latestEditionBooks.values()) {
                    String title = safeField(book.getTitle());
                    String summary = safeField(book.getSummary());
                    csv.printRecord(book.getId(), title, summary);
                }
            }
            System.out.printf("✅ 최신판만 남긴 책 CSV (%d권) 저장 완료 → %s%n", latestEditionBooks.size(), CSV_PATH);

        } catch (Exception e) {
            System.out.println("❌ CSV 저장 실패: " + e.getMessage());
        }
    }

    private String safeField(String value) {
        return value != null ? value.replace(",", " ").replace("\n", " ") : "없음";
    }

    private int compareEdition(Book b1, Book b2) {
        try {
            int e1 = Integer.parseInt(b1.getEdition().replace("판", "").trim());
            int e2 = Integer.parseInt(b2.getEdition().replace("판", "").trim());
            return Integer.compare(e1, e2);
        } catch (NumberFormatException e) {
            return 0; // 비교 불가하면 그냥 같다고 처리
        }
    }
}
