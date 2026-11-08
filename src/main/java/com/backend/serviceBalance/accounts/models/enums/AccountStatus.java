package com.backend.serviceBalance.accounts.models.enums;

public enum AccountStatus {
    ACTIVE("active"), INACTIVE("inactive"), BLOCKED("blocked");

    private final String value;

    AccountStatus(String value) {
        this.value = value;
    }

    public static AccountStatus fromValue(String value) {
        if (value == null) return null;

        for (AccountStatus status : AccountStatus.values()) if (status.value.equalsIgnoreCase(value)) return status;

        throw new IllegalArgumentException("invalid AccountStatus value: " + value);
    }
}
