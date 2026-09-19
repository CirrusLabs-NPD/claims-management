package com.claire.claims.repository;

import com.claire.claims.domain.InsurancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {
    List<InsurancePolicy> findByPatientIdOrderByPriorityAsc(Long patientId);
}
