package com.salescms.entity;

import com.salescms.entity.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "pipelines")
public class Pipeline extends TenantOwnedEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "module_key", nullable = false)
    private String moduleKey = "opportunities";

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    protected Pipeline() {
    }

    public Pipeline(String name, boolean isDefault) {
        this.name = name;
        this.isDefault = isDefault;
    }

    public Pipeline(String moduleKey, String name, boolean isDefault) {
        this.moduleKey = moduleKey;
        this.name = name;
        this.isDefault = isDefault;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        this.moduleKey = moduleKey;
    }
}
