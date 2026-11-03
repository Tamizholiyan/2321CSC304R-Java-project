package com.campuscommute.campuscommute.pricing;

import com.campuscommute.campuscommute.pricing.dto.PricingResponse;
import com.campuscommute.campuscommute.pricing.dto.SettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rides")
@Tag(name = "Pricing & Settlement", description = "Dynamic fuel expense calculation, per-occupant split, and peer settlement")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping("/{id}/pricing")
    @Operation(summary = "Get ride pricing breakdown", description = "Calculates dynamic fuel cost split equally among driver and confirmed passengers upon ride completion")
    public ResponseEntity<PricingResponse> getPricing(@PathVariable("id") Long id) {
        PricingResponse response = pricingService.calculate(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/settlement")
    @Operation(summary = "Get ride settlement details", description = "Returns payment breakdown and peer-to-peer UPI deep-link for direct settlement between riders and driver")
    public ResponseEntity<SettlementResponse> getSettlement(@PathVariable("id") Long id) {
        SettlementResponse response = pricingService.getSettlement(id);
        return ResponseEntity.ok(response);
    }
}

