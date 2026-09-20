package com.claire.claims.web;

import com.claire.claims.config.AppProperties;
import com.claire.claims.dto.ReportDtos.ClaimReport;
import com.claire.claims.dto.ReportDtos.ReportPeriod;
import com.claire.claims.dto.ReportDtos.ReportPeriodType;
import com.claire.claims.dto.ReportDtos.ReportSummary;
import com.claire.claims.security.SecurityConfig;
import com.claire.claims.service.ClaimReportService;
import com.claire.claims.service.export.ClaimReportExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * The reporting endpoint's ACCESS contract, exercised through the REAL
 * {@link SecurityConfig} filter chain (stateless JWT, {@code roles} claim →
 * ROLE_* authorities, {@code anyRequest().authenticated()}):
 *
 *  - reachable by ADMIN, BILLER and VIEWER — every read role — with a valid
 *    bearer token, and
 *  - rejected with 401 when the request carries no token.
 *
 * A tiny web context is built with only the real security beans and a
 * stub {@link ReportController} whose collaborators are mocked, so no database
 * is touched. Tokens are minted with the same {@link JwtEncoder} the app uses,
 * signed with the default {@code app.jwt.secret}, so they verify against the
 * real {@code JwtDecoder}.
 */
class ReportSecurityTest {

    private MockMvc mvc;
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp() {
        AnnotationConfigWebApplicationContext ctx = new AnnotationConfigWebApplicationContext();
        ctx.setServletContext(new MockServletContext());
        ctx.register(TestSecurityBeans.class);
        ctx.refresh();

        mvc = webAppContextSetup(ctx).apply(springSecurity()).build();
        jwtEncoder = ctx.getBean(JwtEncoder.class);
    }

    private String tokenWithRole(String role) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("tester")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .claim("roles", List.of(role))
                .build();
        org.springframework.security.oauth2.jwt.JwsHeader header =
                org.springframework.security.oauth2.jwt.JwsHeader
                        .with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                        .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Test
    void reachableByAdminBillerAndViewerWithAValidToken() throws Exception {
        for (String role : List.of("ADMIN", "BILLER", "VIEWER")) {
            mvc.perform(get("/api/reports/claims")
                            .param("period", "YEAR").param("year", "2026")
                            .header("Authorization", "Bearer " + tokenWithRole(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get("/api/reports/claims")
                        .param("period", "YEAR").param("year", "2026"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The real {@link SecurityConfig} plus a report controller wired to mocked
     * collaborators — no service or repository beans, so nothing reaches a
     * database. {@link AppProperties} supplies the default JWT secret.
     */
    @org.springframework.context.annotation.Configuration
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @org.springframework.context.annotation.Import(SecurityConfig.class)
    static class TestSecurityBeans {

        @org.springframework.context.annotation.Bean
        AppProperties appProperties() {
            return new AppProperties();
        }

        @org.springframework.context.annotation.Bean
        ClaimReportService claimReportService() {
            ClaimReportService svc = mock(ClaimReportService.class);
            ReportPeriod period = new ReportPeriod(ReportPeriodType.YEAR, 2026, null,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "FY2026");
            ReportSummary summary = new ReportSummary(0, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, List.of(), List.of());
            when(svc.build(any(), any(), any()))
                    .thenReturn(new ClaimReport(period, summary, List.of()));
            return svc;
        }

        @org.springframework.context.annotation.Bean
        ClaimReportExporter claimReportExporter() {
            return mock(ClaimReportExporter.class);
        }

        @org.springframework.context.annotation.Bean
        ReportController reportController(ClaimReportService reports, ClaimReportExporter exporter) {
            return new ReportController(reports, exporter);
        }
    }
}
