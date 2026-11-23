package com.bookstore.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserRequest {

    @NotBlank(message = "이름은 필수입니다.")
    private String username;

    @Email(message = "올바른 이메일 형식이어야 합니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]*$",
            message = "비밀번호는 최소 1개 이상의 영문자와 숫자를 포함해야 합니다.")
    private String password;
    private List<String> categories; // 관심 카테고리
}
