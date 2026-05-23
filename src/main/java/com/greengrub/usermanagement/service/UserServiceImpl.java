package com.greengrub.usermanagement.service;

import com.greengrub.proto.donation.DonationListResponse;
import com.greengrub.proto.donation.DonationResponse;
import com.greengrub.proto.donation.Quantity;
import com.greengrub.usermanagement.client.DonationServiceClient;
import com.greengrub.usermanagement.client.ImageServiceClient;
import com.greengrub.usermanagement.dto.DonationListView;
import com.greengrub.usermanagement.dto.UserCreateRequest;
import com.greengrub.usermanagement.dto.UserResponse;
import com.greengrub.usermanagement.dto.UserUpdateRequest;
import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.exception.InvalidPasswordException;
import com.greengrub.usermanagement.exception.UserAlreadyExistsException;
import com.greengrub.usermanagement.exception.UserNotFoundException;
import com.greengrub.usermanagement.exception.UserStorageException;
import com.greengrub.usermanagement.mapper.UserMapper;
import com.greengrub.usermanagement.repository.UserRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private static final String RETRY_NAME = "userRetry";
    private static final String CB_NAME = "userBreaker";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ImageServiceClient imageServiceClient;
    private final DonationServiceClient donationServiceClient;

    @Override
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        log.info("Creating new user with email: {}", request.getEmail());

        if (existsByEmailInternal(request.getEmail())) {
            log.warn("User already exists with email: {}", request.getEmail());
            throw new UserAlreadyExistsException(request.getEmail());
        }

        User user = userMapper.toEntity(request);
        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);

        User savedUser = saveUser(user);
        log.info("User created successfully with id: {}", savedUser.getId());

        return userMapper.toResponse(savedUser);
    }

    @Override
    public UserResponse getUserById(String userId) {
        log.info("Fetching user by id: {}", userId);
        return inflateImageUrl(userMapper.toResponse(findUserByIdOrThrow(userId)));
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        log.info("Fetching user by email: {}", email);
        return inflateImageUrl(userMapper.toResponse(findUserByEmailOrThrow(email)));
    }

    @Override
    public List<UserResponse> getAllUsers() {
        log.info("Fetching all users");
        return findAllUsers().stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getActiveUsers() {
        log.info("Fetching all active users");
        return findActiveUsers().stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getUsersByRole(UserRole role) {
        log.info("Fetching users by role: {}", role);
        return findUsersByRole(role).stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponse updateUser(String userId, UserUpdateRequest request) {
        log.info("Updating user with id: {}", userId);

        User user = findUserByIdOrThrow(userId);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (existsByEmailInternal(request.getEmail())) {
                log.warn("Email already exists: {}", request.getEmail());
                throw new UserAlreadyExistsException(request.getEmail());
            }
        }

        userMapper.updateEntity(user, request);
        User updatedUser = saveUser(user);
        log.info("User updated successfully with id: {}", updatedUser.getId());

        return userMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional
    public void updatePassword(String userId, String oldPassword, String newPassword) {
        log.info("Updating password for user with id: {}", userId);

        User user = findUserByIdOrThrow(userId);

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("Invalid old password provided for user: {}", userId);
            throw new InvalidPasswordException("Old password is incorrect");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidPasswordException("New password must be at least 8 characters long");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        saveUser(user);

        log.info("Password updated successfully for user: {}", userId);
    }

    @Override
    @Transactional
    public void resetPassword(String userId, String newPassword) {
        log.info("Resetting password for user with id: {}", userId);

        User user = findUserByIdOrThrow(userId);

        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidPasswordException("New password must be at least 8 characters long");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        saveUser(user);

        log.info("Password reset successfully for user: {}", userId);
    }

    @Override
    public boolean verifyPassword(String userId, String password) {
        log.debug("Verifying password for user with id: {}", userId);
        User user = findUserByIdOrThrow(userId);
        return passwordEncoder.matches(password, user.getPassword());
    }

    @Override
    @Transactional
    public void deleteUser(String userId) {
        log.info("Soft deleting user with id: {}", userId);

        User user = findUserByIdOrThrow(userId);
        user.deactivate();
        saveUser(user);

        log.info("User soft deleted successfully with id: {}", userId);
    }

    @Override
    @Transactional
    public void permanentlyDeleteUser(String userId) {
        log.info("Permanently deleting user with id: {}", userId);

        if (!existsByIdInternal(userId)) {
            throw new UserNotFoundException(userId);
        }

        deleteUserById(userId);
        log.info("User permanently deleted with id: {}", userId);
    }

    @Override
    @Transactional
    public UserResponse activateUser(String userId) {
        log.info("Activating user with id: {}", userId);

        User user = findUserByIdOrThrow(userId);
        user.activate();
        User activatedUser = saveUser(user);

        log.info("User activated successfully with id: {}", userId);
        return userMapper.toResponse(activatedUser);
    }

    @Override
    @Transactional
    public UserResponse deactivateUser(String userId) {
        log.info("Deactivating user with id: {}", userId);

        User user = findUserByIdOrThrow(userId);
        user.deactivate();
        User deactivatedUser = saveUser(user);

        log.info("User deactivated successfully with id: {}", userId);
        return userMapper.toResponse(deactivatedUser);
    }

    @Override
    public boolean existsByEmail(String email) {
        return existsByEmailInternal(email);
    }

    @Override
    public List<UserResponse> searchUsersByName(String name) {
        log.info("Searching users by name: {}", name);
        return searchByName(name).stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getActiveDonors() {
        log.info("Fetching all active donors");
        return findActiveDonors().stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getActiveRecipients() {
        log.info("Fetching all active recipients");
        return findActiveRecipients().stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public long countUsersByRole(UserRole role) {
        return countByRoleInternal(role);
    }

    @Override
    public long countActiveUsers() {
        return countActiveInternal();
    }

    // ============= gRPC Helper Methods =============

    @Override
    @Transactional
    public User createUser(User user) {
        log.info("Creating user via gRPC: {}", user.getEmail());

        if (existsByEmailInternal(user.getEmail())) {
            throw new UserAlreadyExistsException(user.getEmail());
        }

        return saveUser(user);
    }

    @Override
    @Transactional
    public User updateUser(User user) {
        log.info("Updating user via gRPC: {}", user.getId());

        if (!existsByIdInternal(user.getId())) {
            throw new UserNotFoundException(user.getId());
        }

        return saveUser(user);
    }

    @Override
    public User getUserEntityById(String userId) {
        log.debug("Fetching user entity by ID: {}", userId);
        return findUserByIdOrThrow(userId);
    }

    @Override
    public User getUserEntityByEmail(String email) {
        log.debug("Fetching user entity by email: {}", email);
        return findUserByEmailOrThrow(email);
    }

    @Override
    public List<User> getUsersByRoleAndActive(UserRole role, boolean isActive) {
        log.debug("Fetching users by role: {} and active: {}", role, isActive);
        return findByRoleAndActive(role, isActive);
    }

    @Override
    public List<User> getUsersByActive(boolean isActive) {
        log.debug("Fetching users by active status: {}", isActive);
        return findByActive(isActive);
    }

    @Override
    public List<User> getAllUserEntities() {
        log.debug("Fetching all user entities");
        return findAllUsers();
    }

    @Override
    public List<User> getUserEntitiesByRole(UserRole role) {
        log.debug("Fetching user entities by role: {}", role);
        return findUsersByRole(role);
    }

    // ============= Profile image =============

    @Override
    @Transactional
    public UserResponse uploadProfileImage(String userId, MultipartFile file) {
        log.info("Uploading profile image for userId: {} ({} bytes)", userId, file.getSize());

        User user = findUserByIdOrThrow(userId);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            // Reading the multipart payload failed before we ever called image-service.
            throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage());
        }

        // image-service call lives behind imageServiceBreaker — failure here throws
        // ImageServiceException (→ 503) and the user row is NOT modified, so we never
        // leave a half-set imageId pointing at nothing.
        String newImageId = imageServiceClient.uploadProfileImage(
                userId,
                bytes,
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "profile",
                file.getContentType() != null ? file.getContentType() : "application/octet-stream");

        user.setImageId(newImageId);
        User saved = saveUser(user);

        log.info("Profile image uploaded for userId: {} → imageId: {}", userId, newImageId);
        return inflateImageUrl(userMapper.toResponse(saved));
    }

    @Override
    @Transactional
    public UserResponse deleteProfileImage(String userId) {
        log.info("Clearing profile image pointer for userId: {}", userId);

        User user = findUserByIdOrThrow(userId);
        user.setImageId(null);
        User saved = saveUser(user);

        return userMapper.toResponse(saved);
    }

    @Override
    public DonationListView getDonationsByUserId(String userId, int page, int pageSize) {
        log.info("Fetching donations for userId={} page={} pageSize={}", userId, page, pageSize);

        // Confirm the user actually exists before we hit donation-service. Avoids
        // a successful "empty list" response for a userId that was never valid.
        findUserByIdOrThrow(userId);

        DonationListResponse response = donationServiceClient.getDonationsByUserId(userId, page, pageSize);
        return mapDonationListResponse(response);
    }

    private DonationListView mapDonationListResponse(DonationListResponse response) {
        List<DonationListView.DonationView> donations = response.getDonationsList().stream()
                .map(this::mapDonation)
                .collect(Collectors.toList());

        return DonationListView.builder()
                .donations(donations)
                .totalCount(response.getTotalCount())
                .page(response.getPage())
                .pageSize(response.getPageSize())
                .build();
    }

    private DonationListView.DonationView mapDonation(DonationResponse d) {
        return DonationListView.DonationView.builder()
                .id(d.getId())
                .donationName(d.getDonationName())
                .pickUpAddress(d.getPickUpAddress())
                .pickUpTime(d.getPickUpTime())
                .estimatedQuantity(mapQuantity(d.getEstimatedQuantity()))
                .foodItemsId(List.copyOf(d.getFoodItemsIdList()))
                .status(d.getStatus().name())
                .creationDate(d.getCreationDate())
                .updateDate(d.getUpdateDate())
                .build();
    }

    private DonationListView.QuantityView mapQuantity(Quantity q) {
        return DonationListView.QuantityView.builder()
                .amount(q.getAmount())
                .unit(q.getUnit().name())
                .build();
    }

    /**
     * Best-effort URL inflation for single-user reads. List endpoints intentionally
     * skip this to avoid an N×RPC fan-out — the frontend can resolve URLs lazily.
     * If image-service is down, the user response still goes out with imageUrl=null.
     */
    private UserResponse inflateImageUrl(UserResponse response) {
        if (response == null || response.getImageId() == null) {
            return response;
        }
        imageServiceClient.getById(response.getImageId())
                .ifPresent(view -> response.setImageUrl(view.imageUrl()));
        return response;
    }

    // ============= Resilience-wrapped repository helpers =============
    // Annotations live on private methods; business exceptions (UserNotFoundException,
    // UserAlreadyExistsException, InvalidPasswordException) propagate without retry.
    // Generic exceptions are wrapped in UserStorageException, which the retry policy targets.

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected User saveUser(User user) {
        try {
            return userRepository.save(user);
        } catch (Exception e) {
            log.error("Transient failure saving user: {}", e.getMessage());
            throw new UserStorageException("Failed to save user", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected User findUserByIdOrThrow(String userId) {
        try {
            return userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Transient failure finding user by id {}: {}", userId, e.getMessage());
            throw new UserStorageException("Failed to fetch user by id", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected User findUserByEmailOrThrow(String email) {
        try {
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("email", email));
        } catch (UserNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Transient failure finding user by email {}: {}", email, e.getMessage());
            throw new UserStorageException("Failed to fetch user by email", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected boolean existsByEmailInternal(String email) {
        try {
            return userRepository.existsByEmail(email);
        } catch (Exception e) {
            log.error("Transient failure checking email existence {}: {}", email, e.getMessage());
            throw new UserStorageException("Failed to check email existence", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected boolean existsByIdInternal(String userId) {
        try {
            return userRepository.existsById(userId);
        } catch (Exception e) {
            log.error("Transient failure checking id existence {}: {}", userId, e.getMessage());
            throw new UserStorageException("Failed to check user existence", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected void deleteUserById(String userId) {
        try {
            userRepository.deleteById(userId);
        } catch (Exception e) {
            log.error("Transient failure deleting user {}: {}", userId, e.getMessage());
            throw new UserStorageException("Failed to delete user", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected List<User> findAllUsers() {
        try {
            return userRepository.findAll();
        } catch (Exception e) {
            log.error("Transient failure fetching all users: {}", e.getMessage());
            throw new UserStorageException("Failed to fetch users", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected List<User> findActiveUsers() {
        try {
            return userRepository.findByIsActiveTrue();
        } catch (Exception e) {
            log.error("Transient failure fetching active users: {}", e.getMessage());
            throw new UserStorageException("Failed to fetch active users", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected List<User> findUsersByRole(UserRole role) {
        try {
            return userRepository.findByRole(role);
        } catch (Exception e) {
            log.error("Transient failure fetching users by role {}: {}", role, e.getMessage());
            throw new UserStorageException("Failed to fetch users by role", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected List<User> searchByName(String name) {
        try {
            return userRepository.findByNameContainingIgnoreCase(name);
        } catch (Exception e) {
            log.error("Transient failure searching by name {}: {}", name, e.getMessage());
            throw new UserStorageException("Failed to search users by name", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected List<User> findActiveDonors() {
        try {
            return userRepository.findAllActiveDonors(UserRole.DONOR);
        } catch (Exception e) {
            log.error("Transient failure fetching active donors: {}", e.getMessage());
            throw new UserStorageException("Failed to fetch active donors", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected List<User> findActiveRecipients() {
        try {
            return userRepository.findAllActiveRecipients(UserRole.RECIPIENT);
        } catch (Exception e) {
            log.error("Transient failure fetching active recipients: {}", e.getMessage());
            throw new UserStorageException("Failed to fetch active recipients", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected long countByRoleInternal(UserRole role) {
        try {
            return userRepository.countByRole(role);
        } catch (Exception e) {
            log.error("Transient failure counting users by role {}: {}", role, e.getMessage());
            throw new UserStorageException("Failed to count users by role", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected long countActiveInternal() {
        try {
            return userRepository.countActiveUsers();
        } catch (Exception e) {
            log.error("Transient failure counting active users: {}", e.getMessage());
            throw new UserStorageException("Failed to count active users", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected List<User> findByRoleAndActive(UserRole role, boolean isActive) {
        try {
            return userRepository.findByRoleAndIsActive(role, isActive);
        } catch (Exception e) {
            log.error("Transient failure fetching users by role/active: {}", e.getMessage());
            throw new UserStorageException("Failed to fetch users by role and active flag", e);
        }
    }

    @Retry(name = RETRY_NAME)
    @CircuitBreaker(name = CB_NAME)
    protected List<User> findByActive(boolean isActive) {
        try {
            return userRepository.findByIsActive(isActive);
        } catch (Exception e) {
            log.error("Transient failure fetching users by active flag: {}", e.getMessage());
            throw new UserStorageException("Failed to fetch users by active flag", e);
        }
    }
}
