package com.bookstore.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "book")  //  @UniqueConstraint 제거
@Getter
@Setter
@NoArgsConstructor
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String author;
    private String publisher;
    private int price;

    @Column(columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "isbn", unique = true)
    private String isbn;

    @Column(name = "isbnPrefix", length = 12, nullable = true)
    private String isbnPrefix;

    private String category;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, length = 20)
    private String edition = "1판";

    @Column(name = "image_url")
    private String imageUrl;

    @ManyToMany(mappedBy = "recommendedBooks") //  추가
    @JsonIgnore
    private Set<User> recommendedByUsers = new HashSet<>();

    @ManyToMany(mappedBy = "borrowedBooks")
    @JsonIgnore
    private Set<User> borrowers = new HashSet<>();

    @Column(nullable = false)
    private int stock = 1;  // 기본값 1
}
