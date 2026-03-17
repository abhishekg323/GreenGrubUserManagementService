package com.greengrub.usermanagement.entity;

/**
 * User role enumeration for GreenGrub application
 * Defines the types of users in the system
 */
public enum UserRole {
    DONOR("Food Donor"),
    RECIPIENT("Food Recipient"),
    ADMIN("System Administrator");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
