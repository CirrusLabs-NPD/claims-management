package com.claire.claims.repository;

import com.claire.claims.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByMrn(String mrn);

    boolean existsByMrn(String mrn);

    /**
     * Derived query rather than JPQL with a nullable parameter: comparing a
     * bind parameter to NULL needs an explicit type hint on some dialects, and
     * this cannot get that wrong. The service branches on a blank search term.
     */
    Page<Patient> findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrMrnContainingIgnoreCase(
            String lastName, String firstName, String mrn, Pageable pageable);
}
