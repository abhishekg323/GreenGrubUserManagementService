package com.greengrub.usermanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greengrub.usermanagement.dto.*;
import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Complete API Integration Tests
 * Tests all endpoints with authentication scenarios
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompleteApiIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static String donorToken;
    private static String donorUserId;
    private static String recipientToken;
    private static String recipientUserId;
    private static String adminToken;
    private static String adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @AfterAll
    static void cleanUp(@Autowired UserRepository userRepository) {
        userRepository.deleteAll();
    }

    // ==================== AUTH TESTS ====================

    @Test
    @Order(1)
    void test01_SignupDonor_Success() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .name("Donor User")
                .email("donor@test.com")
                .password("password123")
                .phoneNumber("1111111111")
                .address("Donor Address")
                .role(UserRole.DONOR)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.email").value("donor@test.com"))
                .andExpect(jsonPath("$.data.role").value("DONOR"))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        ApiResponse apiResponse = objectMapper.readValue(response, ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) apiResponse.getData();
        donorToken = (String) data.get("token");
        donorUserId = (String) data.get("userId");
    }

    @Test
    @Order(2)
    void test02_SignupRecipient_Success() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .name("Recipient User")
                .email("recipient@test.com")
                .password("password123")
                .phoneNumber("2222222222")
                .address("Recipient Address")
                .role(UserRole.RECIPIENT)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.role").value("RECIPIENT"))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        ApiResponse apiResponse = objectMapper.readValue(response, ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) apiResponse.getData();
        recipientToken = (String) data.get("token");
        recipientUserId = (String) data.get("userId");
    }

    @Test
    @Order(3)
    void test03_CreateAdminUser_AndLogin() throws Exception {
        // Create admin user directly via repository
        User admin = new User();
        admin.setName("Admin User");
        admin.setEmail("admin@test.com");
        admin.setPassword(passwordEncoder.encode("password123"));
        admin.setPhoneNumber("3333333333");
        admin.setRole(UserRole.ADMIN);
        admin.setIsActive(true);
        User savedAdmin = userRepository.save(admin);
        adminUserId = savedAdmin.getId();

        // Login as admin
        LoginRequest loginRequest = LoginRequest.builder()
                .email("admin@test.com")
                .password("password123")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        ApiResponse apiResponse = objectMapper.readValue(response, ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) apiResponse.getData();
        adminToken = (String) data.get("token");
    }

    @Test
    @Order(4)
    void test04_SignupWithExistingEmail_ShouldFail() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .name("Another User")
                .email("donor@test.com")
                .password("password123")
                .phoneNumber("4444444444")
                .role(UserRole.DONOR)
                .build();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("User Already Exists"));
    }

    @Test
    @Order(5)
    void test05_SignupWithInvalidData_ShouldFail() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .name("")
                .email("invalid-email")
                .password("short")
                .build();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors").isNotEmpty());
    }

    @Test
    @Order(6)
    void test06_Login_Success() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("donor@test.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.email").value("donor@test.com"));
    }

    @Test
    @Order(7)
    void test07_LoginWithWrongPassword_ShouldFail() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("donor@test.com")
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Password"));
    }

    @Test
    @Order(8)
    void test08_LoginWithNonExistentUser_ShouldFail() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("nonexistent@test.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User Not Found"));
    }

    @Test
    @Order(9)
    void test09_ValidateToken_Success() throws Exception {
        mockMvc.perform(get("/api/v1/auth/validate")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Token is valid"))
                .andExpect(jsonPath("$.data.email").value("donor@test.com"));
    }

    @Test
    @Order(10)
    void test10_ValidateTokenWithoutHeader_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/auth/validate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(11)
    void test11_GetCurrentUser_Success() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("donor@test.com"));
    }

    // ==================== USER MANAGEMENT TESTS ====================

    @Test
    @Order(12)
    void test12_GetUserById_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + donorUserId)
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(donorUserId))
                .andExpect(jsonPath("$.email").value("donor@test.com"))
                .andExpect(jsonPath("$.name").value("Donor User"));
    }

    @Test
    @Order(13)
    void test13_GetUserByIdWithoutAuth_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + donorUserId))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    void test14_GetUserByIdNonExistent_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/users/non-existent-id")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User Not Found"));
    }

    @Test
    @Order(15)
    void test15_GetAllUsers_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @Order(16)
    void test16_GetActiveUsers_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users?active=true")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    @Order(17)
    void test17_GetUsersByRole_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users?role=DONOR")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].role").value("DONOR"));
    }

    @Test
    @Order(18)
    void test18_SearchUsersByName_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/search?name=Donor")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(19)
    void test19_UpdateUser_Success() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
                .name("Donor User Updated")
                .phoneNumber("9999999999")
                .build();

        mockMvc.perform(put("/api/v1/users/" + donorUserId)
                        .header("Authorization", "Bearer " + donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User updated successfully"));

        // Verify update
        mockMvc.perform(get("/api/v1/users/" + donorUserId)
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Donor User Updated"))
                .andExpect(jsonPath("$.phoneNumber").value("9999999999"));
    }

    @Test
    @Order(20)
    void test20_UpdateUserWithoutAuth_ShouldFail() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
                .name("Should Fail")
                .build();

        mockMvc.perform(put("/api/v1/users/" + donorUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(21)
    void test21_UpdatePassword_Success() throws Exception {
        PasswordUpdateRequest request = PasswordUpdateRequest.builder()
                .oldPassword("password123")
                .newPassword("newPassword456")
                .build();

        mockMvc.perform(put("/api/v1/users/" + recipientUserId + "/password")
                        .header("Authorization", "Bearer " + recipientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password updated successfully"));

        // Verify new password works
        LoginRequest loginRequest = LoginRequest.builder()
                .email("recipient@test.com")
                .password("newPassword456")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(22)
    void test22_UpdatePasswordWithWrongOldPassword_ShouldFail() throws Exception {
        PasswordUpdateRequest request = PasswordUpdateRequest.builder()
                .oldPassword("wrongpassword")
                .newPassword("newPassword789")
                .build();

        mockMvc.perform(put("/api/v1/users/" + donorUserId + "/password")
                        .header("Authorization", "Bearer " + donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Password"));
    }

    @Test
    @Order(23)
    void test23_DeactivateUser_Success() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + recipientUserId + "/deactivate")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deactivated successfully"));

        // Verify user is deactivated
        mockMvc.perform(get("/api/v1/users/" + recipientUserId)
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @Order(24)
    void test24_LoginWithDeactivatedUser_ShouldFail() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("recipient@test.com")
                .password("newPassword456")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Password"));
    }

    @Test
    @Order(25)
    void test25_ActivateUser_Success() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + recipientUserId + "/activate")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User activated successfully"));

        // Verify user is activated
        mockMvc.perform(get("/api/v1/users/" + recipientUserId)
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @Order(26)
    void test26_CheckEmailExists_True() throws Exception {
        mockMvc.perform(get("/api/v1/users/check-email?email=donor@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    @Order(27)
    void test27_CheckEmailExists_False() throws Exception {
        mockMvc.perform(get("/api/v1/users/check-email?email=newuser@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(false))
                .andExpect(jsonPath("$.message").value("Email is available"));
    }

    @Test
    @Order(28)
    void test28_GetUserStatsAsNonAdmin_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/users/stats")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(29)
    void test29_GetUserStatsAsAdmin_Success() throws Exception {
        mockMvc.perform(get("/api/v1/users/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.activeUsers").exists())
                .andExpect(jsonPath("$.data.donors").exists())
                .andExpect(jsonPath("$.data.recipients").exists());
    }

    @Test
    @Order(30)
    void test30_CreateUserAsNonAdmin_ShouldFail() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .name("Test User")
                .email("testuser@test.com")
                .password("password123")
                .phoneNumber("5555555555")
                .role(UserRole.DONOR)
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(31)
    void test31_CreateUserAsAdmin_Success() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .name("Admin Created User")
                .email("admincreated@test.com")
                .password("password123")
                .phoneNumber("6666666666")
                .role(UserRole.DONOR)
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("admincreated@test.com"));
    }

    @Test
    @Order(32)
    void test32_DeleteUser_Success() throws Exception {
        // Create a user to delete
        UserCreateRequest createRequest = UserCreateRequest.builder()
                .name("User To Delete")
                .email("todelete@test.com")
                .password("password123")
                .phoneNumber("7777777777")
                .role(UserRole.DONOR)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        ApiResponse apiResponse = objectMapper.readValue(response, ApiResponse.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) apiResponse.getData();
        String userToDeleteId = (String) data.get("userId");
        String userToDeleteToken = (String) data.get("token");

        // Delete the user (soft delete)
        mockMvc.perform(delete("/api/v1/users/" + userToDeleteId)
                        .header("Authorization", "Bearer " + userToDeleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deleted successfully"));

        // Verify user is soft-deleted (inactive)
        mockMvc.perform(get("/api/v1/users/" + userToDeleteId)
                        .header("Authorization", "Bearer " + userToDeleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @Order(33)
    void test33_DeleteUserWithoutAuth_ShouldFail() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + donorUserId))
                .andExpect(status().isForbidden());
    }

    // ==================== EDGE CASES & ERROR HANDLING ====================

    @Test
    @Order(34)
    void test34_UpdateNonExistentUser_ShouldFail() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
                .name("Should Fail")
                .build();

        mockMvc.perform(put("/api/v1/users/non-existent-id")
                        .header("Authorization", "Bearer " + donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User Not Found"));
    }

    @Test
    @Order(35)
    void test35_UpdatePasswordForNonExistentUser_ShouldFail() throws Exception {
        PasswordUpdateRequest request = PasswordUpdateRequest.builder()
                .oldPassword("password123")
                .newPassword("newPassword456")
                .build();

        mockMvc.perform(put("/api/v1/users/non-existent-id/password")
                        .header("Authorization", "Bearer " + donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User Not Found"));
    }

    @Test
    @Order(36)
    void test36_SearchWithEmptyQuery_ShouldReturnEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/users/search?name=NonExistentUserName")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Order(37)
    void test37_UpdateUserWithInvalidEmail_ShouldFail() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
                .email("invalid-email")
                .build();

        mockMvc.perform(put("/api/v1/users/" + donorUserId)
                        .header("Authorization", "Bearer " + donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @Order(38)
    void test38_UpdatePasswordWithShortPassword_ShouldFail() throws Exception {
        PasswordUpdateRequest request = PasswordUpdateRequest.builder()
                .oldPassword("password123")
                .newPassword("short")
                .build();

        mockMvc.perform(put("/api/v1/users/" + donorUserId + "/password")
                        .header("Authorization", "Bearer " + donorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @Order(39)
    void test39_AccessProtectedEndpointWithInvalidToken_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + donorUserId)
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(40)
    void test40_AccessProtectedEndpointWithoutBearerPrefix_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + donorUserId)
                        .header("Authorization", donorToken))
                .andExpect(status().isForbidden());
    }
}
