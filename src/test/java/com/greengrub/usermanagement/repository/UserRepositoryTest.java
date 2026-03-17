package com.greengrub.usermanagement.repository;

import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserRepository
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User donorUser;
    private User recipientUser;

    @BeforeEach
    void setUp() {
        // Clear repository
        userRepository.deleteAll();

        // Create test users
        donorUser = User.builder()
                .name("John Donor")
                .email("john.donor@test.com")
                .password("password123")
                .phoneNumber("1234567890")
                .address("123 Donor Street")
                .role(UserRole.DONOR)
                .isActive(true)
                .build();

        recipientUser = User.builder()
                .name("Jane Recipient")
                .email("jane.recipient@test.com")
                .password("password456")
                .phoneNumber("9876543210")
                .address("456 Recipient Avenue")
                .role(UserRole.RECIPIENT)
                .isActive(true)
                .build();
    }

    @Test
    void shouldSaveUser() {
        // When
        User savedUser = userRepository.save(donorUser);
        userRepository.flush(); // Force synchronization with database

        // Then
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getName()).isEqualTo("John Donor");
        assertThat(savedUser.getEmail()).isEqualTo("john.donor@test.com");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.DONOR);
        // CreatedAt and UpdatedAt will be set on flush
    }

    @Test
    void shouldFindUserByEmail() {
        // Given
        userRepository.save(donorUser);

        // When
        Optional<User> found = userRepository.findByEmail("john.donor@test.com");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("John Donor");
    }

    @Test
    void shouldReturnEmptyWhenEmailNotFound() {
        // When
        Optional<User> found = userRepository.findByEmail("nonexistent@test.com");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldCheckIfEmailExists() {
        // Given
        userRepository.save(donorUser);

        // When
        boolean exists = userRepository.existsByEmail("john.donor@test.com");
        boolean notExists = userRepository.existsByEmail("notfound@test.com");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void shouldFindUsersByRole() {
        // Given
        userRepository.save(donorUser);
        userRepository.save(recipientUser);

        // When
        List<User> donors = userRepository.findByRole(UserRole.DONOR);
        List<User> recipients = userRepository.findByRole(UserRole.RECIPIENT);

        // Then
        assertThat(donors).hasSize(1);
        assertThat(donors.get(0).getRole()).isEqualTo(UserRole.DONOR);
        assertThat(recipients).hasSize(1);
        assertThat(recipients.get(0).getRole()).isEqualTo(UserRole.RECIPIENT);
    }

    @Test
    void shouldFindActiveUsers() {
        // Given
        donorUser.setIsActive(true);
        recipientUser.setIsActive(false);
        userRepository.save(donorUser);
        userRepository.save(recipientUser);

        // When
        List<User> activeUsers = userRepository.findByIsActiveTrue();
        List<User> inactiveUsers = userRepository.findByIsActiveFalse();

        // Then
        assertThat(activeUsers).hasSize(1);
        assertThat(activeUsers.get(0).getIsActive()).isTrue();
        assertThat(inactiveUsers).hasSize(1);
        assertThat(inactiveUsers.get(0).getIsActive()).isFalse();
    }

    @Test
    void shouldFindUsersByRoleAndActiveStatus() {
        // Given
        User inactiveDonor = User.builder()
                .name("Inactive Donor")
                .email("inactive@test.com")
                .password("password")
                .phoneNumber("1111111111")
                .role(UserRole.DONOR)
                .isActive(false)
                .build();

        userRepository.save(donorUser);
        userRepository.save(inactiveDonor);

        // When
        List<User> activeDonors = userRepository.findByRoleAndIsActive(UserRole.DONOR, true);
        List<User> inactiveDonors = userRepository.findByRoleAndIsActive(UserRole.DONOR, false);

        // Then
        assertThat(activeDonors).hasSize(1);
        assertThat(activeDonors.get(0).getIsActive()).isTrue();
        assertThat(inactiveDonors).hasSize(1);
        assertThat(inactiveDonors.get(0).getIsActive()).isFalse();
    }

    @Test
    void shouldFindUsersByNameContaining() {
        // Given
        userRepository.save(donorUser);
        userRepository.save(recipientUser);

        // When
        List<User> usersWithJohn = userRepository.findByNameContainingIgnoreCase("john");
        List<User> usersWithJane = userRepository.findByNameContainingIgnoreCase("jane");
        List<User> usersWithRecipient = userRepository.findByNameContainingIgnoreCase("RECIPIENT");

        // Then
        assertThat(usersWithJohn).hasSize(1);
        assertThat(usersWithJohn.get(0).getName()).contains("John");
        assertThat(usersWithJane).hasSize(1);
        assertThat(usersWithRecipient).hasSize(1);
    }

    @Test
    void shouldFindUserByPhoneNumber() {
        // Given
        userRepository.save(donorUser);

        // When
        Optional<User> found = userRepository.findByPhoneNumber("1234567890");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getPhoneNumber()).isEqualTo("1234567890");
    }

    @Test
    void shouldCountUsersByRole() {
        // Given
        userRepository.save(donorUser);
        userRepository.save(recipientUser);

        // When
        long donorCount = userRepository.countByRole(UserRole.DONOR);
        long recipientCount = userRepository.countByRole(UserRole.RECIPIENT);

        // Then
        assertThat(donorCount).isEqualTo(1);
        assertThat(recipientCount).isEqualTo(1);
    }

    @Test
    void shouldCountActiveUsers() {
        // Given
        recipientUser.setIsActive(false);
        userRepository.save(donorUser);
        userRepository.save(recipientUser);

        // When
        long activeCount = userRepository.countActiveUsers();

        // Then
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void shouldFindActiveDonors() {
        // Given
        User inactiveDonor = User.builder()
                .name("Inactive Donor")
                .email("inactive@test.com")
                .password("password")
                .phoneNumber("1111111111")
                .role(UserRole.DONOR)
                .isActive(false)
                .build();

        userRepository.save(donorUser);
        userRepository.save(inactiveDonor);
        userRepository.save(recipientUser);

        // When
        List<User> activeDonors = userRepository.findAllActiveDonors(UserRole.DONOR);

        // Then
        assertThat(activeDonors).hasSize(1);
        assertThat(activeDonors.get(0).getRole()).isEqualTo(UserRole.DONOR);
        assertThat(activeDonors.get(0).getIsActive()).isTrue();
    }

    @Test
    void shouldFindActiveRecipients() {
        // Given
        userRepository.save(donorUser);
        userRepository.save(recipientUser);

        // When
        List<User> activeRecipients = userRepository.findAllActiveRecipients(UserRole.RECIPIENT);

        // Then
        assertThat(activeRecipients).hasSize(1);
        assertThat(activeRecipients.get(0).getRole()).isEqualTo(UserRole.RECIPIENT);
        assertThat(activeRecipients.get(0).getIsActive()).isTrue();
    }
}
