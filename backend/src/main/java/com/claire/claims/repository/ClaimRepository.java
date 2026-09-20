package com.claire.claims.repository;

import com.claire.claims.domain.Claim;
import com.claire.claims.domain.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long>,
                                         JpaSpecificationExecutor<Claim> {

    /**
     * Overridden purely to attach an entity graph: without it the claims list
     * fires one extra query per row for patient, provider and payer.
     */
    @Override
    @EntityGraph(attributePaths = {"patient", "provider", "payer"})
    Page<Claim> findAll(Specification<Claim> spec, Pageable pageable);

    /**
     * Unpaged, sorted variant with the same entity graph as the paged list, so the
     * report's detail rows load patient, provider and payer without N+1 queries.
     */
    @Override
    @EntityGraph(attributePaths = {"patient", "provider", "payer"})
    List<Claim> findAll(Specification<Claim> spec, org.springframework.data.domain.Sort sort);

    @EntityGraph(attributePaths = {"patient", "provider", "payer", "policy"})
    Optional<Claim> findWithReferencesById(Long id);

    Optional<Claim> findByClaimNumber(String claimNumber);

    boolean existsByClaimNumber(String claimNumber);

    /**
     * Status counts and money totals in one pass, for the dashboard.
     * Returns rows of [status, count, totalCharge, paidAmount].
     */
    @Query("""
           SELECT c.status, COUNT(c), COALESCE(SUM(c.totalCharge), 0), COALESCE(SUM(c.paidAmount), 0)
           FROM Claim c
           GROUP BY c.status
           """)
    List<Object[]> summaryByStatus();

    @Query("SELECT COALESCE(SUM(c.totalCharge - c.paidAmount), 0) FROM Claim c WHERE c.status NOT IN :closed")
    BigDecimal outstandingReceivable(@Param("closed") List<ClaimStatus> closed);

    /**
     * Status counts and money totals for claims whose service period falls inside
     * [from, to], using the same date rule the claims list applies
     * (serviceDateFrom &gt;= from AND serviceDateTo &lt;= to). Returns rows of
     * [status, count, totalCharge, paidAmount]. Powers the report summary by status.
     */
    @Query("""
           SELECT c.status, COUNT(c), COALESCE(SUM(c.totalCharge), 0), COALESCE(SUM(c.paidAmount), 0)
           FROM Claim c
           WHERE c.serviceDateFrom >= :from AND c.serviceDateTo <= :to
           GROUP BY c.status
           """)
    List<Object[]> summaryByStatusInPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Same period window, grouped by CMS place-of-service code — the categorical
     * "type" dimension of the report. Returns rows of
     * [placeOfService, count, totalCharge, paidAmount].
     */
    @Query("""
           SELECT c.placeOfService, COUNT(c), COALESCE(SUM(c.totalCharge), 0), COALESCE(SUM(c.paidAmount), 0)
           FROM Claim c
           WHERE c.serviceDateFrom >= :from AND c.serviceDateTo <= :to
           GROUP BY c.placeOfService
           ORDER BY c.placeOfService
           """)
    List<Object[]> summaryByTypeInPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Outstanding receivable (open claims only) within the same period window. */
    @Query("""
           SELECT COALESCE(SUM(c.totalCharge - c.paidAmount), 0)
           FROM Claim c
           WHERE c.serviceDateFrom >= :from AND c.serviceDateTo <= :to AND c.status NOT IN :closed
           """)
    BigDecimal outstandingReceivableInPeriod(@Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("closed") List<ClaimStatus> closed);

    /** Highest sequence number issued this year, used to mint the next claim number. */
    @Query("SELECT MAX(c.claimNumber) FROM Claim c WHERE c.claimNumber LIKE CONCAT('CLM-', :year, '-%')")
    Optional<String> maxClaimNumberForYear(@Param("year") String year);
}
