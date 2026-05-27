package com.greengrub.usermanagement.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class EntityTest {

    // ── UserRole ──────────────────────────────────────────────────────────────

    @Test
    void userRole_valuesExist() {
        assertThat(UserRole.values()).hasSize(3);
        assertThat(UserRole.valueOf("DONOR")).isEqualTo(UserRole.DONOR);
        assertThat(UserRole.valueOf("RECIPIENT")).isEqualTo(UserRole.RECIPIENT);
        assertThat(UserRole.valueOf("ADMIN")).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void userRole_displayNames() {
        assertThat(UserRole.DONOR.getDisplayName()).isEqualTo("Food Donor");
        assertThat(UserRole.RECIPIENT.getDisplayName()).isEqualTo("Food Recipient");
        assertThat(UserRole.ADMIN.getDisplayName()).isEqualTo("System Administrator");
    }

    // ── User helpers ──────────────────────────────────────────────────────────

    @Test
    void user_activate_setsIsActiveTrue() {
        User user = User.builder().id("u1").isActive(false).build();
        user.activate();
        assertThat(user.getIsActive()).isTrue();
    }

    @Test
    void user_deactivate_setsIsActiveFalse() {
        User user = User.builder().id("u1").isActive(true).build();
        user.deactivate();
        assertThat(user.getIsActive()).isFalse();
    }

    @Test
    void user_hasRole_matchesCorrectRole() {
        User user = User.builder().id("u1").role(UserRole.DONOR).build();
        assertThat(user.hasRole(UserRole.DONOR)).isTrue();
        assertThat(user.hasRole(UserRole.RECIPIENT)).isFalse();
    }

    @Test
    void user_prePersist_setsIdAndTimestamps() {
        User user = new User();
        user.onCreate();
        assertThat(user.getId()).isNotBlank();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getIsActive()).isTrue();
    }

    @Test
    void user_prePersist_doesNotOverwriteExistingId() {
        User user = new User();
        user.setId("existing-id");
        user.onCreate();
        assertThat(user.getId()).isEqualTo("existing-id");
    }

    @Test
    void user_preUpdate_updatesTimestamp() throws InterruptedException {
        User user = User.builder().id("u1")
                .updatedAt(LocalDateTime.of(2024, 1, 1, 0, 0)).build();
        Thread.sleep(1);
        user.onUpdate();
        assertThat(user.getUpdatedAt()).isAfter(LocalDateTime.of(2024, 1, 1, 0, 0));
    }

    @Test
    void user_equalsAndHashCode_basedOnId() {
        User u1 = User.builder().id("same-id").name("Alice").build();
        User u2 = User.builder().id("same-id").name("Bob").build();
        assertThat(u1).isEqualTo(u2);
        assertThat(u1.hashCode()).isEqualTo(u2.hashCode());
    }
}
