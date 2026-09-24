package com.campuscommute.campuscommute.auth;

import com.campuscommute.campuscommute.auth.dto.UserProfileResponse;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final StudentDirectoryRepository studentDirectoryRepository;

    public UserService(UserRepository userRepository, StudentDirectoryRepository studentDirectoryRepository) {
        this.userRepository = userRepository;
        this.studentDirectoryRepository = studentDirectoryRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        StudentDirectory student = studentDirectoryRepository.findById(user.getRegisterNumber()).orElse(null);

        return UserProfileResponse.from(user, student);
    }

    @Transactional
    public UserProfileResponse updateUserRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        user.setRole(newRole);
        user = userRepository.save(user);

        StudentDirectory student = studentDirectoryRepository.findById(user.getRegisterNumber()).orElse(null);

        return UserProfileResponse.from(user, student);
    }
}
