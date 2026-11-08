package com.backend.serviceBalance.accounts.payloads.responses;

public record AccountResponse(Long id, String firstname, String lastname, String username, String email,
                              String phoneNumber, Long balance, String status, String role) {
}
