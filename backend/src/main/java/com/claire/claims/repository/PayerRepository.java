package com.claire.claims.repository;

import com.claire.claims.domain.Payer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PayerRepository extends JpaRepository<Payer, Long> {
    Optional<Payer> findByPayerCode(String payerCode);
    boolean existsByPayerCode(String payerCode);
}
