package com.claire.claims.repository;

import com.claire.claims.domain.ClaimStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClaimStatusHistoryRepository extends JpaRepository<ClaimStatusHistory, Long> {
    List<ClaimStatusHistory> findByClaimIdOrderByChangedAtAsc(Long claimId);
}
