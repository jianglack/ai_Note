package com.ainote.app.service;

public interface MemoryAdvisorRawSignalAdvisor extends MemorySignalAdvisor {

    MemoryAdvisorRawResult adviseRaw(MemoryCapturePolicy.CaptureRequest request);
}
