package com.campuscommute.campuscommute.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByRegisterNumber(String registerNumber);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByRegisterNumber(String registerNumber);

    boolean existsByPhoneNumber(String phoneNumber);
}
