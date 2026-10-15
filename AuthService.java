package com.taskflow.domain.service;

import com.taskflow.api.dto.request.LoginRequest;
import com.taskflow.api.dto.request.RegisterRequest;
import com.taskflow.api.dto.response.AuthResponse;
import com.taskflow.domain.entity.AppUser;
import com.taskflow.domain.repository.UserRepository;
import com.taskflow.exception.BadRequestException;
import com.taskflow.exception.ConflictException;
import com.taskflow.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validatePasswordLength(request.password());
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        userRepository.save(new AppUser(email, passwordEncoder.encode(request.password())));
        return issueToken(email);
    }

    public AuthResponse login(LoginRequest request) {
        validatePasswordLength(request.password());
        String email = normalizeEmail(request.email());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        return issueToken(email);
    }

    private AuthResponse issueToken(String email) {
        Instant issuedAt = Instant.now();
        return new AuthResponse(jwtService.generateToken(email, issuedAt), "Bearer",
                jwtService.expiresAt(issuedAt));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException(
                    "Password must not exceed 72 UTF-8 bytes when using BCrypt.");
        }
    }
}
