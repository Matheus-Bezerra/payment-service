package dev.matheus.payment.adapter.out.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorizationResponse(String status, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(Boolean authorization) {
    }

    public boolean authorized() {
        return data != null && Boolean.TRUE.equals(data.authorization());
    }
}
