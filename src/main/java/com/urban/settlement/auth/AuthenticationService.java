package com.urban.settlement.auth;

import com.urban.settlement.config.JwtService;
import com.urban.settlement.exception.ResourceNotFoundException;
import com.urban.settlement.token.Token;
import com.urban.settlement.token.TokenRepository;
import com.urban.settlement.token.TokenState;
import com.urban.settlement.token.TokenType;
import com.urban.settlement.model.enums.Role;
import com.urban.settlement.model.User;
import com.urban.settlement.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
        private final UserRepository repository;
        private final TokenRepository tokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;

        public AuthenticationResponse register(RegisterRequest request) {
                if (repository.existsByEmail(request.getEmail())) {
                        return AuthenticationResponse.builder().message("User already exists").build();
                }

                var user = User.builder()
                                .firstname(request.getFirstname())
                                .lastname(request.getLastname())
                                .email(request.getEmail())
                                .physicalAddress(request.getPhysicalAddress())
                                .gender(request.getGender())
                                .dateOfBirth(request.getDateOfBirth())
                                .password(passwordEncoder.encode(request.getPassword()))
                                .role(request.getRole() != null ? request.getRole() : Role.USER)
                                .enabled(true)
                                .build();

                var savedUser = repository.save(user);
                var jwtToken = jwtService.generateToken(user);
                var refreshToken = jwtService.generateRefreshToken(user);
                saveUserToken(savedUser, jwtToken);

                return AuthenticationResponse.builder()
                                .role(user.getRole())
                                .email(user.getEmail())
                                .accessToken(jwtToken)
                                .refreshToken(refreshToken)
                                .officerId(user.getOfficerId())
                                .message("Account created successfully")
                                .build();
        }

        public AuthenticationResponse authenticate(AuthenticationRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getEmail(),
                                                request.getPassword()));
                var user = repository.findByEmail(request.getEmail())
                                .orElseThrow();
                var jwtToken = jwtService.generateToken(user);
                var refreshToken = jwtService.generateRefreshToken(user);
                revokeAllUserTokens(user);
                saveUserToken(user, jwtToken);

                return AuthenticationResponse.builder()
                                .role(user.getRole())
                                .email(user.getEmail())
                                .accessToken(jwtToken)
                                .refreshToken(refreshToken)
                                .officerId(user.getOfficerId())
                                .message("Login successful.")
                                .build();
        }

        private void saveUserToken(User user, String jwtToken) {
                var token = Token.builder()
                                .user(user)
                                .token(jwtToken)
                                .tokenType(TokenType.BEARER)
                                .expired(false)
                                .revoked(false)
                                .tokenState(TokenState.fresh)
                                .build();
                tokenRepository.save(token);
        }

        private void revokeAllUserTokens(User user) {
                var validUserTokens = tokenRepository.findAllValidTokenByUser(user.getId());
                if (validUserTokens.isEmpty())
                        return;
                validUserTokens.forEach(token -> {
                        token.setExpired(true);
                        token.setRevoked(true);
                });
                tokenRepository.saveAll(validUserTokens);
        }

        public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
                final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                final String refreshToken;
                final String userEmail;
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        return;
                }
                refreshToken = authHeader.substring(7);
                userEmail = jwtService.extractUsername(refreshToken);
                if (userEmail != null) {
                        var user = this.repository.findByEmail(userEmail)
                                        .orElseThrow();
                        if (jwtService.isTokenValid(refreshToken, user)) {
                                var accessToken = jwtService.generateToken(user);
                                revokeAllUserTokens(user);
                                saveUserToken(user, accessToken);
                                var authResponse = AuthenticationResponse.builder()
                                                .accessToken(accessToken)
                                                .refreshToken(refreshToken)
                                                .build();
                                new ObjectMapper().writeValue(response.getOutputStream(), authResponse);
                        }
                }
        }

        public List<User> getAllUsers() {
                return repository.findAll();
        }

        public ResponseEntity<AuthenticationResponse> updateUserRole(String email, Role role) {
                User user = repository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                user.setRole(role);
                repository.save(user);
                return ResponseEntity.ok(AuthenticationResponse
                                .builder()
                                .message("User role updated successfully")
                                .build());
        }

        public User getUserByEmail(String email) {
                return repository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        }

        public List<User> getAllUsersByRole(Role role) {
                return repository.findAllByRole(role); // Requires custom repository method
        }
}
