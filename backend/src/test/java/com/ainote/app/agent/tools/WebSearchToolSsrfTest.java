package com.ainote.app.agent.tools;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

class WebSearchToolSsrfTest {

    @Test
    void fetchClientDoesNotAutomaticallyFollowRedirects() throws Exception {
        Field clientField = WebSearchTool.class.getDeclaredField("httpClient");
        clientField.setAccessible(true);

        HttpClient client = (HttpClient) clientField.get(null);

        assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
    }
}
