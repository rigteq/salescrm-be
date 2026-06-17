package com.salescms.mapping;

import com.salescms.crm.pipeline.Pipeline;
import com.salescms.crm.pipeline.Stage;
import com.salescms.metadata.CrmRecord;
import com.salescms.metadata.CustomField;
import com.salescms.metadata.IndustryTemplate;
import com.salescms.metadata.MetadataDtos;
import com.salescms.metadata.TenantModule;
import com.salescms.platform.auth.Tenant;
import com.salescms.platform.rbac.Permission;
import com.salescms.platform.rbac.RbacDtos.PermissionDto;
import com.salescms.platform.rbac.RbacDtos.PlatformCompanyDto;
import com.salescms.platform.rbac.RbacDtos.RoleDto;
import com.salescms.quote.Quote;
import com.salescms.quote.QuoteLine;
import com.salescms.quote.QuoteDtos.QuoteLineView;
import com.salescms.quote.QuoteDtos.QuoteView;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface SalesCmsMapper {

    MetadataDtos.ModuleDto toModuleDto(TenantModule module);

    MetadataDtos.CustomFieldDto toCustomFieldDto(CustomField field);

    MetadataDtos.TemplateDto toTemplateDto(IndustryTemplate template);

    MetadataDtos.StageDto toStageDto(Stage stage);

    @Mapping(target = "stages", source = "stages")
    MetadataDtos.PipelineDto toPipelineDto(Pipeline pipeline, java.util.List<Stage> stages);

    @Mapping(target = "customValues", expression = "java(normalizeCustomValues(customValues))")
    MetadataDtos.RecordDto toRecordDto(CrmRecord record, @Context Map<String, Object> customValues);

    QuoteLineView toQuoteLineView(QuoteLine line);

    QuoteView toQuoteView(Quote quote);

    PermissionDto toPermissionDto(Permission permission);

    RoleDto toRoleDto(com.salescms.platform.rbac.Role role, long userCount, long permissionCount);

    PlatformCompanyDto toPlatformCompanyDto(Tenant tenant);

    default Map<String, Object> normalizeCustomValues(Map<String, Object> customValues) {
        return customValues == null ? Map.of() : customValues;
    }
}
