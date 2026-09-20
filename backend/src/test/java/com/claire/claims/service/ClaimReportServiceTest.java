package com.claire.claims.service;

import com.claire.claims.domain.Claim;
import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.domain.Patient;
import com.claire.claims.domain.Payer;
import com.claire.claims.domain.PlanType;
import com.claire.claims.domain.Provider;
import com.claire.claims.dto.ClaimDtos.ClaimListItem;
import com.claire.claims.dto.ClaimDtos.StatusBucket;
import com.claire.claims.dto.ReportDtos.ClaimReport;
import com.claire.claims.dto.ReportDtos.ReportPeriodType;
import com.claire.claims.dto.ReportDtos.TypeBucket;
import com.claire.claims.repository.ClaimRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Report aggregation + detail, exercised against a real JPA layer (H2 in
 * PostgreSQL mode). This is where the acceptance criteria that need data live:
 *
 *  - the summary rollup must equal the detail rows it sits on top of, and
 *  - a request must never return a claim outside its period.
 *
 * The repo is single-tenant RBAC with no tenant/owner column, so "cannot cross
 * a tenant boundary" is enforced here as: the reporting PERIOD is the only row
 * filter, and it is applied identically to the summary and the detail — a claim
 * outside the window appears in neither, and no user-scoped or tenant-scoped
 * widening exists that could leak one in.
 *
 * Spring Boot 4.1 does not ship the {@code @DataJpaTest} slice in this project's
 * declared dependencies, so the JPA layer is bootstrapped directly with the
 * Hibernate/Jakarta persistence API and the REAL {@link ClaimRepository} is
 * built via Spring Data's {@link JpaRepositoryFactory}. This runs the actual
 * {@code @Query} aggregation SQL and the real {@link ClaimReportService} — no
 * mocks over the layer under test.
 */
class ClaimReportServiceTest {

    private static EntityManagerFactory emf;

    private EntityManager em;
    private ClaimReportService reports;

    private Patient patient;
    private Provider provider;
    private Payer payer;

    @BeforeAll
    static void bootJpa() {
        emf = Persistence.createEntityManagerFactory("reportslice", Map.of(
                "jakarta.persistence.jdbc.url",
                "jdbc:h2:mem:reportslice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "jakarta.persistence.jdbc.user", "sa",
                "jakarta.persistence.jdbc.password", "",
                "jakarta.persistence.jdbc.driver", "org.h2.Driver",
                "hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.dialect", "org.hibernate.dialect.H2Dialect"));
    }

    @AfterAll
    static void closeJpa() {
        if (emf != null) emf.close();
    }

    @BeforeEach
    void setUp() {
        em = emf.createEntityManager();

        ClaimRepository claims = new JpaRepositoryFactory(em).getRepository(ClaimRepository.class);
        reports = new ClaimReportService(claims, new ClaimMapper());

        // Fresh data per test — wipe then seed shared reference rows.
        inTx(() -> {
            em.createQuery("DELETE FROM Claim").executeUpdate();
            em.createQuery("DELETE FROM Patient").executeUpdate();
            em.createQuery("DELETE FROM Provider").executeUpdate();
            em.createQuery("DELETE FROM Payer").executeUpdate();
        });
        inTx(() -> {
            patient = persistPatient();
            provider = persistProvider();
            payer = persistPayer();
        });
    }

    // ------------------------------------------------------------------
    // Summary math == detail rows
    // ------------------------------------------------------------------

    @Test
    void quarterSummaryTotalsEqualTheDetailRows() {
        inTx(() -> {
            persistClaim("CLM-2025-000001", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 12), "100.00", "80.00", "80.00");
            persistClaim("CLM-2025-000002", ClaimStatus.SUBMITTED, "21",
                    LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 3), "250.50", "300.00", "0.00");
            persistClaim("CLM-2025-000003", ClaimStatus.PARTIALLY_PAID, "11",
                    LocalDate.of(2025, 3, 30), LocalDate.of(2025, 3, 31), "400.00", "500.00", "120.00");
        });

        ClaimReport report = reports.build(ReportPeriodType.QUARTER, 2025, 1);

        assertThat(report.detail()).extracting(ClaimListItem::claimNumber)
                .containsExactly("CLM-2025-000001", "CLM-2025-000002", "CLM-2025-000003");

        BigDecimal detailCharge = sum(report.detail(), ClaimListItem::totalCharge);
        BigDecimal detailPaid = sum(report.detail(), ClaimListItem::paidAmount);

        assertThat(report.summary().totalClaims()).isEqualTo(report.detail().size());
        assertThat(report.summary().totalCharged()).isEqualByComparingTo(detailCharge);
        assertThat(report.summary().totalPaid()).isEqualByComparingTo(detailPaid);
        assertThat(report.summary().totalCharged()).isEqualByComparingTo("750.50");
        assertThat(report.summary().totalPaid()).isEqualByComparingTo("200.00");
    }

    @Test
    void byStatusBucketsSumBackToTheDetailRows() {
        inTx(() -> {
            persistClaim("CLM-2025-000001", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 12), "100.00", "100.00", "100.00");
            persistClaim("CLM-2025-000002", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 1, 20), LocalDate.of(2025, 1, 22), "200.00", "200.00", "150.00");
            persistClaim("CLM-2025-000003", ClaimStatus.DENIED, "22",
                    LocalDate.of(2025, 2, 5), LocalDate.of(2025, 2, 6), "300.00", "0.00", "0.00");
        });

        ClaimReport report = reports.build(ReportPeriodType.QUARTER, 2025, 1);

        long paidCount = report.summary().byStatus().stream()
                .filter(b -> b.status() == ClaimStatus.PAID).mapToLong(StatusBucket::count).sum();
        long deniedCount = report.summary().byStatus().stream()
                .filter(b -> b.status() == ClaimStatus.DENIED).mapToLong(StatusBucket::count).sum();
        assertThat(paidCount).isEqualTo(2);
        assertThat(deniedCount).isEqualTo(1);

        BigDecimal bucketCharge = report.summary().byStatus().stream()
                .map(StatusBucket::totalCharge).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(bucketCharge).isEqualByComparingTo("600.00");
        assertThat(bucketCharge).isEqualByComparingTo(report.summary().totalCharged());

        long statusCountSum = report.summary().byStatus().stream()
                .mapToLong(StatusBucket::count).sum();
        assertThat(statusCountSum).isEqualTo(report.summary().totalClaims());
        assertThat(statusCountSum).isEqualTo(report.detail().size());
    }

    @Test
    void byTypeBucketsSumBackToTheDetailRows() {
        inTx(() -> {
            persistClaim("CLM-2025-000001", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 12), "100.00", "100.00", "50.00");
            persistClaim("CLM-2025-000002", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 1, 20), LocalDate.of(2025, 1, 22), "100.00", "100.00", "50.00");
            persistClaim("CLM-2025-000003", ClaimStatus.PAID, "21",
                    LocalDate.of(2025, 2, 5), LocalDate.of(2025, 2, 6), "300.00", "300.00", "0.00");
        });

        ClaimReport report = reports.build(ReportPeriodType.QUARTER, 2025, 1);

        long typeCountSum = report.summary().byType().stream()
                .mapToLong(TypeBucket::count).sum();
        BigDecimal typeChargeSum = report.summary().byType().stream()
                .map(TypeBucket::totalCharge).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(typeCountSum).isEqualTo(report.detail().size());
        assertThat(typeChargeSum).isEqualByComparingTo(report.summary().totalCharged());

        assertThat(report.summary().byType())
                .filteredOn(b -> b.code().equals("11"))
                .singleElement()
                .satisfies(b -> {
                    assertThat(b.count()).isEqualTo(2);
                    assertThat(b.totalCharge()).isEqualByComparingTo("200.00");
                });
    }

    @Test
    void outstandingReceivableExcludesClosedClaims() {
        inTx(() -> {
            persistClaim("CLM-2025-000001", ClaimStatus.PARTIALLY_PAID, "11",
                    LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 12), "300.00", "0.00", "100.00");
            persistClaim("CLM-2025-000002", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 1, 20), LocalDate.of(2025, 1, 22), "500.00", "0.00", "500.00");
            persistClaim("CLM-2025-000003", ClaimStatus.VOID, "11",
                    LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 2), "999.00", "0.00", "0.00");
        });

        ClaimReport report = reports.build(ReportPeriodType.QUARTER, 2025, 1);

        // (totalCharge - paidAmount) over open claims only = 300 - 100 = 200.
        assertThat(report.summary().outstandingReceivable()).isEqualByComparingTo("200.00");
    }

    // ------------------------------------------------------------------
    // Period scoping — the boundary a request cannot cross
    // ------------------------------------------------------------------

    @Test
    void claimsOutsideThePeriodAppearInNeitherSummaryNorDetail() {
        inTx(() -> {
            persistClaim("CLM-2025-000001", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 1, 15), LocalDate.of(2025, 1, 16), "100.00", "100.00", "100.00");
            persistClaim("CLM-2024-000001", ClaimStatus.PAID, "11",
                    LocalDate.of(2024, 12, 20), LocalDate.of(2024, 12, 21), "999.00", "999.00", "999.00");
            persistClaim("CLM-2025-000099", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 2), "888.00", "888.00", "888.00");
        });

        ClaimReport report = reports.build(ReportPeriodType.QUARTER, 2025, 1);

        assertThat(report.detail()).extracting(ClaimListItem::claimNumber)
                .containsExactly("CLM-2025-000001");
        assertThat(report.summary().totalClaims()).isEqualTo(1);
        assertThat(report.summary().totalCharged()).isEqualByComparingTo("100.00");
    }

    @Test
    void claimSpanningTheQuarterBoundaryIsExcluded() {
        // serviceDateTo (Apr 2) falls after Q1's last day, so the to <= end rule
        // excludes it — matching exactly how the claims list scopes rows.
        inTx(() -> persistClaim("CLM-2025-000001", ClaimStatus.PAID, "11",
                LocalDate.of(2025, 3, 30), LocalDate.of(2025, 4, 2), "100.00", "100.00", "100.00"));

        ClaimReport report = reports.build(ReportPeriodType.QUARTER, 2025, 1);

        assertThat(report.detail()).isEmpty();
        assertThat(report.summary().totalClaims()).isZero();
        assertThat(report.summary().totalCharged()).isEqualByComparingTo("0.00");
        assertThat(report.summary().outstandingReceivable()).isEqualByComparingTo("0.00");
    }

    @Test
    void annualReportRollsUpEveryQuarterAndNothingOutsideTheYear() {
        inTx(() -> {
            persistClaim("CLM-2025-000001", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 2), "100.00", "0.00", "0.00");
            persistClaim("CLM-2025-000002", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 2), "200.00", "0.00", "0.00");
            persistClaim("CLM-2025-000003", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 2), "300.00", "0.00", "0.00");
            persistClaim("CLM-2025-000004", ClaimStatus.PAID, "11",
                    LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 2), "400.00", "0.00", "0.00");
            persistClaim("CLM-2024-000001", ClaimStatus.PAID, "11",
                    LocalDate.of(2024, 12, 31), LocalDate.of(2024, 12, 31), "9.00", "0.00", "0.00");
            persistClaim("CLM-2026-000001", ClaimStatus.PAID, "11",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), "9.00", "0.00", "0.00");
        });

        ClaimReport annual = reports.build(ReportPeriodType.YEAR, 2025, null);

        assertThat(annual.detail()).hasSize(4);
        assertThat(annual.summary().totalClaims()).isEqualTo(4);
        assertThat(annual.summary().totalCharged()).isEqualByComparingTo("1000.00");

        // The annual total must equal the sum of the four quarterly reports.
        BigDecimal quarterlySum = BigDecimal.ZERO;
        long quarterlyCount = 0;
        for (int q = 1; q <= 4; q++) {
            ClaimReport r = reports.build(ReportPeriodType.QUARTER, 2025, q);
            quarterlySum = quarterlySum.add(r.summary().totalCharged());
            quarterlyCount += r.summary().totalClaims();
        }
        assertThat(quarterlySum).isEqualByComparingTo(annual.summary().totalCharged());
        assertThat(quarterlyCount).isEqualTo(annual.summary().totalClaims());
    }

    // ------------------------------------------------------------------
    // helpers / fixtures
    // ------------------------------------------------------------------

    /**
     * Run a unit of work in the EntityManager's own resource-local transaction.
     * The persistence unit is RESOURCE_LOCAL (no Spring tx manager), and the
     * Spring Data repository reads happen on the same EM inside the same active
     * transaction, so writes are visible to the aggregation queries.
     */
    private void inTx(Runnable body) {
        em.getTransaction().begin();
        try {
            body.run();
            em.flush();
            em.getTransaction().commit();
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.clear();
        }
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Patient persistPatient() {
        Patient p = new Patient();
        p.setMrn("MRN-1");
        p.setFirstName("Ada");
        p.setLastName("Lovelace");
        p.setDateOfBirth(LocalDate.of(1990, 1, 1));
        em.persist(p);
        return p;
    }

    private Provider persistProvider() {
        Provider pr = new Provider();
        pr.setNpi("1234567890");
        pr.setFirstName("Grace");
        pr.setLastName("Hopper");
        pr.setActive(true);
        em.persist(pr);
        return pr;
    }

    private Payer persistPayer() {
        Payer pay = new Payer();
        pay.setPayerCode("PAY1");
        pay.setName("Acme Health");
        pay.setPlanType(PlanType.COMMERCIAL);
        pay.setActive(true);
        em.persist(pay);
        return pay;
    }

    private void persistClaim(String number, ClaimStatus status, String pos,
                              LocalDate from, LocalDate to,
                              String charge, String allowed, String paid) {
        Claim c = new Claim();
        c.setClaimNumber(number);
        c.setPatient(patient);
        c.setProvider(provider);
        c.setPayer(payer);
        c.setStatus(status);
        c.setPlaceOfService(pos);
        c.setServiceDateFrom(from);
        c.setServiceDateTo(to);
        c.setTotalCharge(new BigDecimal(charge));
        c.setAllowedAmount(new BigDecimal(allowed));
        c.setPaidAmount(new BigDecimal(paid));
        c.setCreatedBy("test");
        em.persist(c);
    }
}
