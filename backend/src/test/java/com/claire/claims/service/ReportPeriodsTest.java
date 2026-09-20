package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.dto.ReportDtos.ReportPeriod;
import com.claire.claims.dto.ReportDtos.ReportPeriodType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The period boundary maths and input validation, exercised without a database. */
class ReportPeriodsTest {

    @Test
    void quarterOneSpansJanToMarch() {
        ReportPeriod p = ReportPeriods.resolve(ReportPeriodType.QUARTER, 2025, 1);

        assertThat(p.type()).isEqualTo(ReportPeriodType.QUARTER);
        assertThat(p.year()).isEqualTo(2025);
        assertThat(p.quarter()).isEqualTo(1);
        assertThat(p.from()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(p.to()).isEqualTo(LocalDate.of(2025, 3, 31));
        assertThat(p.label()).isEqualTo("Q1 2025");
    }

    @Test
    void quarterFourEndsOnDecemberThirtyFirst() {
        ReportPeriod p = ReportPeriods.resolve(ReportPeriodType.QUARTER, 2025, 4);

        assertThat(p.from()).isEqualTo(LocalDate.of(2025, 10, 1));
        assertThat(p.to()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void quarterTwoAndThreeHaveCorrectBoundaries() {
        assertThat(ReportPeriods.resolve(ReportPeriodType.QUARTER, 2025, 2).from())
                .isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(ReportPeriods.resolve(ReportPeriodType.QUARTER, 2025, 2).to())
                .isEqualTo(LocalDate.of(2025, 6, 30));
        assertThat(ReportPeriods.resolve(ReportPeriodType.QUARTER, 2025, 3).from())
                .isEqualTo(LocalDate.of(2025, 7, 1));
        assertThat(ReportPeriods.resolve(ReportPeriodType.QUARTER, 2025, 3).to())
                .isEqualTo(LocalDate.of(2025, 9, 30));
    }

    @Test
    void yearSpansTheWholeCalendarYear() {
        ReportPeriod p = ReportPeriods.resolve(ReportPeriodType.YEAR, 2024, null);

        assertThat(p.type()).isEqualTo(ReportPeriodType.YEAR);
        assertThat(p.quarter()).isNull();
        assertThat(p.from()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(p.to()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(p.label()).isEqualTo("FY2024");
    }

    @Test
    void nullPeriodTypeIsRejected() {
        assertThatThrownBy(() -> ReportPeriods.resolve(null, 2025, 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("period is required");
    }

    @Test
    void missingYearIsRejected() {
        assertThatThrownBy(() -> ReportPeriods.resolve(ReportPeriodType.YEAR, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("year is required");
    }

    @Test
    void yearBelowMinimumIsRejected() {
        assertThatThrownBy(() -> ReportPeriods.resolve(ReportPeriodType.YEAR, 1999, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("year must be between");
    }

    @Test
    void yearTooFarInTheFutureIsRejected() {
        int tooFar = Year.now().getValue() + 2;
        assertThatThrownBy(() -> ReportPeriods.resolve(ReportPeriodType.YEAR, tooFar, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("year must be between");
    }

    @Test
    void quarterMissingForQuarterReportIsRejected() {
        assertThatThrownBy(() -> ReportPeriods.resolve(ReportPeriodType.QUARTER, 2025, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("quarter (1-4) is required");
    }

    @Test
    void quarterOutOfRangeIsRejected() {
        assertThatThrownBy(() -> ReportPeriods.resolve(ReportPeriodType.QUARTER, 2025, 0))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("quarter must be between 1 and 4");
        assertThatThrownBy(() -> ReportPeriods.resolve(ReportPeriodType.QUARTER, 2025, 5))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("quarter must be between 1 and 4");
    }

    @Test
    void quarterSuppliedForYearReportIsRejected() {
        assertThatThrownBy(() -> ReportPeriods.resolve(ReportPeriodType.YEAR, 2025, 2))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("quarter must not be supplied for a YEAR report");
    }
}
