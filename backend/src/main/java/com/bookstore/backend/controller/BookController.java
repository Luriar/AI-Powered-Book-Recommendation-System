package com.bookstore.backend.controller;

import com.bookstore.backend.model.Book;
import com.bookstore.backend.model.User;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.repository.UserRepository;
import com.bookstore.backend.service.AiSummaryService;
import com.bookstore.backend.service.BookService;
import com.bookstore.backend.service.GoogleBooksService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookRepository bookRepository;
    private final BookService bookService;
    private final GoogleBooksService googleBooksService;
    private final AiSummaryService aiSummaryService;
    private final UserRepository userRepository;

    public BookController(BookRepository bookRepository,
                          BookService bookService,
                          GoogleBooksService googleBooksService,
                          AiSummaryService aiSummaryService,
                          UserRepository userRepository) {
        this.bookRepository = bookRepository;
        this.bookService = bookService;
        this.googleBooksService = googleBooksService;
        this.aiSummaryService = aiSummaryService;
        this.userRepository = userRepository;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBook(@PathVariable Long id) {
        if (bookRepository.existsById(id)) {
            bookRepository.deleteById(id);
            return ResponseEntity.ok("도서가 삭제되었습니다.");
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBookById(@PathVariable Long id) {
        return bookRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<Book>> getBooksByCategory(@PathVariable String category) {
        List<Book> result = bookService.getBooksByCategory(category);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/paged")
    public ResponseEntity<Map<String, Object>> getBooksPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size) {
        Page<Book> bookPage = bookService.getBooksPaged(page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("books", bookPage.getContent());
        response.put("totalPages", bookPage.getTotalPages());
        response.put("currentPage", bookPage.getNumber());
        response.put("totalItems", bookPage.getTotalElements());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/category-paged")
    public ResponseEntity<Map<String, Object>> getBooksByCategoryPaged(
            @RequestParam String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size) {

        Page<Book> bookPage = bookService.getBooksByCategoryPaged(category, page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("books", bookPage.getContent());
        response.put("totalPages", bookPage.getTotalPages());
        response.put("currentPage", bookPage.getNumber());
        response.put("totalItems", bookPage.getTotalElements());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search-paged")
    public ResponseEntity<Map<String, Object>> searchBooksPaged(
            @RequestParam("q") String query,
            @RequestParam(value = "type", defaultValue = "all") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size) {

        Page<Book> bookPage;

        switch (type.toLowerCase()) {
            case "title":
                bookPage = bookService.searchByTitlePaged(query, page, size);
                break;
            case "author":
                bookPage = bookService.searchByAuthorPaged(query, page, size);
                break;
            default:
                bookPage = bookService.searchBooksPaged(query, page, size);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("books", bookPage.getContent());
        response.put("totalPages", bookPage.getTotalPages());
        response.put("currentPage", bookPage.getNumber());
        response.put("totalItems", bookPage.getTotalElements());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Book>> searchBooks(
            @RequestParam("q") String query,
            @RequestParam(value = "type", defaultValue = "all") String type) {

        List<Book> results;

        switch (type.toLowerCase()) {
            case "title":
                results = bookService.searchByTitle(query);
                break;
            case "author":
                results = bookService.searchByAuthor(query);
                break;
            default:
                results = bookService.searchBooks(query);
        }

        return ResponseEntity.ok(results);
    }

    //  도서 찜 추가
    @PostMapping("/{bookId}/favorite")
    public ResponseEntity<?> addFavorite(@PathVariable Long bookId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        Optional<Book> bookOpt = bookRepository.findById(bookId);

        if (userOpt.isPresent() && bookOpt.isPresent()) {
            User user = userOpt.get();
            Book book = bookOpt.get();

            user.getFavoriteBooks().add(book);
            userRepository.save(user);
            return ResponseEntity.ok("도서를 찜했습니다.");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("도서 또는 사용자를 찾을 수 없습니다.");
    }

    //  도서 대출
    @PostMapping("/{id}/borrow")
    public ResponseEntity<?> borrowBook(@PathVariable Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            bookService.borrowBook(id, email);
            return ResponseEntity.ok("도서 대출 완료");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("대출 실패: " + e.getMessage());
        }
    }

    //  도서 반납
    @PostMapping("/{id}/return")
    public ResponseEntity<?> returnBook(@PathVariable Long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            bookService.returnBook(id, email);
            return ResponseEntity.ok("도서 반납 완료");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("반납 실패: " + e.getMessage());
        }
    }

    @PostMapping("/google/isbn/{isbn}")
    public ResponseEntity<?> addBookFromGoogle(@PathVariable String isbn) {
        try {
            Book savedBook = googleBooksService.saveBookByIsbn(isbn);
            return ResponseEntity.ok(savedBook);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping(value = "/custom", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addCustomBook(
            @RequestPart("book") Book book,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        try {
            Book saved = bookService.saveBookWithImage(book, image);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("등록 실패: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류: " + e.getMessage());
        }
    }

    @PostMapping("/register-isbn")
    public ResponseEntity<?> registerBookByIsbn(@RequestBody Map<String, String> body) {
        String isbn = body.get("isbn");
        try {
            Book savedBook = googleBooksService.saveBookByIsbn(isbn);

            ResponseEntity<?> response = ResponseEntity.ok(savedBook);

            //  비동기로 요약 처리 (간단히 스레드로 처리)
            new Thread(() -> {
                try {
                    aiSummaryService.summarizeAndFill(savedBook);
                    bookRepository.save(savedBook);
                    System.out.println("✅ 요약 완료 및 저장됨: " + savedBook.getTitle());
                } catch (IOException e) {
                    System.err.println("❌ 요약 실패: " + e.getMessage());
                }
            }).start();

            return response;

        } catch (IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("서버 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PostMapping(value = "/manual", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Book addBookManual(
            @RequestPart("book") Book book,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return bookService.saveBookWithImage(book, image);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBook(@PathVariable Long id, @RequestBody Book updatedBook) {
        return bookRepository.findById(id)
                .map(book -> {
                    book.setTitle(updatedBook.getTitle());
                    book.setAuthor(updatedBook.getAuthor());
                    book.setPublisher(updatedBook.getPublisher());
                    book.setCategory(updatedBook.getCategory());
                    book.setPrice(updatedBook.getPrice());
                    book.setSummary(updatedBook.getSummary());
                    return ResponseEntity.ok(bookRepository.save(book));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateBook(@PathVariable Long id,
                                        @RequestPart("book") Book updatedBook,
                                        @RequestPart(value = "image", required = false) MultipartFile image) {
        try {
            Book updated = bookService.updateBookWithImage(id, updatedBook, image);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    //  도서 찜 해제
    @DeleteMapping("/{bookId}/favorite")
    public ResponseEntity<?> removeFavorite(@PathVariable Long bookId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> userOpt = userRepository.findByEmail(email);
        Optional<Book> bookOpt = bookRepository.findById(bookId);

        if (userOpt.isPresent() && bookOpt.isPresent()) {
            User user = userOpt.get();
            Book book = bookOpt.get();

            user.getFavoriteBooks().remove(book);
            userRepository.save(user);
            return ResponseEntity.ok("도서 찜을 해제했습니다.");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("도서 또는 사용자를 찾을 수 없습니다.");
    }
}
