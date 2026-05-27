package com.greengrub.usermanagement.service;

import com.greengrub.proto.donation.DonationListResponse;
import com.greengrub.usermanagement.client.DonationServiceClient;
import com.greengrub.usermanagement.client.ImageServiceClient;
import com.greengrub.usermanagement.dto.*;
import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.exception.InvalidPasswordException;
import com.greengrub.usermanagement.exception.UserAlreadyExistsException;
import com.greengrub.usermanagement.exception.UserNotFoundException;
import com.greengrub.usermanagement.mapper.UserMapper;
import com.greengrub.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ImageServiceClient imageServiceClient;
    @Mock private DonationServiceClient donationServiceClient;

    @InjectMocks private UserServiceImpl userService;

    private User testUser;
    private UserCreateRequest createRequest;
    private UserUpdateRequest updateRequest;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("test-id-123").name("John Doe").email("john@test.com")
                .password("hashedPassword").phoneNumber("1234567890").address("123 Main St")
                .role(UserRole.DONOR).isActive(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        createRequest = UserCreateRequest.builder()
                .name("John Doe").email("john@test.com").password("password123")
                .phoneNumber("1234567890").address("123 Main St").role(UserRole.DONOR)
                .build();

        updateRequest = UserUpdateRequest.builder()
                .name("John Updated").email("john.updated@test.com")
                .build();

        userResponse = UserResponse.builder()
                .id("test-id-123").name("John Doe").email("john@test.com")
                .phoneNumber("1234567890").address("123 Main St").role(UserRole.DONOR)
                .isActive(true).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    // ── createUser ────────────────────────────────────────────────────────────

    @Test
    void shouldCreateUserSuccessfully() {
        User userWithPlainPassword = User.builder()
                .name("John Doe").email("john@test.com").password("password123")
                .phoneNumber("1234567890").address("123 Main St").role(UserRole.DONOR).isActive(true)
                .build();

        when(userRepository.existsByEmail(createRequest.getEmail())).thenReturn(false);
        when(userMapper.toEntity(createRequest)).thenReturn(userWithPlainPassword);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        UserResponse result = userService.createUser(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john@test.com");
        verify(userRepository).existsByEmail(createRequest.getEmail());
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenCreatingUserWithExistingEmail() {
        when(userRepository.existsByEmail(createRequest.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(createRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("john@test.com");

        verify(userRepository, never()).save(any(User.class));
    }

    // ── getUserById / getUserByEmail ──────────────────────────────────────────

    @Test
    void shouldGetUserByIdSuccessfully() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        UserResponse result = userService.getUserById("test-id-123");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("test-id-123");
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundById() {
        when(userRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById("non-existent-id"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("non-existent-id");
    }

    @Test
    void shouldGetUserByEmailSuccessfully() {
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        UserResponse result = userService.getUserByEmail("john@test.com");

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john@test.com");
    }

    @Test
    void getUserByEmail_notFound_throwsException() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByEmail("missing@test.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ── getAll / getActive / getByRole ────────────────────────────────────────

    @Test
    void shouldGetAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(testUser));
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldGetActiveUsers() {
        when(userRepository.findByIsActiveTrue()).thenReturn(List.of(testUser));
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        List<UserResponse> result = userService.getActiveUsers();

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldGetUsersByRole() {
        when(userRepository.findByRole(UserRole.DONOR)).thenReturn(List.of(testUser));
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        List<UserResponse> result = userService.getUsersByRole(UserRole.DONOR);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo(UserRole.DONOR);
    }

    // ── updateUser ────────────────────────────────────────────────────────────

    @Test
    void shouldUpdateUserSuccessfully() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("john.updated@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        UserResponse result = userService.updateUser("test-id-123", updateRequest);

        assertThat(result).isNotNull();
        verify(userMapper).updateEntity(testUser, updateRequest);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingWithExistingEmail() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("john.updated@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser("test-id-123", updateRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("john.updated@test.com");
    }

    @Test
    void updateUser_sameEmail_doesNotCheckDuplicate() {
        UserUpdateRequest sameEmailReq = UserUpdateRequest.builder().email("john@test.com").build();
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        userService.updateUser("test-id-123", sameEmailReq);

        verify(userRepository, never()).existsByEmail(any());
    }

    // ── delete / permanentlyDelete ────────────────────────────────────────────

    @Test
    void shouldDeleteUserSuccessfully() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        userService.deleteUser("test-id-123");

        assertThat(testUser.getIsActive()).isFalse();
    }

    @Test
    void shouldPermanentlyDeleteUser() {
        when(userRepository.existsById("test-id-123")).thenReturn(true);

        userService.permanentlyDeleteUser("test-id-123");

        verify(userRepository).deleteById("test-id-123");
    }

    @Test
    void permanentlyDeleteUser_notFound_throwsException() {
        when(userRepository.existsById("missing-id")).thenReturn(false);

        assertThatThrownBy(() -> userService.permanentlyDeleteUser("missing-id"))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ── activate / deactivate ─────────────────────────────────────────────────

    @Test
    void shouldActivateUser() {
        testUser.setIsActive(false);
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        UserResponse result = userService.activateUser("test-id-123");

        assertThat(result).isNotNull();
        assertThat(testUser.getIsActive()).isTrue();
    }

    @Test
    void shouldDeactivateUser() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        userService.deactivateUser("test-id-123");

        assertThat(testUser.getIsActive()).isFalse();
    }

    // ── existsByEmail / search / donors / recipients ──────────────────────────

    @Test
    void shouldCheckIfEmailExists() {
        when(userRepository.existsByEmail("john@test.com")).thenReturn(true);
        assertThat(userService.existsByEmail("john@test.com")).isTrue();
    }

    @Test
    void shouldSearchUsersByName() {
        when(userRepository.findByNameContainingIgnoreCase("john")).thenReturn(List.of(testUser));
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        assertThat(userService.searchUsersByName("john")).hasSize(1);
    }

    @Test
    void shouldGetActiveDonors() {
        when(userRepository.findAllActiveDonors(UserRole.DONOR)).thenReturn(List.of(testUser));
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        assertThat(userService.getActiveDonors()).hasSize(1);
    }

    @Test
    void shouldGetActiveRecipients() {
        testUser.setRole(UserRole.RECIPIENT);
        when(userRepository.findAllActiveRecipients(UserRole.RECIPIENT)).thenReturn(List.of(testUser));
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        assertThat(userService.getActiveRecipients()).hasSize(1);
    }

    @Test
    void shouldCountUsersByRole() {
        when(userRepository.countByRole(UserRole.DONOR)).thenReturn(5L);
        assertThat(userService.countUsersByRole(UserRole.DONOR)).isEqualTo(5L);
    }

    @Test
    void shouldCountActiveUsers() {
        when(userRepository.countActiveUsers()).thenReturn(10L);
        assertThat(userService.countActiveUsers()).isEqualTo(10L);
    }

    // ── password ──────────────────────────────────────────────────────────────

    @Test
    void updatePassword_correctOldPassword_savesNewHash() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldpass", "hashedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newpass123")).thenReturn("newHash");
        when(userRepository.save(any())).thenReturn(testUser);

        userService.updatePassword("test-id-123", "oldpass", "newpass123");

        verify(userRepository).save(argThat(u -> "newHash".equals(u.getPassword())));
    }

    @Test
    void updatePassword_wrongOldPassword_throwsInvalidPasswordException() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong", "hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> userService.updatePassword("test-id-123", "wrong", "newpass123"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("Old password is incorrect");
    }

    @Test
    void updatePassword_tooShortNewPassword_throwsInvalidPasswordException() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldpass", "hashedPassword")).thenReturn(true);

        assertThatThrownBy(() -> userService.updatePassword("test-id-123", "oldpass", "short"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("8 characters");
    }

    @Test
    void resetPassword_valid_savesNewHash() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newpass123")).thenReturn("resetHash");
        when(userRepository.save(any())).thenReturn(testUser);

        userService.resetPassword("test-id-123", "newpass123");

        verify(userRepository).save(argThat(u -> "resetHash".equals(u.getPassword())));
    }

    @Test
    void resetPassword_tooShort_throwsInvalidPasswordException() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.resetPassword("test-id-123", "short"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void verifyPassword_matchingPassword_returnsTrue() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);

        assertThat(userService.verifyPassword("test-id-123", "password123")).isTrue();
    }

    // ── image ─────────────────────────────────────────────────────────────────

    @Test
    void uploadProfileImage_success_updatesImageId() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        when(file.getOriginalFilename()).thenReturn("photo.jpg");
        when(file.getContentType()).thenReturn("image/jpeg");

        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(imageServiceClient.uploadProfileImage(anyString(), any(), anyString(), anyString()))
                .thenReturn("img-new-001");
        when(userRepository.save(any())).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        userService.uploadProfileImage("test-id-123", file);

        assertThat(testUser.getImageId()).isEqualTo("img-new-001");
    }

    @Test
    void uploadProfileImage_ioException_throwsIllegalArgument() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new IOException("disk error"));
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.uploadProfileImage("test-id-123", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Could not read uploaded file");
    }

    @Test
    void deleteProfileImage_clearsImageId() {
        testUser.setImageId("old-img-id");
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        userService.deleteProfileImage("test-id-123");

        assertThat(testUser.getImageId()).isNull();
    }

    @Test
    void getUserById_withImageId_inflatesImageUrl() {
        UserResponse responseWithImage = UserResponse.builder()
                .id("test-id-123").imageId("img-001").build();
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(responseWithImage);
        when(imageServiceClient.getById("img-001"))
                .thenReturn(Optional.of(new ImageServiceClient.ImageView("img-001", "https://cdn/img-001.jpg")));

        UserResponse result = userService.getUserById("test-id-123");

        assertThat(result.getImageUrl()).isEqualTo("https://cdn/img-001.jpg");
    }

    // ── donations ─────────────────────────────────────────────────────────────

    @Test
    void getDonationsByUserId_success_returnsMappedView() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));
        DonationListResponse resp = DonationListResponse.newBuilder()
                .setTotalCount(0).setPage(0).setPageSize(10).build();
        when(donationServiceClient.getDonationsByUserId("test-id-123", 0, 10)).thenReturn(resp);

        DonationListView result = userService.getDonationsByUserId("test-id-123", 0, 10);

        assertThat(result.getTotalCount()).isZero();
    }

    @Test
    void getDonationsByUserId_userNotFound_throwsException() {
        when(userRepository.findById("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getDonationsByUserId("bad-id", 0, 10))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ── gRPC entity helpers ───────────────────────────────────────────────────

    @Test
    void createUser_entity_newEmail_returnsEntity() {
        when(userRepository.existsByEmail("john@test.com")).thenReturn(false);
        when(userRepository.save(testUser)).thenReturn(testUser);

        User result = userService.createUser(testUser);

        assertThat(result).isEqualTo(testUser);
    }

    @Test
    void createUser_entity_duplicateEmail_throwsException() {
        when(userRepository.existsByEmail("john@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(testUser))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void updateUser_entity_existing_savesAndReturns() {
        when(userRepository.existsById("test-id-123")).thenReturn(true);
        when(userRepository.save(testUser)).thenReturn(testUser);

        User result = userService.updateUser(testUser);

        assertThat(result).isEqualTo(testUser);
    }

    @Test
    void updateUser_entity_notFound_throwsException() {
        when(userRepository.existsById("test-id-123")).thenReturn(false);

        assertThatThrownBy(() -> userService.updateUser(testUser))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getUserEntityById_returnsUserEntity() {
        when(userRepository.findById("test-id-123")).thenReturn(Optional.of(testUser));

        User result = userService.getUserEntityById("test-id-123");

        assertThat(result).isEqualTo(testUser);
    }

    @Test
    void getUserEntityByEmail_returnsUserEntity() {
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(testUser));

        User result = userService.getUserEntityByEmail("john@test.com");

        assertThat(result).isEqualTo(testUser);
    }

    @Test
    void getUsersByRoleAndActive_returnsMatchingEntities() {
        when(userRepository.findByRoleAndIsActive(UserRole.DONOR, true)).thenReturn(List.of(testUser));

        List<User> result = userService.getUsersByRoleAndActive(UserRole.DONOR, true);

        assertThat(result).hasSize(1);
    }

    @Test
    void getUsersByActive_returnsMatchingEntities() {
        when(userRepository.findByIsActive(true)).thenReturn(List.of(testUser));

        List<User> result = userService.getUsersByActive(true);

        assertThat(result).hasSize(1);
    }

    @Test
    void getAllUserEntities_returnsAll() {
        when(userRepository.findAll()).thenReturn(List.of(testUser));

        List<User> result = userService.getAllUserEntities();

        assertThat(result).hasSize(1);
    }

    @Test
    void getUserEntitiesByRole_returnsMatchingEntities() {
        when(userRepository.findByRole(UserRole.DONOR)).thenReturn(List.of(testUser));

        List<User> result = userService.getUserEntitiesByRole(UserRole.DONOR);

        assertThat(result).hasSize(1);
    }
}
