package com.bookstore.backend.repository;

import com.bookstore.backend.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByIsbn(String isbn);

    List<Book> findAllByOrderByIdAsc();

    boolean existsByIsbnPrefix(String isbnPrefix);

    // 🔍 제목 또는 저자에 검색어가 포함된 도서 리스트 조회
    List<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title, String author);

    // 단일 기준 검색용
    List<Book> findByTitleContainingIgnoreCase(String title);
    List<Book> findByAuthorContainingIgnoreCase(String author);

    //카테고리
    List<Book> findByCategoryIgnoreCase(String category);

    //페이지
    Page<Book> findByCategoryIgnoreCase(String category, Pageable pageable);
    Page<Book> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    Page<Book> findByAuthorContainingIgnoreCase(String author, Pageable pageable);
    Page<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title, String author, Pageable pageable);

    Optional<Book> findByTitle(String title);
}
