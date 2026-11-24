package com.blackcoat.api.auth.service;

import com.blackcoat.api.auth.model.AuthDtos;
import com.blackcoat.api.auth.model.RefreshToken;
import com.blackcoat.api.auth.model.User;
import com.blackcoat.api.auth.repository.RefreshTokenRepository;
import com.blackcoat.api.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenExpirationMs;

    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        @Value("${blackcoat.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        String normalizedEmail = req.email().toLowerCase().trim();
        if (userRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        String sanitizedName = "Advocate";
        if (req.displayName() != null) {
            sanitizedName = req.displayName().replaceAll("<[^>]*>", "").trim();
            if (sanitizedName.isEmpty()) sanitizedName = "Advocate";
            if (sanitizedName.length() > 80) sanitizedName = sanitizedName.substring(0, 80);
        }

        String hashedPassword = passwordEncoder.encode(req.password());
        User user = new User(
            normalizedEmail,
            hashedPassword,
            sanitizedName,
            req.langPref() != null && !req.langPref().isBlank() ? req.langPref().trim() : "en"
        );
        user = userRepository.save(user);

        return createAuthSession(user);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(req.email().toLowerCase().trim())
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        return createAuthSession(user);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshTokenRequest req) {
        String tokenHash = hashToken(req.refreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
            .orElseThrow(() -> new IllegalArgumentException("Invalid or revoked refresh token"));

        if (storedToken.isExpired()) {
            storedToken.setRevokedAt(OffsetDateTime.now());
            refreshTokenRepository.save(storedToken);
            throw new IllegalArgumentException("Refresh token has expired");
        }

        // Token rotation: revoke current token and issue new
        storedToken.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(storedToken);

        User user = userRepository.findById(storedToken.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User no longer exists"));

        return createAuthSession(user);
    }

    private AuthDtos.AuthResponse createAuthSession(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String rawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String tokenHash = hashToken(rawRefreshToken);

        OffsetDateTime expiresAt = OffsetDateTime.now().plusNanos(refreshTokenExpirationMs * 1_000_000);
        RefreshToken refreshToken = new RefreshToken(user.getId(), tokenHash, expiresAt);
        refreshTokenRepository.save(refreshToken);

        return new AuthDtos.AuthResponse(
            accessToken,
            rawRefreshToken,
            jwtService.getAccessTokenExpirationMs() / 1000,
            new AuthDtos.UserProfile(user.getId(), user.getEmail(), user.getDisplayName(), user.getLangPref())
        );
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
