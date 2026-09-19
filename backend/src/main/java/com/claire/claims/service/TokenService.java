package com.claire.claims.service;

import com.claire.claims.config.AppProperties;
import com.claire.claims.domain.AppUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final AppProperties props;

    public TokenService(JwtEncoder encoder, AppProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public record IssuedToken(String value, OffsetDateTime expiresAt) { }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.getJwt().getTtlMinutes(), ChronoUnit.MINUTES);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.getJwt().getIssuer())
                .issuedAt(now)
                .expiresAt(exp)
                .subject(user.getUsername())
                .claim("roles", List.of(user.getRole().name()))
                .claim("name", user.getFullName())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new IssuedToken(value, exp.atOffset(ZoneOffset.UTC));
    }
}
