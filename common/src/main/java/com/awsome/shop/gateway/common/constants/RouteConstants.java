package com.awsome.shop.gateway.common.constants;

/**
 * Gateway route constants
 */
public final class RouteConstants {

    private RouteConstants() {
    }

    // ==================== Header Names ====================

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** Authenticated user id injected to downstream (design FR-G2). */
    public static final String HEADER_USER_ID = "X-User-Id";
    /** Authenticated user role injected to downstream (design FR-G2/FR-G3). */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    // ==================== Route Metadata Keys ====================

    public static final String METADATA_AUTH_REQUIRED = "auth-required";

    // ==================== Path Prefixes ====================

    public static final String PATH_PREFIX_PUBLIC = "/api/v1/public/";
    public static final String PATH_PREFIX_DOCS = "/v3/api-docs/";

    // ==================== Gateway Attributes ====================

    public static final String ATTR_REQUEST_ID = "requestId";
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USER_ROLE = "userRole";
    public static final String ATTR_REQUEST_START_TIME = "requestStartTime";

    // ==================== Roles ====================

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";
}
