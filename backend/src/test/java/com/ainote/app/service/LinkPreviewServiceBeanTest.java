package com.ainote.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LinkPreviewServiceBeanTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(UrlSafetyValidator.class)
            .withBean(LinkPreviewService.class);

    @Test
    void springInstantiatesServiceWithProductionConstructor() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LinkPreviewService.class);
        });
    }
}
