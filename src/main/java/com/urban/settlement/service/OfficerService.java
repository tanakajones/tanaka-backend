package com.urban.settlement.service;

import com.urban.settlement.model.Officer;
import com.urban.settlement.model.User;
import com.urban.settlement.model.enums.AvailabilityStatus;
import com.urban.settlement.model.enums.Role;
import com.urban.settlement.repository.OfficerRepository;
import com.urban.settlement.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Service for Officer management
 * Handles availability and workload tracking
 */
@Service
public class OfficerService {

    private static final Logger logger = LoggerFactory.getLogger(OfficerService.class);

    @Autowired
    private OfficerRepository officerRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Create new officer
     */
    public Officer createOfficer(Officer officer) {
        Officer saved = officerRepository.save(officer);
        logger.info("Created officer: {}", saved.getId());
        return saved;
    }

    /**
     * Get officer by ID
     */
    public Officer getOfficerById(String id) {
        return officerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Officer not found: " + id));
    }

    /**
     * Get all officers (Consolidated from Officer table and Users with role OFFICER)
     */
    public List<Officer> getAllOfficers() {
        List<Officer> officers = new ArrayList<>(officerRepository.findAll());
        List<User> userOfficers = userRepository.findAllByRole(Role.OFFICER);

        for (User user : userOfficers) {
            boolean exists = officers.stream()
                .anyMatch(o -> o.getEmail().equalsIgnoreCase(user.getEmail()));
            
            if (!exists) {
                officers.add(convertToOfficer(user));
            }
        }
        return officers;
    }

    /**
     * Get available officers (Consolidated)
     */
    public List<Officer> getAvailableOfficers() {
        List<Officer> available = new ArrayList<>(officerRepository.findByAvailabilityStatus(AvailabilityStatus.AVAILABLE));
        List<User> userOfficers = userRepository.findAllByRole(Role.OFFICER);

        for (User user : userOfficers) {
            boolean existsInAvailable = available.stream()
                .anyMatch(o -> o.getEmail().equalsIgnoreCase(user.getEmail()));
            
            boolean existsInAllOfficers = officerRepository.findAll().stream()
                .anyMatch(o -> o.getEmail().equalsIgnoreCase(user.getEmail()));

            if (!existsInAvailable && !existsInAllOfficers) {
                // Assuming users with role OFFICER are available by default if not in Officer table
                available.add(convertToOfficer(user));
            }
        }
        return available;
    }

    private Officer convertToOfficer(User user) {
        Officer officer = new Officer();
        officer.setId("USER_" + user.getId());
        officer.setName(user.getFirstname() + " " + user.getLastname());
        officer.setEmail(user.getEmail());
        officer.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        officer.setSkills(randomizeSkills());
        officer.setWorkload(0);
        officer.setMaxTasksPerDay(5);
        officer.setPerformanceScore(100.0);
        return officer;
    }

    private List<String> randomizeSkills() {
        List<String> allSkills = Arrays.asList(
            "ROAD_REPAIR", "DRAINAGE_MAINTENANCE", "WASTE_MANAGEMENT", 
            "ELECTRICAL", "SEWER_REPORT", "STREET_LIGHTING", "TRAFFIC_SIGNALS"
        );
        Random rand = new Random();
        int numSkills = rand.nextInt(3) + 1; // 1 to 3 random skills
        
        List<String> skills = new ArrayList<>();
        for (int i = 0; i < numSkills; i++) {
            String skill = allSkills.get(rand.nextInt(allSkills.size()));
            if (!skills.contains(skill)) {
                skills.add(skill);
            }
        }
        return skills;
    }

    /**
     * Get officers by skill
     */
    public List<Officer> getOfficersBySkill(String skill) {
        return officerRepository.findBySkillsContaining(skill);
    }

    /**
     * Update officer availability
     */
    public Officer updateAvailability(String officerId, AvailabilityStatus status) {
        Officer officer = getOfficerById(officerId);
        officer.setAvailabilityStatus(status);
        Officer updated = officerRepository.save(officer);
        logger.info("Updated officer {} availability to {}", officerId, status);
        return updated;
    }

    /**
     * Update officer
     */
    public Officer updateOfficer(String id, Officer updatedOfficer) {
        Officer existing = getOfficerById(id);

        if (updatedOfficer.getName() != null) {
            existing.setName(updatedOfficer.getName());
        }
        if (updatedOfficer.getEmail() != null) {
            existing.setEmail(updatedOfficer.getEmail());
        }
        if (updatedOfficer.getSkills() != null) {
            existing.setSkills(updatedOfficer.getSkills());
        }
        if (updatedOfficer.getCurrentLocation() != null) {
            existing.setCurrentLocation(updatedOfficer.getCurrentLocation());
        }

        return officerRepository.save(existing);
    }

    /**
     * Delete officer
     */
    public void deleteOfficer(String id) {
        officerRepository.deleteById(id);
        logger.info("Deleted officer: {}", id);
    }

    /**
     * Increment officer workload
     */
    public void incrementWorkload(String officerId) {
        Officer officer = getOfficerById(officerId);
        officer.incrementWorkload();
        officerRepository.save(officer);
    }

    public User getUserById(String userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    public List<String> randomizeSkillsForUser(User user) {
        return randomizeSkills();
    }

    /**
     * Decrement officer workload
     */
    public void decrementWorkload(String officerId) {
        Officer officer = getOfficerById(officerId);
        officer.decrementWorkload();
        officerRepository.save(officer);
    }
}
