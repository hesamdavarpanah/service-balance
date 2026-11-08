package com.backend.serviceBalance.accounts.models.enums;

public enum AccountRole {
    ADMIN("admin"), USER("user");

    private final String value;

    AccountRole(String value) {
        this.value = value;
    }

    public static AccountRole fromValue(String value) {
        if (value == null) return null;

        for (AccountRole accountRole : AccountRole.values())
            if (value.equalsIgnoreCase(accountRole.value)) return accountRole;

        throw new IllegalArgumentException("invalid AccountRole value: " + value);
    }
}
