package com.rodrilang.librarymanager.integrations.tiendanube.job.handler;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;

public interface TiendanubeJobHandler {

    TiendanubeJobType type();

    void execute(TiendanubeJobExecutionContext context);
}
