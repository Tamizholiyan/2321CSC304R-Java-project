package com.campuscommute.campuscommute.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student_directory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StudentDirectory {

    @Id
    @Column(name = "register_number", length = 12, nullable = false)
    private String registerNumber;

    @Column(name = "full_name", length = 100, nullable = false)
    private String fullName;

    @Column(name = "department", length = 50, nullable = false)
    private String department;

    @Column(name = "year_of_study", nullable = false)
    private Integer yearOfStudy;
}
