package com.greengrub.usermanagement.config;

import com.greengrub.usermanagement.entity.User;
import com.greengrub.usermanagement.entity.UserRole;
import com.greengrub.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * Data Initializer - DEVELOPMENT/DEMO ONLY
 *
 * ⚠️ SECURITY NOTICE:
 * This file contains hardcoded sample passwords for development and testing purposes.
 *
 * IMPORTANT:
 * - These are NOT production credentials
 * - MUST be disabled in production: app.data.init.enabled=false
 * - Sample passwords are BCrypt hashed before database storage
 * - Safe for public repos as these are clearly marked as demo data
 *
 * OPTIONAL: This file can be safely deleted from the repository.
 * The application will run normally without it (users register via API).
 *
 * Configuration:
 * - Enable: app.data.init.enabled=true (default for development)
 * - Disable: app.data.init.enabled=false (required for production)
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "app.data.init.enabled",
    havingValue = "true",
    matchIfMissing = true  // Enabled by default if property is not set
)
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            // Check if data already exists
            if (userRepository.count() > 0) {
                log.info("Database already contains {} users. Skipping data initialization.", userRepository.count());
                return;
            }

            log.info("Initializing database with sample users...");

            // Create sample users
            List<User> users = List.of(
                    // Admin User
                    User.builder()
                            .name("Admin User")
                            .email("admin@greengrub.com")
                            .password(passwordEncoder.encode("Admin@2024"))
                            .phoneNumber("1234567890")
                            .address("123 Admin Street, San Francisco, CA 94105")
                            .role(UserRole.ADMIN)
                            .isActive(true)
                            .build(),

                    // Food Donors
                    User.builder()
                            .name("John's Restaurant")
                            .email("john.restaurant@example.com")
                            .password(passwordEncoder.encode("Donor@2024"))
                            .phoneNumber("4155551001")
                            .address("456 Market Street, San Francisco, CA 94102")
                            .role(UserRole.DONOR)
                            .isActive(true)
                            .build(),

                    User.builder()
                            .name("Maria's Cafe")
                            .email("maria.cafe@example.com")
                            .password(passwordEncoder.encode("Donor@2024"))
                            .phoneNumber("4155551002")
                            .address("789 Mission Street, San Francisco, CA 94103")
                            .role(UserRole.DONOR)
                            .isActive(true)
                            .build(),

                    User.builder()
                            .name("Golden Gate Bakery")
                            .email("goldenbakery@example.com")
                            .password(passwordEncoder.encode("Donor@2024"))
                            .phoneNumber("4155551003")
                            .address("321 Powell Street, San Francisco, CA 94108")
                            .role(UserRole.DONOR)
                            .isActive(true)
                            .build(),

                    User.builder()
                            .name("Fresh Foods Market")
                            .email("freshfoods@example.com")
                            .password(passwordEncoder.encode("Donor@2024"))
                            .phoneNumber("4155551004")
                            .address("567 Valencia Street, San Francisco, CA 94110")
                            .role(UserRole.DONOR)
                            .isActive(true)
                            .build(),

                    // Food Recipients
                    User.builder()
                            .name("Hope Community Center")
                            .email("hope.center@example.com")
                            .password(passwordEncoder.encode("Recipient@2024"))
                            .phoneNumber("4155552001")
                            .address("100 Community Drive, San Francisco, CA 94112")
                            .role(UserRole.RECIPIENT)
                            .isActive(true)
                            .build(),

                    User.builder()
                            .name("St. Mary's Food Bank")
                            .email("stmarys.foodbank@example.com")
                            .password(passwordEncoder.encode("Recipient@2024"))
                            .phoneNumber("4155552002")
                            .address("200 Charity Lane, San Francisco, CA 94115")
                            .role(UserRole.RECIPIENT)
                            .isActive(true)
                            .build(),

                    User.builder()
                            .name("Helping Hands Shelter")
                            .email("helpinghands@example.com")
                            .password(passwordEncoder.encode("Recipient@2024"))
                            .phoneNumber("4155552003")
                            .address("300 Support Avenue, San Francisco, CA 94117")
                            .role(UserRole.RECIPIENT)
                            .isActive(true)
                            .build(),

                    User.builder()
                            .name("Unity Mission")
                            .email("unity.mission@example.com")
                            .password(passwordEncoder.encode("Recipient@2024"))
                            .phoneNumber("4155552004")
                            .address("400 Hope Street, San Francisco, CA 94118")
                            .role(UserRole.RECIPIENT)
                            .isActive(true)
                            .build(),

                    User.builder()
                            .name("Care Foundation")
                            .email("care.foundation@example.com")
                            .password(passwordEncoder.encode("Recipient@2024"))
                            .phoneNumber("4155552005")
                            .address("500 Compassion Road, San Francisco, CA 94121")
                            .role(UserRole.RECIPIENT)
                            .isActive(true)
                            .build(),

                    // Additional Test User
                    User.builder()
                            .name("Test User")
                            .email("test@example.com")
                            .password(passwordEncoder.encode("Password123"))
                            .phoneNumber("5555555555")
                            .address("123 Test Street, San Francisco, CA 94101")
                            .role(UserRole.DONOR)
                            .isActive(true)
                            .build()
            );

            // Save all users
            userRepository.saveAll(users);

            log.info("✅ Successfully initialized database with {} sample users", users.size());
            log.info("Sample credentials:");
            log.info("  Admin: admin@greengrub.com / Admin@2024");
            log.info("  Donor: john.restaurant@example.com / Donor@2024");
            log.info("  Recipient: hope.center@example.com / Recipient@2024");
            log.info("  Test: test@example.com / Password123");
        };
    }
}
