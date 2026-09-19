package com.claire.claims.repository;

import com.claire.claims.domain.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    Optional<Provider> findByNpi(String npi);
    boolean existsByNpi(String npi);
}
