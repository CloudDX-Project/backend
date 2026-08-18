package com.travel.user.service;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.user.dto.UserResponse;
import com.travel.user.dto.UserSignUpRequest;
import com.travel.user.entity.User;
import com.travel.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse signUp(UserSignUpRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_EMAIL
            );
        }

        User user = User.builder()
                .email(request.email())
                .password(
                        passwordEncoder.encode(request.password())
                )
                .nickname(request.nickname())
                .build();

        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }
}