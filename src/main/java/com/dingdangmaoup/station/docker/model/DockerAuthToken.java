package com.dingdangmaoup.station.docker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DockerAuthToken {
    private String token;

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("issued_at")
    private String issuedAt;

    public String getEffectiveToken() {
        return token != null ? token : accessToken;
    }
}
