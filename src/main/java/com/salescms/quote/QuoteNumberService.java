package com.salescms.quote;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Allocates per-tenant sequential quote numbers (Q-00001, ...) using a row-locked counter. */
@Service
public class QuoteNumberService {

    private final JdbcTemplate jdbc;

    public QuoteNumberService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String nextNumber(UUID tenantId) {
        Long number = jdbc.queryForObject("""
                INSERT INTO quote_counters (tenant_id, next_number)
                VALUES (?, 2)
                ON CONFLICT (tenant_id)
                DO UPDATE SET next_number = quote_counters.next_number + 1
                RETURNING next_number - 1
                """, Long.class, tenantId);
        return String.format("Q-%05d", number);
    }
}
