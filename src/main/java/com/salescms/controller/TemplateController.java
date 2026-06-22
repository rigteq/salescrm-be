package com.salescms.controller;
import com.salescms.service.MetadataTemplateService;
import com.salescms.dto.MetadataDtos;
import com.salescms.repository.IndustryTemplateRepository;
import com.salescms.entity.IndustryTemplate;

import com.salescms.exception.BadRequestException;
import com.salescms.exception.NotFoundException;
import com.salescms.mapper.SalesCmsMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static com.salescms.dto.MetadataDtos.ClonePreviewDto;
import static com.salescms.dto.MetadataDtos.CustomFieldDto;
import static com.salescms.dto.MetadataDtos.ModuleDto;
import static com.salescms.dto.MetadataDtos.PipelineDto;
import static com.salescms.dto.MetadataDtos.StageDto;
import static com.salescms.dto.MetadataDtos.TemplateDetailDto;
import static com.salescms.dto.MetadataDtos.TemplateDto;
import static com.salescms.dto.MetadataDtos.TemplateRequest;

@RestController
@RequestMapping("/api/platform/templates")
public class TemplateController {

    private final IndustryTemplateRepository templates;
    private final MetadataTemplateService templateService;
    private final JdbcTemplate jdbc;
    private final SalesCmsMapper mapper;

    public TemplateController(IndustryTemplateRepository templates, MetadataTemplateService templateService,
                              JdbcTemplate jdbc, SalesCmsMapper mapper) {
        this.templates = templates;
        this.templateService = templateService;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_PERMISSION_MANAGE')")
    public List<TemplateDto> list() {
        return templates.findAll().stream()
                .map(mapper::toTemplateDto)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_PERMISSION_MANAGE')")
    public TemplateDetailDto get(@PathVariable UUID id) {
        IndustryTemplate template = templates.findById(id)
                .orElseThrow(() -> new NotFoundException("IndustryTemplate", id));
        return detail(template);
    }

    @PostMapping
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_PERMISSION_MANAGE')")
    public TemplateDto create(@RequestBody TemplateRequest request) {
        if (templates.findByTemplateKey(required(request.templateKey(), "templateKey")).isPresent()) {
            throw new BadRequestException("Template key already exists");
        }
        IndustryTemplate template = new IndustryTemplate(
                request.templateKey().trim(),
                required(request.name(), "name"),
                request.description(),
                required(request.businessType(), "businessType"));
        template.setActive(request.active());
        return mapper.toTemplateDto(templates.save(template));
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_PERMISSION_MANAGE')")
    public TemplateDto update(@PathVariable UUID id, @RequestBody TemplateRequest request) {
        IndustryTemplate template = templates.findById(id)
                .orElseThrow(() -> new NotFoundException("IndustryTemplate", id));
        template.setName(required(request.name(), "name"));
        template.setDescription(request.description());
        template.setBusinessType(required(request.businessType(), "businessType"));
        template.setActive(request.active());
        return mapper.toTemplateDto(templates.save(template));
    }

    @PostMapping("/{id}/clone-preview")
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_PERMISSION_MANAGE')")
    public ClonePreviewDto clonePreview(@PathVariable UUID id) {
        IndustryTemplate template = templates.findById(id)
                .orElseThrow(() -> new NotFoundException("IndustryTemplate", id));
        return new ClonePreviewDto(id, template.getTemplateKey(), template.getName(),
                count("industry_template_modules", "template_id", id),
                count("industry_template_pipelines", "template_id", id),
                countStages(id),
                count("industry_template_custom_fields", "template_id", id),
                count("industry_template_forms", "template_id", id),
                count("industry_template_workflows", "template_id", id),
                count("industry_template_dashboards", "template_id", id));
    }

    @PostMapping("/{id}/clone-to-current-tenant")
    @Transactional
    @PreAuthorize("@permissionService.hasPermission('PLATFORM_PERMISSION_MANAGE')")
    public TemplateDto cloneToCurrentTenant(@PathVariable UUID id) {
        templateService.cloneTemplateForTenant(id, com.salescms.entity.TenantContext.requireTenantId(), false);
        return mapper.toTemplateDto(templates.findById(id).orElseThrow());
    }

    private TemplateDetailDto detail(IndustryTemplate template) {
        UUID id = template.getId();
        List<ModuleDto> modules = jdbc.query("""
                select id, module_key, singular_label, plural_label, icon, display_order, enabled
                from industry_template_modules where template_id=? order by display_order
                """, this::templateModule, id);
        List<PipelineDto> pipelines = jdbc.query("""
                select id, module_key, name, is_default
                from industry_template_pipelines where template_id=? order by module_key, name
                """, (rs, rowNum) -> templatePipeline(rs), id);
        List<CustomFieldDto> fields = jdbc.query("""
                select id, module_key, field_key, label, field_type, options_json,
                       validation_json, visibility_rules_json, required, display_order
                from industry_template_custom_fields where template_id=? order by module_key, display_order
                """, this::templateField, id);
        return new TemplateDetailDto(mapper.toTemplateDto(template), modules, pipelines, fields);
    }

    private ModuleDto templateModule(ResultSet rs, int rowNum) throws SQLException {
        return new ModuleDto(rs.getObject("id", UUID.class), rs.getString("module_key"), rs.getBoolean("enabled"),
                rs.getString("singular_label"), rs.getString("plural_label"), rs.getString("icon"),
                rs.getInt("display_order"));
    }

    private PipelineDto templatePipeline(ResultSet rs) throws SQLException {
        UUID pipelineId = rs.getObject("id", UUID.class);
        List<StageDto> stages = jdbc.query("""
                select id, name, probability, color, sequence, stage_status, outcome_type
                from industry_template_stages where template_pipeline_id=? order by sequence
                """, (stage, rowNum) -> new StageDto(stage.getObject("id", UUID.class), stage.getString("name"),
                stage.getInt("sequence"), stage.getInt("probability"),
                "WON".equals(stage.getString("outcome_type")), "LOST".equals(stage.getString("outcome_type")),
                stage.getString("color"), stage.getInt("sequence"), stage.getString("stage_status"),
                stage.getString("outcome_type")), pipelineId);
        return new PipelineDto(pipelineId, rs.getString("module_key"), rs.getString("name"),
                rs.getBoolean("is_default"), stages);
    }

    private CustomFieldDto templateField(ResultSet rs, int rowNum) throws SQLException {
        return new CustomFieldDto(rs.getObject("id", UUID.class), rs.getString("module_key"),
                rs.getString("field_key"), rs.getString("label"), rs.getString("field_type"),
                rs.getString("options_json"), rs.getString("validation_json"),
                rs.getString("visibility_rules_json"), rs.getBoolean("required"),
                rs.getInt("display_order"), true);
    }

    private int count(String table, String column, UUID value) {
        Integer count = jdbc.queryForObject("select count(*) from " + table + " where " + column + "=?",
                Integer.class, value);
        return count == null ? 0 : count;
    }

    private int countStages(UUID templateId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from industry_template_stages s
                join industry_template_pipelines p on p.id=s.template_pipeline_id
                where p.template_id=?
                """, Integer.class, templateId);
        return count == null ? 0 : count;
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(label + " is required");
        }
        return value.trim();
    }
}
