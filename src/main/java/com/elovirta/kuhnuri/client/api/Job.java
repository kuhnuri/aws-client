package com.elovirta.kuhnuri.client.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Job {
    public final String id;
    public final String status;

    public Job(@JsonProperty("id") final String id,
               @JsonProperty("status") final String status) {
        this.id = id;
        this.status = status;
    }
}
