package com.campuscommute.campuscommute.auth;

import com.campuscommute.campuscommute.auth.dto.AuthResponse;
import com.campuscommute.campuscommute.auth.dto.LoginRequest;
import com.campuscommute.campuscommute.auth.dto.RefreshTokenRequest;
import com.campuscommute.campuscommute.auth.dto.RegisterRequest;
import com.campuscommute.campuscommute.auth.dto.UpdateRoleRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentDirectoryRepository studentDirectoryRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Ensure student directory has valid test students
        if (!studentDirectoryRepository.existsById("310621104001")) {
            studentDirectoryRepository.save(new StudentDirectory(
                    "310621104001", "Kavitha R", "Computer Science and Engineering", 3
            ));
        }
        if (!studentDirectoryRepository.existsById("310621104002")) {
            studentDirectoryRepository.save(new StudentDirectory(
                    "310621104002", "Arun Kumar S", "Information Technology", 4
            ));
        }
    }

    @Test
    @DisplayName("AC1: Registering with a register number not in student_directory returns 401 and does not create user")
    void testRegisterUnverifiedStudentReturns401() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "999999999999", // Invalid EEC register number
                "9876543210",
                "securePass123"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("Easwari Engineering College student directory")));

        assertThat(userRepository.findByRegisterNumber("999999999999")).isEmpty();
    }

    @Test
    @DisplayName("AC2: Registering valid student succeeds; registering same register number twice returns 409")
    void testDuplicateRegistrationReturns409() throws Exception {
        RegisterRequest first = new RegisterRequest(
                "310621104001",
                "9876543210",
                "securePass123"
        );

        // First registration succeeds
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.user.role", is("RIDER")))
                .andExpect(jsonPath("$.user.fullName", is("Kavitha R")));

        assertThat(userRepository.findByRegisterNumber("310621104001")).isPresent();

        // Second registration with the same register number returns 409 Conflict
        RegisterRequest duplicate = new RegisterRequest(
                "310621104001",
                "9876543211", // different phone
                "anotherPassword"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("AC3: Register & login returns tokens; GET /me with token returns user; no token/garbage token returns 401")
    void testTokensAndUserProfileEndpoint() throws Exception {
        // Register user
        RegisterRequest regRequest = new RegisterRequest(
                "310621104002",
                "9123456780",
                "password123"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // Login with phone + password
        LoginRequest loginRequest = new LoginRequest("9123456780", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        String accessToken = authResponse.accessToken();

        // 1. GET /api/users/me with valid access token returns 200 with user profile
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registerNumber", is("310621104002")))
                .andExpect(jsonPath("$.fullName", is("Arun Kumar S")))
                .andExpect(jsonPath("$.department", is("Information Technology")))
                .andExpect(jsonPath("$.role", is("RIDER")));

        // 2. GET /api/users/me with NO token returns 401
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        // 3. GET /api/users/me with GARBAGE token returns 401
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer garbage.invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("AC4: /api/auth/refresh with valid refresh token returns new access token; passing access token is rejected")
    void testRefreshTokenValidationAndAccessRejection() throws Exception {
        RegisterRequest regRequest = new RegisterRequest(
                "310621104001",
                "9876543210",
                "securePass123"
        );

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                regResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        String accessToken = authResponse.accessToken();
        String refreshToken = authResponse.refreshToken();

        // 1. Passing an access token to /refresh MUST BE REJECTED with 401
        RefreshTokenRequest invalidRefresh = new RefreshTokenRequest(accessToken);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("Access tokens cannot be used to refresh")));

        // 2. Passing a valid refresh token returns a new pair
        RefreshTokenRequest validRefresh = new RefreshTokenRequest(refreshToken);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()));
    }

    @Test
    @DisplayName("AC5: PATCH /api/users/me/role changes role and reflects in subsequent /me call")
    void testPatchUserRole() throws Exception {
        RegisterRequest regRequest = new RegisterRequest(
                "310621104001",
                "9876543210",
                "securePass123"
        );

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                regResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        String accessToken = authResponse.accessToken();

        // Initial role is RIDER
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("RIDER")));

        // Change role to DRIVER
        UpdateRoleRequest updateRequest = new UpdateRoleRequest(Role.DRIVER);
        mockMvc.perform(patch("/api/users/me/role")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("DRIVER")));

        // Verify next /me call reflects DRIVER
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("DRIVER")));

        // Change role back to RIDER
        UpdateRoleRequest updateRider = new UpdateRoleRequest(Role.RIDER);
        mockMvc.perform(patch("/api/users/me/role")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("RIDER")));
    }
}
