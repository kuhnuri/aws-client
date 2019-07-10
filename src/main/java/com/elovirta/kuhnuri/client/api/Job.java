package com.elovirta.kuhnuri.client.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Job {
    public final String status;

    public Job(@JsonProperty("status") final String status) {
        this.status = status;
    }
}
