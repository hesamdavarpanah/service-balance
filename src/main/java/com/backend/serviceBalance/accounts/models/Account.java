package com.backend.serviceBalance.accounts.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("accounts")
public class Account {

    @Id
    private Long id;

    private String firstname;

    private String lastname;

    private String username;

    private String email;

    @Column("phone_number")
    private String phoneNumber;

    @JsonIgnore
    @Column("password_hash")
    private String passwordHash;

    private Long balance;

}