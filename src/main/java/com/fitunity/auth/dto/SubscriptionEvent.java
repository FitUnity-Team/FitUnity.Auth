package com.fitunity.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class SubscriptionEvent {
    @JsonProperty("userId")
    public String userId;
    @JsonProperty("status")
    public String status; // "ACTIVE", "EXPIREE", "NONE"
    @JsonProperty("expiresAt")
    public LocalDateTime expiresAt; // nullable

    public SubscriptionEvent() {}
}
