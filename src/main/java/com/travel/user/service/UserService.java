package com.travel.user.service;

import com.travel.global.exception.BusinessException;
import com.travel.global.exception.ErrorCode;
import com.travel.global.security.JwtTokenProvider;
import com.travel.user.dto.UserLoginRequest;
import com.travel.user.dto.UserResponse;
import com.travel.user.dto.UserSignUpRequest;
import com.travel.user.entity.User;
import com.travel.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.refresh-store-enabled:false}")
    private boolean refreshStoreEnabled;

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
    public AuthenticationTokens login(UserLoginRequest request) {

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INVALID_LOGIN)
                );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new BusinessException(
                    ErrorCode.INVALID_LOGIN
            );
        }

        return issueTokens(user);
    }

    public AuthenticationTokens refresh(String refreshToken) {
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        if (refreshStoreEnabled) {
            String storedToken = stringRedisTemplate.opsForValue()
                    .get(refreshTokenKey(userId));

            if (!tokensEqual(storedToken, refreshToken)) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.USER_NOT_FOUND)
                );

        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        if (refreshToken == null
                || !jwtTokenProvider.validateRefreshToken(refreshToken)) {
            return;
        }

        if (!refreshStoreEnabled) {
            return;
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String key = refreshTokenKey(userId);
        String storedToken = stringRedisTemplate.opsForValue().get(key);

        if (tokensEqual(storedToken, refreshToken)) {
            stringRedisTemplate.delete(key);
        }
    }

    private AuthenticationTokens issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getEmail()
        );
        String refreshToken = jwtTokenProvider.createRefreshToken(
                user.getId(), user.getEmail()
        );

        if (refreshStoreEnabled) {
            stringRedisTemplate.opsForValue().set(
                    refreshTokenKey(user.getId()),
                    refreshToken,
                    Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpiration())
            );
        }

        return new AuthenticationTokens(accessToken, refreshToken);
    }

    private String refreshTokenKey(Long userId) {
        return REFRESH_TOKEN_KEY_PREFIX + userId;
    }

    private boolean tokensEqual(String first, String second) {
        if (first == null || second == null) {
            return false;
        }

        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8)
        );
    }

    public record AuthenticationTokens(
            String accessToken,
            String refreshToken
    ) {
    }
}
