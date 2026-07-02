package com.ainote.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CostTrackingServiceTest {

    private CostTrackingService service;

    @BeforeEach
    void setUp() {
        service = new CostTrackingService();
        service.setPrices(Map.of(
                "deepseek-chat", price(0.001, 0.002),
                "gpt-4", price(0.03, 0.06)
        ));
    }

    @Test
    void calculateCost_exactModelName_usesConfiguredInputAndOutputPrices() {
        double cost = service.calculateCost("deepseek-chat", 2000, 3000);

        assertThat(cost).isEqualTo(0.008);
    }

    @Test
    void calculateCost_containsModelName_usesMatchingPrice() {
        double cost = service.calculateCost("deepseek-chat-2026-06", 1000, 1000);

        assertThat(cost).isEqualTo(0.003);
    }

    @Test
    void calculateCost_unknownOrNullModel_returnsZero() {
        assertThat(service.calculateCost("unknown-model", 1000, 1000)).isZero();
        assertThat(service.calculateCost(null, 1000, 1000)).isZero();
    }

    private static CostTrackingService.ModelPrice price(double input, double output) {
        CostTrackingService.ModelPrice price = new CostTrackingService.ModelPrice();
        price.setInput(input);
        price.setOutput(output);
        return price;
    }
}
