package com.campuscommute.campuscommute.auth.dto;

import com.campuscommute.campuscommute.auth.Role;
import com.campuscommute.campuscommute.auth.StudentDirectory;
import com.campuscommute.campuscommute.auth.User;

public record UserProfileResponse(
        Long userId,
        String registerNumber,
        String fullName,
        String department,
        Integer yearOfStudy,
        String phoneNumber,
        Role role
) {
    public static UserProfileResponse from(User user, StudentDirectory studentDirectory) {
        String fullName = studentDirectory != null ? studentDirectory.getFullName() : null;
        String department = studentDirectory != null ? studentDirectory.getDepartment() : null;
        Integer yearOfStudy = studentDirectory != null ? studentDirectory.getYearOfStudy() : null;

        return new UserProfileResponse(
                user.getUserId(),
                user.getRegisterNumber(),
                fullName,
                department,
                yearOfStudy,
                user.getPhoneNumber(),
                user.getRole()
        );
    }
}
