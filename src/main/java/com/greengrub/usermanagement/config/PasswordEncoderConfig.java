package com.greengrub.usermanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security configuration for password encoding
 * Provides BCryptPasswordEncoder for secure password hashing
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * BCryptPasswordEncoder bean for password hashing
     *
     * BCrypt is a secure password hashing algorithm that:
     * - Uses adaptive cost factor (strength = 10 by default)
     * - Automatically generates a salt for each password
     * - Produces a 60-character hash
     * - Is resistant to rainbow table attacks
     *
     * @return PasswordEncoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
