package com.playdata.userservice.user.repository;

import com.playdata.userservice.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findBySocialProviderAndSocialId(String socialId, String socialProvider);


}
