//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS org.springframework.boot:spring-boot-starter:4.1.0
//DEPS com.joshlong:bitwarden-cli-client-spring-boot-starter:0.0.4
//DEPS com.jayway.jsonpath:json-path:3.0.0

import com.joshlong.bitwarden.Bitwarden;
import com.joshlong.bitwarden.DefaultBitwarden;
import tools.jackson.databind.JsonNode;

/**
 * i wrote a <em>very</em> anemic little wrappper on top of the Bitwarden CLI and published it to Maven Central.
 * That wrapper makes the orchestration in this script possible.
 */

void main(String[] args) throws Exception {
    var bw = new DefaultBitwarden(System.getenv("BW_SESSION"));
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var env = this.build(bw, executor);
        var script = "";
        for (var e : env.entrySet())
            script += "export " + e.getKey() + "='" + e.getValue() + "' " + System.lineSeparator();
        IO.println(script);
    }
}

Map<String, String> build(Bitwarden bw, Executor executor) throws Exception {
    var env = new ConcurrentHashMap<String, String>();
    var contributors = List.<Runnable>of(
            () -> contributeConstants(bw, env),
            () -> contributeWordpress(bw, env),
            () -> contributeElasticsearch(bw, env),
            () -> contributePodbean(bw, env),
            () -> contributeAbly(bw, env),
            () -> contributeOpenAi(bw, env),
            () -> contributeAws(bw, env),
            () -> contributeAuth0(bw, env),
            () -> contributeGithub(bw, env)
    );
    var futures = contributors.stream() //
            .map(contributor -> CompletableFuture.runAsync(contributor, executor))
            .toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(futures).join();
    contributeDerived(env);
    return Map.copyOf(env);
}

void contributeConstants(Bitwarden bitwarden, Map<String, String> env) {
    env.put("PODCAST_ASSETS_S3_BUCKET", "podcast-assets-bucket-dev");
    env.put("PODCAST_ASSETS_S3_BUCKET_FOLDER", "062019");
    env.put("PODCAST_INPUT_S3_BUCKET", "podcast-input-bucket-dev");
    env.put("PODCAST_OUTPUT_S3_BUCKET", "podcast-output-bucket-dev");
    env.put("PODCASTS_PROCESSOR_RMQ_REQUESTS", "podcast-processor-requests");
    env.put("PODCASTS_PROCESSOR_RMQ_REPLIES", "podcast-processor-replies");
    env.put("DB_USERNAME", "mogul");
    env.put("DB_PASSWORD", "mogul");
    env.put("DB_HOST", "localhost");
    env.put("DB_SCHEMA", "mogul");
    env.put("RMQ_HOST", "127.0.0.1");
    env.put("RMQ_USERNAME", "mogul");
    env.put("RMQ_PASSWORD", "mogul");
    env.put("RMQ_VIRTUAL_HOST", "/");
}

void contributeWordpress(Bitwarden bitwarden, Map<String, String> env) {
    var item = bitwarden.item("mogul-wordpress-client--production");
    env.put("WP_CLIENT_ID", this.field(bitwarden, item, "client-id"));
    env.put("WP_CLIENT_SECRET", this.field(bitwarden, item, "client-secret"));
}

void contributeElasticsearch(Bitwarden bitwarden, Map<String, String> env) {
    var item = bitwarden.item("elasticsearch-development");
    env.put("ELASTICSEARCH_API_KEY", this.field(bitwarden, item, "api-key"));
    env.put("ELASTICSEARCH_API_HOST", this.field(bitwarden, item, "api-host"));
    env.put("ELASTICSEARCH_OTEL_HOST", this.field(bitwarden, item, "otel-host"));
    env.put("ELASTICSEARCH_OTEL_HEADER", this.field(bitwarden, item, "otel-header"));
}

void contributePodbean(Bitwarden bitwarden, Map<String, String> env) {
    var item = bitwarden.item("development-podbean");
    env.put("PODBEAN_CLIENT_ID", this.field(bitwarden, item, "client-id"));
    env.put("PODBEAN_CLIENT_SECRET", this.field(bitwarden, item, "client-secret"));
}

void contributeAbly(Bitwarden bitwarden, Map<String, String> env) {
    env.put("ABLY_KEY", this.password(bitwarden, "mogul-ably-api-key-dev"));
}

void contributeOpenAi(Bitwarden bitwarden, Map<String, String> env) {
    env.put("OPENAI_KEY", this.password(bitwarden, "mogul-openai-key"));
    // env.put("MOGUL_OPENAI_KEY", this.password(bitwarden, "mogul-openai-key"));
}

void contributeAws(Bitwarden bitwarden, Map<String, String> env) {
    var item = bitwarden.item("aws-s3-credentials--production");
    env.put("AWS_REGION", this.field(bitwarden, item, "region"));
    env.put("AWS_ACCESS_KEY_ID", this.field(bitwarden, item, "access-key"));
    env.put("AWS_ACCESS_KEY_SECRET", this.field(bitwarden, item, "access-key-secret"));
}

void contributeAuth0(Bitwarden bitwarden, Map<String, String> env) {
    var item = bitwarden.item("mogul-auth0-client--production");
    env.put("AUTH0_CLIENT_ID", this.field(bitwarden, item, "client-id"));
    env.put("AUTH0_CLIENT_SECRET", this.field(bitwarden, item, "client-secret"));
    env.put("AUTH0_DOMAIN", this.field(bitwarden, item, "domain"));
}

void contributeGithub(Bitwarden bitwarden, Map<String, String> env) {
    var mogulGithubPat = bitwarden.item("mogul-github-packages-pat").get("notes").textValue();
    env.put("GH_USER", "joshlong");
    env.put("GH_TOKEN", mogulGithubPat);
}

void contributeDerived(Map<String, String> env) {
    env.put("RMQ_ADDRESS", "rmq://%s:%s@%s/%s".formatted(env.get("RMQ_USERNAME"), env.get("RMQ_PASSWORD"),
            env.get("RMQ_HOST"), env.get("RMQ_VIRTUAL_HOST")));
    env.put("SPRING_RABBITMQ_HOST", env.get("RMQ_HOST"));
    env.put("SPRING_RABBITMQ_USERNAME", env.get("RMQ_USERNAME"));
    env.put("SPRING_RABBITMQ_PASSWORD", env.get("RMQ_PASSWORD"));
    env.put("SPRING_RABBITMQ_VIRTUAL_HOST", env.get("RMQ_VIRTUAL_HOST"));
    env.put("SPRING_DATASOURCE_URL",
            "jdbc:postgresql://%s/%s".formatted(env.get("DB_HOST"), env.get("DB_SCHEMA")));
    env.put("SPRING_DATASOURCE_USERNAME", env.get("DB_USERNAME"));
    env.put("SPRING_DATASOURCE_PASSWORD", env.get("DB_PASSWORD"));
}

String field(Bitwarden bitwarden, JsonNode item, String name) {
    return bitwarden.selectString(item, "$.fields[?(@.name == '%s')].value".formatted(name));
}

String password(Bitwarden bitwarden, String itemId) {
    return bitwarden.selectString(itemId, "$.login.password");
}