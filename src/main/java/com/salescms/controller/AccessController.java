package com.salescms.controller;
import com.salescms.dto.RbacDtos;
import com.salescms.service.PermissionService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.salescms.dto.RbacDtos.CurrentUserAccessDto;

@RestController
@RequestMapping("/api/me")
public class AccessController {

    private final PermissionService permissionService;

    public AccessController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/access")
    public CurrentUserAccessDto access() {
        return permissionService.currentAccess();
    }
}
