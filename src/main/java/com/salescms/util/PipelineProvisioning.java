package com.salescms.util;
import com.salescms.repository.StageRepository;
import com.salescms.entity.Stage;
import com.salescms.repository.PipelineRepository;
import com.salescms.entity.Pipeline;

import com.salescms.event.TenantProvisionedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/** Creates the default sales pipeline for every new tenant. */
@Component
public class PipelineProvisioning {

    record StageSpec(String name, int probability, boolean won, boolean lost) {
    }

    private static final List<StageSpec> DEFAULT_STAGES = List.of(
            new StageSpec("Qualification", 10, false, false),
            new StageSpec("Discovery", 25, false, false),
            new StageSpec("Proposal Sent", 50, false, false),
            new StageSpec("Negotiation", 75, false, false),
            new StageSpec("Closed Won", 100, true, false),
            new StageSpec("Closed Lost", 0, false, true));

    private final PipelineRepository pipelines;
    private final StageRepository stages;

    public PipelineProvisioning(PipelineRepository pipelines, StageRepository stages) {
        this.pipelines = pipelines;
        this.stages = stages;
    }

    @EventListener
    public void onTenantProvisioned(TenantProvisionedEvent event) {
        if (!pipelines.findByTenantIdAndModuleKeyAndSoftDeletedAtIsNullOrderByCreatedAtAsc(
                event.tenantId(), "opportunities").isEmpty()) {
            return;
        }
        Pipeline pipeline = pipelines.save(new Pipeline("Sales Pipeline", true));
        int position = 0;
        for (StageSpec spec : DEFAULT_STAGES) {
            stages.save(new Stage(event.tenantId(), pipeline.getId(), spec.name(),
                    position++, spec.probability(), spec.won(), spec.lost()));
        }
    }
}
