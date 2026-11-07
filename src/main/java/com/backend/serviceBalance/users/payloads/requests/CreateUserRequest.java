package com.backend.serviceBalance.users.payloads.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class CreateUserRequest {

    @Schema(example = "Hesam", description = "user firstname")
    private String firstname;

    @Schema(example = "Davarpanah", description = "user lastname")
    private String lastname;

    @Schema(example = "hesamdavarpanah", description = "username should be unique")
    private String username;

    @Schema(example = "hesamdavarpanah@gmail.com", description = "email should be unique")
    private String email;

    @Schema(example = "hesamdavarpanah@gmail.com", description = "phoneNumber should be 11 digit numbers and start with 09. (unique)")
    private String phoneNumber;

    @Schema(example = "SecurePas$1", description = "password should have numbers and symbols")
    private String password;

}
