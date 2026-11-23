package com.bookstore.backend.service;

import com.bookstore.backend.model.Book;
import com.bookstore.backend.model.User;
import com.bookstore.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    //  추천 도서 목록 조회
    public List<Book> getRecommendedBooks(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            return new ArrayList<>(userOpt.get().getRecommendedBooks());
        }
        return Collections.emptyList();
    }

    //  (선택) 찜한 도서 목록 조회도 추가할 수 있음
    public List<Book> getFavoriteBooks(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            return new ArrayList<>(userOpt.get().getFavoriteBooks());
        }
        return Collections.emptyList();
    }
}
