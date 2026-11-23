package com.bookstore.backend.service;

import com.bookstore.backend.model.Book;
import com.bookstore.backend.model.User;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.repository.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;

@Service
public class BookService {

    /** 로컬 이미지 저장 디렉터리 (필요하면 변경) */
    private static final String IMAGE_DIR = "C:/ai-bookstore/images/";

    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public BookService(BookRepository bookRepository,
                       UserRepository userRepository) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    /*──────────────────────────────
     *  1.  기본 도서 등록 / 수정
     *─────────────────────────────*/

    public Book saveBook(Book book) {
        fillDefaults(book);
        handleIsbnPrefix(book, true);
        return bookRepository.save(book);
    }

    public Book updateBook(Long id, Book newBook) {
        return bookRepository.findById(id)
                .map(book -> {
                    copyUpdatableFields(newBook, book);
                    handleIsbnPrefix(book, false);
                    return bookRepository.save(book);
                })
                .orElseThrow(() -> new RuntimeException("Book not found"));
    }

    /*──────────────────────────────
     *  2.  이미지 포함 등록 / 수정
     *─────────────────────────────*/

    public Book saveBookWithImage(Book book, MultipartFile image) {
        fillDefaults(book);
        handleIsbnPrefix(book, true);
        saveImageIfPresent(book, image);
        return bookRepository.save(book);
    }

    public Book updateBookWithImage(Long id, Book newBook, MultipartFile image) {
        return bookRepository.findById(id)
                .map(book -> {
                    // 기존 이미지 제거
                    deleteImageIfExists(book.getImageUrl());

                    copyUpdatableFields(newBook, book);
                    handleIsbnPrefix(book, false);
                    saveImageIfPresent(book, image);
                    return bookRepository.save(book);
                })
                .orElseThrow(() -> new RuntimeException("Book not found"));
    }

    /*──────────────────────────────
     *  3.  조회 / 검색 / 페이지네이션
     *─────────────────────────────*/

    public List<Book> getAllBooks()               { return bookRepository.findAll(); }
    public Optional<Book> getBookById(Long id)    { return bookRepository.findById(id); }
    public void deleteBook(Long id)               { bookRepository.deleteById(id); }

    public List<Book> searchBooks(String kw)      { return bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(kw, kw); }
    public List<Book> searchByTitle(String kw)    { return bookRepository.findByTitleContainingIgnoreCase(kw); }
    public List<Book> searchByAuthor(String kw)   { return bookRepository.findByAuthorContainingIgnoreCase(kw); }
    public List<Book> getBooksByCategory(String c){ return bookRepository.findByCategoryIgnoreCase(c); }

    public Page<Book> getBooksPaged(int p,int s)                    { return bookRepository.findAll(PageRequest.of(p, s)); }
    public Page<Book> getBooksByCategoryPaged(String c,int p,int s) { return bookRepository.findByCategoryIgnoreCase(c, PageRequest.of(p, s)); }
    public Page<Book> searchByTitlePaged(String kw,int p,int s)     { return bookRepository.findByTitleContainingIgnoreCase(kw, PageRequest.of(p, s)); }
    public Page<Book> searchByAuthorPaged(String kw,int p,int s)    { return bookRepository.findByAuthorContainingIgnoreCase(kw, PageRequest.of(p, s)); }
    public Page<Book> searchBooksPaged(String kw,int p,int s)       { return bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(kw, kw, PageRequest.of(p, s)); }

    /*──────────────────────────────
     *  4.  대출 / 반납
     *─────────────────────────────*/

    public void borrowBook(Long bookId, String email) {
        Book book = bookRepository.findById(bookId).orElseThrow(() -> new RuntimeException("Book not found"));
        User user = userRepository.findByEmail(email) .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getBorrowedBooks().contains(book))          throw new IllegalStateException("이미 대출한 도서입니다.");
        if (book.getStock() <= 0)                            throw new IllegalStateException("재고가 없습니다.");

        user.getBorrowedBooks().add(book);
        book.getBorrowers().add(user);
        book.setStock(book.getStock() - 1);

        bookRepository.save(book);
        userRepository.save(user);
    }

    public void returnBook(Long bookId, String email) {
        Book book = bookRepository.findById(bookId).orElseThrow(() -> new RuntimeException("Book not found"));
        User user = userRepository.findByEmail(email) .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getBorrowedBooks().contains(book))         throw new IllegalStateException("이 도서를 대출하지 않았습니다.");

        user.getBorrowedBooks().remove(book);
        book.getBorrowers().remove(user);
        book.setStock(book.getStock() + 1);

        bookRepository.save(book);
        userRepository.save(user);
    }

    /*──────────────────────────────
     *  5.  내부 헬퍼 메서드
     *─────────────────────────────*/

    /** edition·stock 등 기본값 채우기 */
    private void fillDefaults(Book book) {
        if (book.getEdition() == null || book.getEdition().isBlank()) book.setEdition("1판");
        if (book.getStock()   <= 0)                                    book.setStock(1);
    }

    /** ISBN → 12자리 prefix 저장 & 중복 검사 (등록 시만) */
    private void handleIsbnPrefix(Book book, boolean checkDuplicate) {
        if (book.getIsbn() != null && book.getIsbn().length() >= 12) {
            String prefix = book.getIsbn().substring(0, 12);
            if (checkDuplicate && bookRepository.existsByIsbnPrefix(prefix))
                throw new IllegalArgumentException("이미 같은 책이 존재합니다.");
            book.setIsbnPrefix(prefix);
        } else {
            book.setIsbnPrefix(null);
        }
    }

    /** 이미지 저장 */
    private void saveImageIfPresent(Book book, MultipartFile image) {
        if (image == null || image.isEmpty()) return;

        try {
            Files.createDirectories(Paths.get(IMAGE_DIR));
            String fileName = System.currentTimeMillis() + "_" + image.getOriginalFilename();
            Path target     = Paths.get(IMAGE_DIR, fileName);
            image.transferTo(target);                                           // ✅ 간단히 저장
            book.setImageUrl("/images/" + fileName);
        } catch (Exception e) {
            System.err.println("❌ 이미지 저장 실패: " + e.getMessage());       // 서비스 전체 중단 방지
        }
    }

    /** 이미지 파일 삭제 */
    private void deleteImageIfExists(String rawUrl) {
        if (rawUrl == null) return;
        try {
            Path path = Paths.get("C:/ai-bookstore", rawUrl.replaceFirst("^/", ""));
            Files.deleteIfExists(path);
        } catch (Exception e) {
            System.err.println("⚠️ 이미지 삭제 실패: " + e.getMessage());
        }
    }

    /** 엔티티 필드 복사(업데이트용) */
    private void copyUpdatableFields(Book src, Book dest) {
        dest.setTitle(src.getTitle());
        dest.setAuthor(src.getAuthor());
        dest.setPublisher(src.getPublisher());
        dest.setPrice(src.getPrice());
        dest.setDescription(src.getDescription());
        dest.setIsbn(src.getIsbn());
        dest.setCategory(src.getCategory());
        dest.setEdition(src.getEdition());
        dest.setStock(src.getStock());
    }
}
