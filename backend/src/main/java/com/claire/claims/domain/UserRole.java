package com.claire.claims.domain;

/**
 * RBAC roles. Mapped to Spring Security authorities as ROLE_&lt;name&gt;.
 *
 * ADMIN  - everything, including deletes and reference-data changes.
 * BILLER - create and edit draft claims, drive status transitions.
 * VIEWER - read only. The API rejects every mutation, not just the UI.
 */
public enum UserRole {
    ADMIN,
    BILLER,
    VIEWER
}
