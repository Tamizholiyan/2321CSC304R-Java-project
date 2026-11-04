package com.campuscommute.campuscommute.auth;

import com.campuscommute.campuscommute.auth.dto.AuthResponse;
import com.campuscommute.campuscommute.auth.dto.LoginRequest;
import com.campuscommute.campuscommute.auth.dto.RefreshTokenRequest;
import com.campuscommute.campuscommute.auth.dto.RegisterRequest;
import com.campuscommute.campuscommute.auth.dto.UpdateRoleRequest;
import com.campuscommute.campuscommute.auth.dto.UpdateUpiRequest;
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

    @Autowired
    private JwtUtil jwtUtil;

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

    @Test
    @DisplayName("Phase 1: Token refresh after role change issues new JWT carrying the updated role")
    void testRefreshAfterRoleChangeReturnsNewRole() throws Exception {
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

        AuthResponse initialAuth = objectMapper.readValue(
                regResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        String initialAccess = initialAuth.accessToken();
        String initialRefresh = initialAuth.refreshToken();
        assertThat(jwtUtil.extractRole(initialAccess)).isEqualTo(Role.RIDER);

        // Switch role to DRIVER
        mockMvc.perform(patch("/api/users/me/role")
                        .header("Authorization", "Bearer " + initialAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(Role.DRIVER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("DRIVER")));

        // Client calls POST /api/auth/refresh with the existing refresh token
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(initialRefresh))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role", is("DRIVER")))
                .andReturn();

        AuthResponse refreshedAuth = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(),
                AuthResponse.class
        );

        // The newly minted access token inside the JWT must carry the DRIVER role
        Role newJwtRole = jwtUtil.extractRole(refreshedAuth.accessToken());
        assertThat(newJwtRole).isEqualTo(Role.DRIVER);
    }

    @Test
    @DisplayName("Phase 1: RegisterRequest validation enforces trimmed 12-digit register number and Indian mobile")
    void testRegisterRequestValidation() throws Exception {
        // Less than 12 digits
        RegisterRequest shortRegNo = new RegisterRequest("31062110400", "9876543210", "Password@123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortRegNo)))
                .andExpect(status().isBadRequest());

        // Invalid Indian mobile number (starts with 5)
        RegisterRequest invalidPhone = new RegisterRequest("310621104001", "5876543210", "Password@123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPhone)))
                .andExpect(status().isBadRequest());

        // Register number with whitespace should be trimmed and succeed
        RegisterRequest untrimmedRegNo = new RegisterRequest("  310621104001  ", "  9876543210  ", "Password@123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(untrimmedRegNo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.registerNumber", is("310621104001")))
                .andExpect(jsonPath("$.user.phoneNumber", is("9876543210")));
    }

    @Test
    @DisplayName("Phase 1: PATCH /api/users/me/upi updates UPI ID with name@handle format validation")
    void testUpdateUpiValidationAndSuccess() throws Exception {
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
        String token = authResponse.accessToken();

        // 1. Initial upiId is null
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upiId").doesNotExist());

        // 2. Invalid UPI format (no @ handle) returns 400
        mockMvc.perform(patch("/api/users/me/upi")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUpiRequest("invalidupi"))))
                .andExpect(status().isBadRequest());

        // 3. Valid UPI updates successfully
        mockMvc.perform(patch("/api/users/me/upi")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUpiRequest("kavitha@oksbi"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upiId", is("kavitha@oksbi")));

        // 4. Subsequent GET /me includes upiId
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upiId", is("kavitha@oksbi")));
    }

    @Test
    @DisplayName("Phase 13: All 28 CSE Year 2 students exist in student_directory with correct names, dept and year")
    void testAll28CseYear2StudentsExistInDirectory() {
        java.util.Map<String, String> expectedStudents = java.util.Map.ofEntries(
                java.util.Map.entry("310625104307", "Sahil Chaudhary"),
                java.util.Map.entry("310625104389", "Surya N"),
                java.util.Map.entry("310625104390", "Suryapriya"),
                java.util.Map.entry("310625104391", "Sushaanth"),
                java.util.Map.entry("310625104392", "Sushmitha"),
                java.util.Map.entry("310625104393", "Suvanika Shree"),
                java.util.Map.entry("310625104394", "Swetha"),
                java.util.Map.entry("310625104395", "Swetha Vasudevan"),
                java.util.Map.entry("310625104396", "Syed Hassan"),
                java.util.Map.entry("310625104397", "Tamizholiyan"),
                java.util.Map.entry("310625104398", "Tanushree"),
                java.util.Map.entry("310625104399", "Tanushri Muralidharan"),
                java.util.Map.entry("310625104400", "Tejna"),
                java.util.Map.entry("310625104401", "Thabitha Sharen"),
                java.util.Map.entry("310625104402", "Thampu Raj"),
                java.util.Map.entry("310625104403", "Thanush"),
                java.util.Map.entry("310625104404", "Thanushree"),
                java.util.Map.entry("310625104405", "Tharukeswaran"),
                java.util.Map.entry("310625104406", "Tharun"),
                java.util.Map.entry("310625104407", "Tharun Karthick"),
                java.util.Map.entry("310625104409", "Thavanesh Ramesh"),
                java.util.Map.entry("310625104410", "Theertha"),
                java.util.Map.entry("310625104411", "Thejas"),
                java.util.Map.entry("310625104412", "Thillai Vignesh"),
                java.util.Map.entry("310625104413", "Thirukumaran"),
                java.util.Map.entry("310625104414", "Thiyagesan"),
                java.util.Map.entry("310625104415", "Thrijagathi"),
                java.util.Map.entry("310625104416", "Thrinayani")
        );

        assertThat(expectedStudents).hasSize(28);

        expectedStudents.forEach((regNo, expectedName) -> {
            var studentOpt = studentDirectoryRepository.findById(regNo);
            assertThat(studentOpt).as("Student with regNo " + regNo + " must exist in directory").isPresent();
            var student = studentOpt.get();
            assertThat(student.getFullName()).as("Full name for " + regNo).isEqualTo(expectedName);
            assertThat(student.getDepartment()).as("Department for " + regNo).isEqualTo("Computer Science and Engineering");
            assertThat(student.getYearOfStudy()).as("Year of study for " + regNo).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("Phase 13: Registration succeeds for listed CSE student and validates derived college email")
    void testRegistrationWithDerivedCollegeEmailValidation() throws Exception {
        // 1. Mismatched email returns 400 Bad Request
        RegisterRequest mismatchedEmailRequest = new RegisterRequest(
                "310625104307",
                "9876543220",
                "Password@123",
                "wrong.email@gmail.com"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mismatchedEmailRequest)))
                .andExpect(status().isBadRequest());

        // 2. Matching derived email succeeds
        RegisterRequest validRequest = new RegisterRequest(
                "310625104307",
                "9876543220",
                "Password@123",
                "310625104307@eec.srmrmp.edu.in"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.fullName", is("Sahil Chaudhary")));

        // Verify user was stored in database
        assertThat(userRepository.findByRegisterNumber("310625104307")).isPresent();
    }
}
