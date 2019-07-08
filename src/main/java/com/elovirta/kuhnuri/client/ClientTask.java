package com.elovirta.kuhnuri.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.taskdefs.Zip;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class ClientTask extends MatchingTask {
    private HttpClient client;
    private File input;
    private URI api;
    private String transtype;
    private File tempDir;

    @Override
    public void init() throws BuildException {
        client = HttpClient.newBuilder().build();
    }

    @Override
    public void execute() throws BuildException {
        try {
            var zip = createPackage();
            var upload = getUpload();
            doUpload(zip, upload.upload);
            var create = getCreate(upload.url);
            var job = doCreate(create);
            log(job.status, Project.MSG_INFO);
        } catch (IOException | InterruptedException e) {
            throw new BuildException(e);
        }

    }

    private File createPackage() {
        var destFile = new File(tempDir, "package.zip");
        var zipTask = new Zip();
        zipTask.setProject(getProject());
        zipTask.setDestFile(destFile);
        zipTask.setBasedir(input.getParentFile());
        zipTask.execute();
        return destFile;
    }

    private Job doCreate(final Create create) throws IOException, InterruptedException {
        final var body = new ObjectMapper().writerFor(Create.class).writeValueAsBytes(create);
        final var createUri = api.resolve("job");
        log("Do create " + createUri, Project.MSG_INFO);
        final var upload = HttpRequest.newBuilder()
                .uri(createUri)
                .timeout(Duration.ofMinutes(2))
                .POST(BodyPublishers.ofByteArray(body))
                .build();
        final var response = client.send(upload, BodyHandlers.ofString(StandardCharsets.UTF_8));
        log(response.body(), Project.MSG_INFO);
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readerFor(Job.class)
                .readValue(response.body());
    }

    private Upload getUpload() throws IOException, InterruptedException {
        final var createUri = api.resolve("upload");
        log("Get upload " + createUri, Project.MSG_INFO);
        final var request = HttpRequest.newBuilder()
                .uri(createUri)
                .timeout(Duration.ofMinutes(1))
                .GET()
                .build();
        final var response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
        log(response.body(), Project.MSG_INFO);
        return new ObjectMapper().readerFor(Upload.class).readValue(response.body());
    }

    private void doUpload(final File zip, final URI url) throws IOException, InterruptedException {
        log("Upload " + zip + " to " + url, Project.MSG_INFO);
        var upload = HttpRequest.newBuilder()
                .uri(url)
                .timeout(Duration.ofMinutes(2))
                .PUT(BodyPublishers.ofFile(zip.toPath()))
                .build();
        client.send(upload, BodyHandlers.discarding());
    }

    private Create getCreate(final URI uri) {
        return new Create(String.format("jar:%s!/%s", uri, input.getName()), Arrays.asList(transtype));
    }

    public void setApi(final String api) {
        try {
            this.api = new URI(api);
        } catch (URISyntaxException e) {
            throw new BuildException(e);
        }
    }

    public void setInput(final File input) {
        this.input = input;
    }

    public void setTranstype(final String transtype) {
        this.transtype = transtype;
    }

    public void setTemp(final File tempDir) {
        this.tempDir = tempDir;
    }

    public static class Job {
        public final String status;

        public Job(@JsonProperty("status") final String status) {
            this.status = status;
        }
    }

    public static class Upload {
        public final URI url;
        public final URI upload;

        public Upload(@JsonProperty("url") final String url,
                      @JsonProperty("upload") final String upload) {
            this.url = URI.create(url);
            this.upload = URI.create(upload);
        }
    }

    public static class Create {
        @JsonProperty("input")
        public final URI input;
        @JsonProperty("transtype")
        public final List<String> transtype;

        public Create(final String input, final List<String> transtype) {
            this.input = URI.create(input);
            this.transtype = transtype;
        }
    }
}
