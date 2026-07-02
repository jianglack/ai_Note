package com.ainote.app.controller;

import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.security.AdminAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
class CostControllerAdminGuardTest {
    private AgentTraceRepository traceRepository;    private AdminAccessGuard adminAccessGuard;
    private CostController controller;

    @BeforeEach
    void setUp() {
        traceRepository = mock(AgentTraceRepository.class);
        adminAccessGuard = mock(AdminAccessGuard.class);
        controller = new CostController(traceRepository, adminAccessGuard);
    }

    @Test
    void costByDay_checksAdminBeforeQueryingCostData() {
        when(traceRepository.aggregateCostByDay(any(LocalDateTime.class))).thenReturn(List.of());

        controller.costByDay(30);

        InOrder inOrder = inOrder(adminAccessGuard, traceRepository);
        inOrder.verify(adminAccessGuard).checkAdminAccess();
        inOrder.verify(traceRepository).aggregateCostByDay(any(LocalDateTime.class));
    }

    @Test
    void costByModel_checksAdminBeforeQueryingCostData() {
        when(traceRepository.aggregateCostByModel(any(LocalDateTime.class))).thenReturn(List.of());

        controller.costByModel(30);

        InOrder inOrder = inOrder(adminAccessGuard, traceRepository);
        inOrder.verify(adminAccessGuard).checkAdminAccess();
        inOrder.verify(traceRepository).aggregateCostByModel(any(LocalDateTime.class));
    }

    @Test
    void topCost_checksAdminBeforeQueryingCostData() {
        when(traceRepository.findTopByCost(any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of());

        controller.topCost(10, 30);

        InOrder inOrder = inOrder(adminAccessGuard, traceRepository);
        inOrder.verify(adminAccessGuard).checkAdminAccess();
        inOrder.verify(traceRepository).findTopByCost(any(LocalDateTime.class), any(Pageable.class));
    }
}
