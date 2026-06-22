package com.ainote.app.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class AdminAgentEvaluationRequest {

    @NotEmpty(message = "Evaluation cases are required")
    @Size(max = 100, message = "At most 100 evaluation cases are allowed")
    private List<@Valid AgentEvalCaseRequest> cases;

    public List<AgentEvalCaseRequest> getCases() {
        return cases;
    }

    public void setCases(List<AgentEvalCaseRequest> cases) {
        this.cases = cases;
    }

    public static class AgentEvalCaseRequest {
        @NotBlank(message = "User query is required")
        @Size(max = 8000, message = "User query must be at most 8000 characters")
        private String userQuery;

        @Size(max = 100, message = "Expected tool name must be at most 100 characters")
        private String expectedToolName;

        @Size(max = 100, message = "Expected action must be at most 100 characters")
        private String expectedAction;

        private Boolean expectSuccess;

        @Size(max = 50, message = "At most 50 expected keywords are allowed")
        private List<@NotBlank(message = "Expected keyword must not be blank") @Size(max = 200, message = "Expected keyword must be at most 200 characters") String> expectedResponseKeywords;

        public String getUserQuery() {
            return userQuery;
        }

        public void setUserQuery(String userQuery) {
            this.userQuery = userQuery;
        }

        public String getExpectedToolName() {
            return expectedToolName;
        }

        public void setExpectedToolName(String expectedToolName) {
            this.expectedToolName = expectedToolName;
        }

        public String getExpectedAction() {
            return expectedAction;
        }

        public void setExpectedAction(String expectedAction) {
            this.expectedAction = expectedAction;
        }

        public Boolean getExpectSuccess() {
            return expectSuccess;
        }

        public void setExpectSuccess(Boolean expectSuccess) {
            this.expectSuccess = expectSuccess;
        }

        public List<String> getExpectedResponseKeywords() {
            return expectedResponseKeywords;
        }

        public void setExpectedResponseKeywords(List<String> expectedResponseKeywords) {
            this.expectedResponseKeywords = expectedResponseKeywords;
        }
    }
}
