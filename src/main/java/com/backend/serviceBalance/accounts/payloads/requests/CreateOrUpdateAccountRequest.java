package com.backend.serviceBalance.accounts.payloads.requests;

import com.backend.serviceBalance.accounts.models.enums.AccountRole;
import com.backend.serviceBalance.accounts.models.enums.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrUpdateAccountRequest {

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

    @Schema(description = "USER | ADMIN")
    private AccountStatus status;

    @Schema(description = "ACTIVE | INACTIVE | BLOCKED")
    private AccountRole role;

    @Schema(example = "SecurePas$1", description = "password should have numbers and symbols")
    private String password;

}
