package com.campuscommute.campuscommute.common;

import com.campuscommute.campuscommute.auth.JwtUtil;
import com.campuscommute.campuscommute.auth.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    private String validToken;

    @BeforeEach
    void setUp() {
        validToken = jwtUtil.generateAccessToken(1L, Role.RIDER);
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException returns 405 Method Not Allowed")
    void methodNotSupported_Returns405() throws Exception {
        // GET /api/auth/login is not supported (only POST)
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status", is(405)))
                .andExpect(jsonPath("$.error", is("Method Not Allowed")))
                .andExpect(jsonPath("$.message", containsString("not supported")));
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException returns 400 Bad Request")
    void methodArgumentTypeMismatch_Returns400() throws Exception {
        // /api/rides/abc should fail with type mismatch for Long rideId
        mockMvc.perform(get("/api/rides/not-a-number")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @Test
    @DisplayName("MissingServletRequestParameterException returns 400 Bad Request")
    void missingServletRequestParameter_Returns400() throws Exception {
        // POST /api/bookings requires rideId param
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("rideId")));
    }

    @Test
    @DisplayName("Generic Exception does not leak exception details to client")
    void genericException_DoesNotLeakInternalDetails() throws Exception {
        // Send malformed or invalid request that triggers exception handling
        mockMvc.perform(get("/api/rides/999999999999999999999999999999999999999999999")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", not(containsString("NullPointerException"))));
    }
}
