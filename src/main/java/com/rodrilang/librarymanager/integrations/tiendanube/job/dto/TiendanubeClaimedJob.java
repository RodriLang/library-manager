package com.rodrilang.librarymanager.integrations.tiendanube.job.dto;

import java.util.UUID;

public record TiendanubeClaimedJob(Long jobId, UUID processingToken) {
}
