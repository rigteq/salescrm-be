package com.salescms.mapper;

import com.salescms.entity.Pipeline;
import com.salescms.entity.Stage;
import com.salescms.entity.CrmRecord;
import com.salescms.entity.CustomField;
import com.salescms.entity.IndustryTemplate;
import com.salescms.dto.MetadataDtos;
import com.salescms.entity.TenantModule;
import com.salescms.entity.Tenant;
import com.salescms.entity.Permission;
import com.salescms.dto.RbacDtos.PermissionDto;
import com.salescms.dto.RbacDtos.PlatformCompanyDto;
import com.salescms.dto.RbacDtos.RoleDto;
import com.salescms.entity.Quote;
import com.salescms.entity.QuoteLine;
import com.salescms.dto.QuoteDtos.QuoteLineView;
import com.salescms.dto.QuoteDtos.QuoteView;
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

    RoleDto toRoleDto(com.salescms.entity.Role role, long userCount, long permissionCount);

    PlatformCompanyDto toPlatformCompanyDto(Tenant tenant);

    default Map<String, Object> normalizeCustomValues(Map<String, Object> customValues) {
        return customValues == null ? Map.of() : customValues;
    }
}
