package com.campuscommute.campuscommute.auth;

import com.campuscommute.campuscommute.auth.dto.AuthResponse;
import com.campuscommute.campuscommute.auth.dto.LoginRequest;
import com.campuscommute.campuscommute.auth.dto.RefreshTokenRequest;
import com.campuscommute.campuscommute.auth.dto.RegisterRequest;
import com.campuscommute.campuscommute.auth.dto.UserProfileResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final StudentDirectoryRepository studentDirectoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(
            StudentDirectoryRepository studentDirectoryRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil
    ) {
        this.studentDirectoryRepository = studentDirectoryRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 1. Walled-garden identity verification against static college directory
        StudentDirectory student = studentDirectoryRepository.findById(request.registerNumber())
                .orElseThrow(() -> new UnauthorizedInstitutionalAccessException(request.registerNumber()));

        // 2. Prevent duplicate registrations for already-onboarded students
        if (userRepository.existsByRegisterNumber(request.registerNumber())) {
            throw DuplicateRegistrationException.forRegisterNumber(request.registerNumber());
        }

        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw DuplicateRegistrationException.forPhoneNumber(request.phoneNumber());
        }

        // 3. Hash password with BCrypt
        String passwordHash = passwordEncoder.encode(request.password());

        // 4. Create active user account with default role RIDER
        User user = new User(
                request.registerNumber(),
                request.phoneNumber(),
                passwordHash,
                Role.RIDER
        );
        user = userRepository.save(user);

        // 5. Issue access and refresh tokens
        String accessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getRole());

        UserProfileResponse userProfile = UserProfileResponse.from(user, student);
        return AuthResponse.of(accessToken, refreshToken, jwtUtil.getAccessExpirationMs(), userProfile);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByPhoneNumber(request.phoneNumber())
                .orElseThrow(() -> new BadCredentialsException("Invalid phone number or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid phone number or password");
        }

        StudentDirectory student = studentDirectoryRepository.findById(user.getRegisterNumber()).orElse(null);

        String accessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getRole());

        UserProfileResponse userProfile = UserProfileResponse.from(user, student);
        return AuthResponse.of(accessToken, refreshToken, jwtUtil.getAccessExpirationMs(), userProfile);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        if (!jwtUtil.isRefreshToken(request.refreshToken())) {
            throw new InvalidTokenException("Invalid or expired refresh token. Access tokens cannot be used to refresh.");
        }

        Long userId = jwtUtil.extractUserId(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User associated with refresh token no longer exists."));

        StudentDirectory student = studentDirectoryRepository.findById(user.getRegisterNumber()).orElse(null);

        String newAccessToken = jwtUtil.generateAccessToken(user.getUserId(), user.getRole());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getRole());

        UserProfileResponse userProfile = UserProfileResponse.from(user, student);
        return AuthResponse.of(newAccessToken, newRefreshToken, jwtUtil.getAccessExpirationMs(), userProfile);
    }
}
