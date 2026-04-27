package com.urban.settlement;

import com.urban.settlement.model.Officer;
import com.urban.settlement.model.User;
import com.urban.settlement.repository.OfficerRepository;
import com.urban.settlement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataFixRunner implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfficerRepository officerRepository;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- DATA FIX RUNNER ---");
        
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.getRole() == com.urban.settlement.model.enums.Role.OFFICER && user.getOfficerId() == null) {
                System.out.println("Fixing User: " + user.getEmail());
                
                // Try to find officer by name or email
                List<Officer> officers = officerRepository.findAll();
                for (Officer officer : officers) {
                    if (officer.getEmail().equalsIgnoreCase(user.getEmail())) {
                        user.setOfficerId(officer.getId());
                        userRepository.save(user);
                        System.out.println("Linked to Officer ID: " + officer.getId());
                        break;
                    }
                }
            }
        }
        System.out.println("--- DATA FIX COMPLETE ---");
    }
}
