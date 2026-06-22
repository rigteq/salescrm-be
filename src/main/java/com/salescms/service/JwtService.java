package com.salescms.service;

import com.salescms.entity.User;
import com.salescms.service.PermissionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final PermissionService permissionService;
    private final String issuer;
    private final long accessTokenMinutes;

    public JwtService(JwtEncoder encoder,
                      PermissionService permissionService,
                      @Value("${salescms.jwt.issuer}") String issuer,
                      @Value("${salescms.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.encoder = encoder;
        this.permissionService = permissionService;
        this.issuer = issuer;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String issueToken(User user) {
        Instant now = Instant.now();
        var access = permissionService.accessFor(user);
        var primary = access.primaryRole();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenMinutes, ChronoUnit.MINUTES))
                .subject(user.getId().toString())
                .claim("userId", user.getId().toString())
                .claim("companyId", user.getTenantId().toString())
                .claim("tenant_id", user.getTenantId().toString())
                .claim("role", user.getRole())
                .claim("primaryRoleCode", primary != null ? primary.roleCode() : user.getRole())
                .claim("primaryRoleName", primary != null ? primary.roleName() : user.getRole())
                .claim("hierarchyLevel", access.hierarchyLevel())
                .claim("permissions", access.permissions().stream().toList())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
