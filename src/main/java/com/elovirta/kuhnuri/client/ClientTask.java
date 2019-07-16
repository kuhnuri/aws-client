package com.elovirta.kuhnuri.client;

import com.elovirta.kuhnuri.client.api.Create;
import com.elovirta.kuhnuri.client.api.Job;
import com.elovirta.kuhnuri.client.api.Upload;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.taskdefs.Zip;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

public class ClientTask extends MatchingTask {
    private HttpClient client;
    private File input;
    private URI api;
    private String transtype;
    private File tempDir;

    @Override
    public void init() throws BuildException {
        api = URI.create(getProject().getProperty("kuhnuri.api"));
        client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();
    }

    @Override
    public void execute() throws BuildException {
        try {
            final var zip = createPackage();
            final var upload = getUpload();
            doUpload(zip, upload.upload);
            final var create = getCreate(upload.url);
            var job = doCreate(create);
            final var id = job.id;
            final var start = System.currentTimeMillis();
            log(job.status);
            while (true) {
                Thread.sleep(5000);
                job = getJob(id);
                var duration = Duration.ofMillis(System.currentTimeMillis() - start);
                switch (job.status) {
                    case "queue":
                    case "process":
                        log(job.status + " " + (duration.getSeconds()));
                        break;
                    case "done":
                    case "error":
                        log(job.status + " " + (duration.getSeconds()));
                        return;
                    default:
                        throw new IllegalArgumentException(job.status);
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new BuildException(e);
        }

    }

    private File createPackage() {
        final var destFile = new File(tempDir, "package.zip");
        final var zipTask = new Zip();
        zipTask.setTaskName("zip");
        zipTask.setProject(getProject());
        zipTask.setDestFile(destFile);
        zipTask.setBasedir(input.getParentFile());
        zipTask.execute();
        return destFile;
    }

    private Job doCreate(final Create create) throws IOException, InterruptedException {
        final var body = new ObjectMapper().writerFor(Create.class).writeValueAsBytes(create);
        final var createUri = api.resolve("job");
        log(String.format("Do create %s", createUri), Project.MSG_VERBOSE);
        final var upload = HttpRequest.newBuilder()
                .uri(createUri)
                .timeout(Duration.ofMinutes(2))
                .POST(BodyPublishers.ofByteArray(body))
                .build();
        final var response = client.send(upload, BodyHandlers.ofString(StandardCharsets.UTF_8));
        log(response.body(), Project.MSG_VERBOSE);
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readerFor(Job.class)
                .readValue(response.body());
    }

    private Job getJob(final String id) throws IOException, InterruptedException {
        final var jobUri = api.resolve("job/" + id);
        log(String.format("Get job %s", id));
        final var upload = HttpRequest.newBuilder()
                .uri(jobUri)
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();
        final var response = client.send(upload, BodyHandlers.ofString(StandardCharsets.UTF_8));
        log(response.body(), Project.MSG_VERBOSE);
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readerFor(Job.class)
                .readValue(response.body());
    }

    private Upload getUpload() throws IOException, InterruptedException {
        final var createUri = api.resolve("upload");
        log(String.format("Get upload %s", createUri));
        final var request = HttpRequest.newBuilder()
                .uri(createUri)
                .timeout(Duration.ofMinutes(1))
                .GET()
                .build();
        final var response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
        log(response.body(), Project.MSG_VERBOSE);
        return new ObjectMapper().readerFor(Upload.class).readValue(response.body());
    }

    private void doUpload(final File zip, final URI url) throws IOException, InterruptedException {
        log(String.format("Upload %s", zip));
        log(String.format("Upload %s to %s", zip, url), Project.MSG_VERBOSE);
        final var upload = HttpRequest.newBuilder()
                .uri(url)
                .timeout(Duration.ofMinutes(2))
                .PUT(BodyPublishers.ofFile(zip.toPath()))
                .build();
        final HttpResponse<String> send = client.send(upload, BodyHandlers.ofString());
        if (send.statusCode() != 200) {
            throw new BuildException(send.body());
        }
    }

    private Create getCreate(final URI uri) {
        return new Create(getJarUri(uri, input.getName()), Arrays.asList(transtype));
    }

    private String getJarUri(final URI uri, final String path) {
        return String.format("jar:%s!/%s", uri, path);
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

}
