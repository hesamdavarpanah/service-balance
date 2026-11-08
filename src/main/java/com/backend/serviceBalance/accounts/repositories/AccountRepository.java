package com.backend.serviceBalance.accounts.repositories;

import com.backend.serviceBalance.accounts.models.Account;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface AccountRepository extends ReactiveCrudRepository<Account, String> {
}