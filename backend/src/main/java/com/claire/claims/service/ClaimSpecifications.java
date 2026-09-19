package com.claire.claims.service;

import com.claire.claims.domain.Claim;
import com.claire.claims.domain.ClaimStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Composable filters for the claims list. Each returns null when unset. */
public final class ClaimSpecifications {

    private ClaimSpecifications() { }

    public static Specification<Claim> hasStatusIn(List<ClaimStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) return null;
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Claim> hasPayer(Long payerId) {
        if (payerId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("payer").get("id"), payerId);
    }

    public static Specification<Claim> hasPatient(Long patientId) {
        if (patientId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("patient").get("id"), patientId);
    }

    public static Specification<Claim> hasProvider(Long providerId) {
        if (providerId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("provider").get("id"), providerId);
    }

    public static Specification<Claim> serviceDateFrom(LocalDate from) {
        if (from == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("serviceDateFrom"), from);
    }

    public static Specification<Claim> serviceDateTo(LocalDate to) {
        if (to == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("serviceDateTo"), to);
    }

    /** Free-text across claim number, patient name and MRN. */
    public static Specification<Claim> matchesText(String q) {
        if (q == null || q.isBlank()) return null;
        String needle = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            Join<Object, Object> patient = root.join("patient", JoinType.LEFT);
            List<Predicate> any = new ArrayList<>();
            any.add(cb.like(cb.lower(root.get("claimNumber")), needle));
            any.add(cb.like(cb.lower(patient.get("lastName")), needle));
            any.add(cb.like(cb.lower(patient.get("firstName")), needle));
            any.add(cb.like(cb.lower(patient.get("mrn")), needle));
            return cb.or(any.toArray(new Predicate[0]));
        };
    }

    /** Combines the non-null specifications with AND. */
    @SafeVarargs
    public static Specification<Claim> allOf(Specification<Claim>... specs) {
        Specification<Claim> result = null;
        for (Specification<Claim> spec : specs) {
            if (spec == null) continue;
            result = (result == null) ? spec : result.and(spec);
        }
        // An unconditional true predicate keeps findAll(spec, pageable) usable
        // even when the caller supplied no filters at all.
        return result != null ? result : (root, query, cb) -> cb.conjunction();
    }
}
