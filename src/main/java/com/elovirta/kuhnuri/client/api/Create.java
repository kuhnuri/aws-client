package com.elovirta.kuhnuri.client.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.util.List;

public class Create {
    @JsonProperty("input")
    public final URI input;
    @JsonProperty("transtype")
    public final List<String> transtype;

    public Create(final String input, final List<String> transtype) {
        this.input = URI.create(input);
        this.transtype = transtype;
    }
}
