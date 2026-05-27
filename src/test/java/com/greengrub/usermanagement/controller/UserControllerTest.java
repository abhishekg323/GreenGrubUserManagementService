package com.greengrub.usermanagement.controller;

import com.greengrub.usermanagement.dto.*;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserControllerTest {

    @Mock private UserService userService;
    @InjectMocks private UserController controller;

    private UserResponse sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = UserResponse.builder()
                .id("user-001").name("John Doe").email("john@example.com")
                .role(UserRole.DONOR).isActive(true)
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();
    }

    @Test
    void createUser_returns201() {
        when(userService.createUser(any(UserCreateRequest.class))).thenReturn(sampleUser);
        UserCreateRequest req = UserCreateRequest.builder()
                .name("John Doe").email("john@example.com").password("pass1234").role(UserRole.DONOR).build();

        ResponseEntity<ApiResponse> response = controller.createUser(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    @Test
    void getUserById_returns200() {
        when(userService.getUserById("user-001")).thenReturn(sampleUser);
        ResponseEntity<UserResponse> response = controller.getUserById("user-001");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo("user-001");
    }

    @Test
    void getUserByEmail_returns200() {
        when(userService.getUserByEmail("john@example.com")).thenReturn(sampleUser);
        ResponseEntity<UserResponse> response = controller.getUserByEmail("john@example.com");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getAllUsers_noParams_callsGetAll() {
        when(userService.getAllUsers()).thenReturn(List.of(sampleUser));
        ResponseEntity<List<UserResponse>> response = controller.getAllUsers(null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).getAllUsers();
    }

    @Test
    void getAllUsers_activeTrue_callsGetActiveUsers() {
        when(userService.getActiveUsers()).thenReturn(List.of(sampleUser));
        controller.getAllUsers(true, null);
        verify(userService).getActiveUsers();
    }

    @Test
    void getAllUsers_roleParam_callsGetUsersByRole() {
        when(userService.getUsersByRole(UserRole.DONOR)).thenReturn(List.of(sampleUser));
        controller.getAllUsers(null, UserRole.DONOR);
        verify(userService).getUsersByRole(UserRole.DONOR);
    }

    @Test
    void searchUsers_returns200() {
        when(userService.searchUsersByName("John")).thenReturn(List.of(sampleUser));
        ResponseEntity<List<UserResponse>> response = controller.searchUsers("John");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getActiveDonors_returns200() {
        when(userService.getActiveDonors()).thenReturn(List.of(sampleUser));
        ResponseEntity<List<UserResponse>> response = controller.getActiveDonors();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getActiveRecipients_returns200() {
        when(userService.getActiveRecipients()).thenReturn(List.of());
        ResponseEntity<List<UserResponse>> response = controller.getActiveRecipients();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateUser_returns200() {
        when(userService.updateUser(eq("user-001"), any())).thenReturn(sampleUser);
        UserUpdateRequest req = UserUpdateRequest.builder().name("New Name").build();
        ResponseEntity<ApiResponse> response = controller.updateUser("user-001", req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void activateUser_returns200() {
        when(userService.activateUser("user-001")).thenReturn(sampleUser);
        ResponseEntity<ApiResponse> response = controller.activateUser("user-001");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deactivateUser_returns200() {
        when(userService.deactivateUser("user-001")).thenReturn(sampleUser);
        ResponseEntity<ApiResponse> response = controller.deactivateUser("user-001");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteUser_returns200() {
        doNothing().when(userService).deleteUser("user-001");
        ResponseEntity<ApiResponse> response = controller.deleteUser("user-001");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).deleteUser("user-001");
    }

    @Test
    void permanentlyDeleteUser_returns200() {
        doNothing().when(userService).permanentlyDeleteUser("user-001");
        ResponseEntity<ApiResponse> response = controller.permanentlyDeleteUser("user-001");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void checkEmailExists_emailExists_returnsTrue() {
        when(userService.existsByEmail("john@example.com")).thenReturn(true);
        ResponseEntity<ApiResponse> response = controller.checkEmailExists("john@example.com");
        assertThat(response.getBody().getData()).isEqualTo(true);
    }

    @Test
    void uploadProfileImage_empty_throwsIllegalArgument() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);
        assertThatThrownBy(() -> controller.uploadProfileImage("user-001", file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uploadProfileImage_tooLarge_throwsIllegalArgument() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(6L * 1024 * 1024);
        assertThatThrownBy(() -> controller.uploadProfileImage("user-001", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void uploadProfileImage_invalidContentType_throwsIllegalArgument() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getContentType()).thenReturn("application/pdf");
        assertThatThrownBy(() -> controller.uploadProfileImage("user-001", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported content type");
    }

    @Test
    void uploadProfileImage_valid_returns200() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(userService.uploadProfileImage(eq("user-001"), any(MultipartFile.class))).thenReturn(sampleUser);

        ResponseEntity<ApiResponse> response = controller.uploadProfileImage("user-001", file);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteProfileImage_returns200() {
        when(userService.deleteProfileImage("user-001")).thenReturn(sampleUser);
        ResponseEntity<ApiResponse> response = controller.deleteProfileImage("user-001");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getDonationsByUserId_negativePage_throwsIllegalArgument() {
        assertThatThrownBy(() -> controller.getDonationsByUserId("user-001", -1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    void getDonationsByUserId_pageSizeTooLarge_throwsIllegalArgument() {
        assertThatThrownBy(() -> controller.getDonationsByUserId("user-001", 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize");
    }

    @Test
    void getDonationsByUserId_valid_returns200() {
        DonationListView donations = DonationListView.builder().build();
        when(userService.getDonationsByUserId("user-001", 0, 10)).thenReturn(donations);
        ResponseEntity<DonationListView> response = controller.getDonationsByUserId("user-001", 0, 10);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getUserStats_returns200() {
        when(userService.getAllUsers()).thenReturn(List.of(sampleUser));
        when(userService.countActiveUsers()).thenReturn(1L);
        when(userService.countUsersByRole(UserRole.DONOR)).thenReturn(1L);
        when(userService.countUsersByRole(UserRole.RECIPIENT)).thenReturn(0L);

        ResponseEntity<ApiResponse> response = controller.getUserStats();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updatePassword_returns200() {
        doNothing().when(userService).updatePassword(eq("user-001"), any(), any());
        PasswordUpdateRequest req = PasswordUpdateRequest.builder()
                .oldPassword("old").newPassword("newpass123").build();
        ResponseEntity<ApiResponse> response = controller.updatePassword("user-001", req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void resetPassword_returns200() {
        doNothing().when(userService).resetPassword(eq("user-001"), any());
        PasswordResetRequest req = PasswordResetRequest.builder().newPassword("newpass123").build();
        ResponseEntity<ApiResponse> response = controller.resetPassword("user-001", req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
