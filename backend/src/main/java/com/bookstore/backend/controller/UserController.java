package com.bookstore.backend.controller;

import com.bookstore.backend.service.BookService; // ✅ 추가
import com.bookstore.backend.service.UserService; // ✅ 추가
import com.bookstore.backend.model.Book;          // ✅ 추가
import com.bookstore.backend.dto.ChangePasswordRequest;
import com.bookstore.backend.model.User;
import com.bookstore.backend.repository.BookRepository;
import com.bookstore.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;                            // ✅ 추가
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService; // ✅ 추가
    private final BookService bookService;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder, BookService bookService, UserService userService, BookRepository bookRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bookService = bookService;
        this.userService = userService; // ✅ 초기화 추가
        this.bookRepository = bookRepository;
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteAccount() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("사용자를 찾을 수 없습니다.");
        }

        userRepository.delete(optionalUser.get());
        return ResponseEntity.ok("회원탈퇴가 완료되었습니다.");
    }

    @DeleteMapping("/me/recommendation/{bookId}")
    public ResponseEntity<?> deleteRecommendation(@PathVariable Long bookId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);
        Optional<Book> optionalBook = bookRepository.findById(bookId);

        if (optionalUser.isPresent() && optionalBook.isPresent()) {
            User user = optionalUser.get();
            Book book = optionalBook.get();

            if (user.getRecommendedBooks().contains(book)) {
                user.getRecommendedBooks().remove(book);
                userRepository.save(user);
                return ResponseEntity.ok("추천 기록 제거 완료");
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("해당 도서가 추천 목록에 없음");
            }
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("사용자 또는 도서 없음");
    }

    // ✅ 로그인된 사용자 정보 반환
    @GetMapping("/me")
    public ResponseEntity<?> getMyInfo() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();

            String username = user.getUsername() != null ? user.getUsername() : "";
            String interests = user.getInterests() != null ? user.getInterests() : "";

            return ResponseEntity.ok(Map.of(
                    "email", user.getEmail(),
                    "name", username,
                    "preferredCategories", interests
            ));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("사용자를 찾을 수 없습니다.");
        }
    }

    @GetMapping("/me/borrowed")
    public ResponseEntity<List<Book>> getMyBorrowedBooks() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isPresent()) {
            List<Book> books = new ArrayList<>(optionalUser.get().getBorrowedBooks());
            return ResponseEntity.ok(books);
        }

        return ResponseEntity.status(404).build();
    }

    @GetMapping("/me/favorites")
    public ResponseEntity<List<Book>> getMyFavoriteBooks() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Book> books = userService.getFavoriteBooks(email);
        return ResponseEntity.ok(books);
    }

    @GetMapping("/me/recommendations")
    public ResponseEntity<List<Book>> getMyRecommendedBooks() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Book> books = userService.getRecommendedBooks(email);
        return ResponseEntity.ok(books);
    }

    // ✅ 도서 대출
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

    // ✅ 도서 반납
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

    // ✅ 관심사 수정
    @PutMapping("/interests")
    public ResponseEntity<?> updateInterests(@RequestBody Map<String, String> body) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        String interests = body.get("interests");

        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setInterests(interests);
            userRepository.save(user);
            return ResponseEntity.ok("관심사가 업데이트되었습니다.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("사용자를 찾을 수 없습니다.");
        }
    }

    // ✅ 비밀번호 변경
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("사용자를 찾을 수 없습니다.");
        }

        User user = optionalUser.get();

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("현재 비밀번호가 일치하지 않습니다.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return ResponseEntity.ok("비밀번호가 변경되었습니다.");
    }
}
