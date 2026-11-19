package com.smartteach.support;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint guarded by a role, so role checks can be tested before real admin endpoints exist. */
@RestController
class RoleProbeController {

    @GetMapping("/api/test/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    String adminOnly() {
        return "ok";
    }
}
