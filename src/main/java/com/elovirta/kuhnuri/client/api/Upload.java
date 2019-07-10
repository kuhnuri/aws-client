package com.elovirta.kuhnuri.client.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;

public class Upload {
    public final URI url;
    public final URI upload;

    public Upload(@JsonProperty("url") final String url,
                  @JsonProperty("upload") final String upload) {
        this.url = URI.create(url);
        this.upload = URI.create(upload);
    }
}
