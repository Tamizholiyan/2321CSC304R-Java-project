package com.campuscommute.campuscommute.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentDirectoryRepository extends JpaRepository<StudentDirectory, String> {

    boolean existsByRegisterNumber(String registerNumber);
}
