package com.backend.serviceBalance.users.repositories;

import com.backend.serviceBalance.users.models.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface UserRepository extends ReactiveCrudRepository<User, Long> {
}