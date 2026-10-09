package com.travel_system.backend_app.service.strategies.profile;

import com.travel_system.backend_app.interfaces.ProfilePictureStrategy;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.StudentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class StudentProfilePicturePictureStrategy implements ProfilePictureStrategy {

    private final StudentRepository studentRepository;

    public StudentProfilePicturePictureStrategy(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }


    @Override
    public boolean supports(UserAccountType userAccountType) {
        return userAccountType == UserAccountType.STUDENT;
    }

    @Override
    public void updatePicture(UserAccount userAccount, String pictureKey) {
        Student student = studentRepository.findByEmail(userAccount.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Student não encontrado pelo email: " + userAccount.getEmail()));

        student.setProfilePicture(pictureKey);
        studentRepository.save(student);
    }

    @Override
    public void deletePicture(UserAccount userAccount) {
        Student student = studentRepository.findByEmail(userAccount.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Student não encontrado pelo email: " + userAccount.getEmail()));

        student.setProfilePicture(null);
        studentRepository.save(student);
    }
}
