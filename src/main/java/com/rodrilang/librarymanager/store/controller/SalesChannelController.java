package com.rodrilang.librarymanager.store.controller;

import com.rodrilang.librarymanager.store.channel.SalesChannelType;
import com.rodrilang.librarymanager.store.dto.SalesChannelSettingsResponse;
import com.rodrilang.librarymanager.store.dto.UpdateSalesChannelRequest;
import com.rodrilang.librarymanager.store.service.SalesChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales-channels")
@RequiredArgsConstructor
public class SalesChannelController {
    private final SalesChannelService service;

    @GetMapping
    public SalesChannelSettingsResponse current() { return service.current(); }

    @PutMapping("/{channel}")
    public SalesChannelSettingsResponse update(@PathVariable SalesChannelType channel, @Valid @RequestBody UpdateSalesChannelRequest request) {
        return service.setEnabled(channel, request.enabled());
    }
}
