package com.rodrilang.librarymanager.shipping.returns.controller;
import com.rodrilang.librarymanager.shipping.returns.dto.*;
import com.rodrilang.librarymanager.shipping.returns.service.StoreReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/store/orders/{orderId}/returns")
@RequiredArgsConstructor
public class StoreReturnAdminController {
    private final StoreReturnService service;
    @GetMapping public List<StoreReturnResponse> list(@PathVariable Long orderId) { return service.list(orderId); }
    @PostMapping public StoreReturnResponse create(@PathVariable Long orderId, @RequestBody CreateStoreReturnRequest request) { return service.create(orderId, request); }
    @PatchMapping("/{returnId}/shipping") public StoreReturnResponse shipping(@PathVariable Long orderId,@PathVariable Long returnId,@RequestBody UpdateStoreReturnShippingRequest request){return service.updateShipping(orderId,returnId,request);}
    @PostMapping("/{returnId}/approve") public StoreReturnResponse approve(@PathVariable Long orderId,@PathVariable Long returnId){return service.approve(orderId,returnId);}
    @PostMapping("/{returnId}/in-transit") public StoreReturnResponse transit(@PathVariable Long orderId,@PathVariable Long returnId){return service.markInTransit(orderId,returnId);}
    @PostMapping("/{returnId}/receive") public StoreReturnResponse receive(@PathVariable Long orderId,@PathVariable Long returnId){return service.receive(orderId,returnId);}
    @PostMapping("/{returnId}/complete") public StoreReturnResponse complete(@PathVariable Long orderId,@PathVariable Long returnId){return service.complete(orderId,returnId);}
    @PostMapping("/{returnId}/reject") public StoreReturnResponse reject(@PathVariable Long orderId,@PathVariable Long returnId){return service.reject(orderId,returnId);}
    @PostMapping("/{returnId}/cancel") public StoreReturnResponse cancel(@PathVariable Long orderId,@PathVariable Long returnId){return service.cancel(orderId,returnId);}
}
