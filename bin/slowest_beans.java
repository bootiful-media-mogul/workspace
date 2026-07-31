///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS org.springframework.boot:spring-boot-starter-restclient:4.1.0

//
// Rank the slowest beans in a Spring Boot startup actuator response.
//
// Usage: jbang SlowestBeans.java [url-or-path] [top-n]
//   url-or-path  an http(s) URL (fetched with Spring's RestClient) or a local
//                file path (read with Jackson). Defaults to
//                http://localhost:8080/actuator/startup
//   top-n        how many rows to print. Defaults to 20.

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.json.JsonMapper;


@JsonIgnoreProperties(ignoreUnknown = true)
record Startup(Timeline timeline) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record Timeline(List<Event> events) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record Event(String duration, StartupStep startupStep) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record StartupStep(String name, List<Tag> tags) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record Tag(String key, String value) {}

record Row(double seconds, String bean) {}

/** Parse an ISO-8601 duration like "PT0.008378S" or "PT1M2.5S" into seconds. */
Pattern DURATION = Pattern.compile("PT(?:(\\d+)M)?([\\d.]+)S");

double secs(String s) {
    var m = DURATION.matcher(s);
    if (!m.matches()) throw new IllegalArgumentException("bad duration: " + s);
    var mins = m.group(1) == null ? 0 : Double.parseDouble(m.group(1));
    return mins * 60 + Double.parseDouble(m.group(2));
}

 void main(String[] args) throws Exception {
    var source = "http://localhost:8080/actuator/startup";
    var topN = 20;

    var startup = load(source);

    var rows = startup.timeline().events().stream()
            .filter(e -> "spring.beans.instantiate".equals(e.startupStep().name()))
            .map(e -> new Row(secs(e.duration()), beanName(e.startupStep())))
            .sorted(Comparator.comparingDouble(Row::seconds).reversed())
            .toList();
    IO.println("total instantiate steps: " + rows.size());
    
    rows.stream().limit(topN).forEach(r -> System.out.printf("%8.1f ms  %s%n", r.seconds() * 1000, r.bean()));
}

String beanName(StartupStep step) {
    return step.tags().stream()
            .filter(t -> "beanName".equals(t.key()))
            .map(Tag::value)
            .findFirst()
            .orElse("?");
}

Startup load(String source) throws Exception {
    if (source.startsWith("http://") || source.startsWith("https://")) {
        return RestClient.create()
                .get()
                .uri(source)
                .retrieve()
                .body(Startup.class);
    }
    return JsonMapper.builder().build()
            .readValue(Files.readString(Path.of(source)), Startup.class);
}