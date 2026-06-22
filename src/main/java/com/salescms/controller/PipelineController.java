package com.salescms.controller;
import com.salescms.repository.StageRepository;
import com.salescms.entity.Stage;
import com.salescms.repository.PipelineRepository;
import com.salescms.entity.Pipeline;

import com.salescms.service.DynamicRecordService;
import com.salescms.mapper.SalesCmsMapper;
import com.salescms.exception.BadRequestException;
import com.salescms.exception.NotFoundException;
import com.salescms.entity.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.salescms.dto.MetadataDtos.PipelineDto;
import static com.salescms.dto.MetadataDtos.PipelineRequest;
import static com.salescms.dto.MetadataDtos.StageDto;
import static com.salescms.dto.MetadataDtos.StageRequest;

@RestController
@RequestMapping("/api/pipelines")
public class PipelineController {

    private final PipelineRepository pipelines;
    private final StageRepository stages;
    private final SalesCmsMapper mapper;

    public PipelineController(PipelineRepository pipelines, StageRepository stages, SalesCmsMapper mapper) {
        this.pipelines = pipelines;
        this.stages = stages;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasAnyPermission('LEAD_VIEW_ALL','LEAD_VIEW_TEAM','LEAD_VIEW_ASSIGNED')")
    public List<PipelineDto> list(@RequestParam(required = false) String moduleKey) {
        UUID tenantId = TenantContext.requireTenantId();
        List<Pipeline> items = moduleKey == null || moduleKey.isBlank()
                ? pipelines.findByTenantIdAndSoftDeletedAtIsNullOrderByCreatedAtAsc(tenantId)
                : pipelines.findByTenantIdAndModuleKeyAndSoftDeletedAtIsNullOrderByCreatedAtAsc(
                        tenantId, DynamicRecordService.normalizeKey(moduleKey));
        return items.stream()
                .map(this::view)
                .toList();
    }

    @PostMapping
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction(#request.moduleKey(),'configure')")
    public PipelineDto create(@RequestBody PipelineRequest request) {
        String moduleKey = DynamicRecordService.normalizeKey(request.moduleKey());
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("name is required");
        }
        Pipeline pipeline = pipelines.save(new Pipeline(moduleKey, request.name().trim(), request.isDefault()));
        saveStages(pipeline, request.stages());
        return view(pipeline);
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public PipelineDto update(@PathVariable UUID id, @RequestBody PipelineRequest request) {
        Pipeline pipeline = pipelines.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("Pipeline", id));
        if (request.moduleKey() != null && !pipeline.getModuleKey().equals(DynamicRecordService.normalizeKey(request.moduleKey()))) {
            throw new BadRequestException("Pipeline module cannot be changed");
        }
        if (request.name() != null && !request.name().isBlank()) {
            pipeline.setName(request.name().trim());
        }
        pipeline.setDefault(request.isDefault());
        saveStages(pipeline, request.stages());
        return view(pipelines.save(pipeline));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("@permissionService.hasModuleAction('settings','configure')")
    public void delete(@PathVariable UUID id) {
        Pipeline pipeline = pipelines.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new NotFoundException("Pipeline", id));
        pipeline.softDelete();
        pipelines.save(pipeline);
    }

    private PipelineDto view(Pipeline pipeline) {
        UUID tenantId = TenantContext.requireTenantId();
        return mapper.toPipelineDto(pipeline,
                stages.findByTenantIdAndPipelineIdOrderByPositionAsc(tenantId, pipeline.getId()));
    }

    private void saveStages(Pipeline pipeline, List<StageRequest> requests) {
        if (requests == null) {
            return;
        }
        UUID tenantId = TenantContext.requireTenantId();
        for (int i = 0; i < requests.size(); i++) {
            StageRequest request = requests.get(i);
            if (request.name() == null || request.name().isBlank()) {
                throw new BadRequestException("stage name is required");
            }
            Stage stage = request.id() == null
                    ? new Stage(tenantId, pipeline.getId(), request.name().trim(), i, request.probability(), false, false)
                    : stages.findByIdAndTenantId(request.id(), tenantId)
                            .orElseThrow(() -> new NotFoundException("Stage", request.id()));
            if (!stage.getPipelineId().equals(pipeline.getId())) {
                throw new BadRequestException("Stage does not belong to pipeline");
            }
            stage.setName(request.name().trim());
            stage.setProbability(request.probability());
            stage.setColor(request.color() == null || request.color().isBlank() ? "#4f46e5" : request.color());
            stage.setSequence(request.sequence() >= 0 ? request.sequence() : i);
            stage.setStageStatus(request.stageStatus() == null || request.stageStatus().isBlank()
                    ? "OPEN" : request.stageStatus().trim().toUpperCase());
            stage.setOutcomeType(request.outcomeType());
            stages.save(stage);
        }
    }
}
