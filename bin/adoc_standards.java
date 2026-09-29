//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS org.springframework.boot:spring-boot-starter:4.1.0
//DEPS org.asciidoctor:asciidoctorj:3.0.0

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.ast.StructuralNode;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * this applies standards to the various {code .adoc} files.
 */
void main(String[] args) throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor();) {
        var root = Paths.get(".");
        var callables = new ArrayList<Callable<Void>>();
        try (var paths = Files.walk(root)) {
            var adocs = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".adoc"))
                    .toList();
            for (var adoc : adocs) {
                callables.add(() -> {
                    process(adoc);
                    replaceDashes(adoc);
                    replaceCommonWords(adoc);
                    return null;
                });
            }
            executor.invokeAll(callables);
        }
    }
}

void replaceCommonWords(Path adoc) throws IOException {

    var content = Files.readString(adoc, StandardCharsets.UTF_8)
            .replaceAll("multi-factor", "multifactor");
    Files.writeString(adoc, content, StandardCharsets.UTF_8);

}

void replaceDashes(Path filePath) throws IOException {
    var file = filePath.toFile();
    var content = Files.readString(filePath, StandardCharsets.UTF_8)
            .replaceAll("[\u2013\u2014]", " - ");
    Files.writeString(filePath, content, StandardCharsets.UTF_8);
}

void process(Path adoc) {
    try {
        var result = ListingAttributeFixer.fix(adoc.toFile());
        Files.writeString(adoc, result.content());
    } //
    catch (Exception e) {
        throw new RuntimeException(e);
    }
}

class ListingAttributeFixer {

    // include::path/to/File.ext[...] -> capture the include target path
    private static final Pattern INCLUDE = Pattern.compile("include::([^\\[]+)\\[");
    // a stand-alone block attribute line, e.g. [source], [listing], [source,java,indent=0]
    private static final Pattern ATTRIBUTE_LINE = Pattern.compile("^\\s*\\[.*]\\s*$");
    // a source attribute line that already declares a language, e.g. [source,java...]
    private static final Pattern SOURCE_WITH_LANG = Pattern.compile("^\\s*\\[source\\s*,\\s*[^,\\]]+.*]\\s*$");
    // file extension -> Asciidoctor source language token
    private static final Map<String, String> LANGUAGES = Map.ofEntries( //
            Map.entry("java", "java"), //
            Map.entry("kt", "kotlin"), //
            Map.entry("kts", "kotlin"), //
            Map.entry("groovy", "groovy"), //
            Map.entry("gradle", "groovy"), //
            Map.entry("scala", "scala"), //
            Map.entry("xml", "xml"), //
            Map.entry("html", "html"), //
            Map.entry("properties", "properties"), //
            Map.entry("yaml", "yaml"), //
            Map.entry("yml", "yaml"), //
            Map.entry("json", "json"), //
            Map.entry("sql", "sql"), //
            Map.entry("js", "javascript"), //
            Map.entry("mjs", "javascript"), //
            Map.entry("ts", "typescript"), //
            Map.entry("tsx", "typescript"), //
            Map.entry("css", "css"), //
            Map.entry("sh", "shell"), //
            Map.entry("bash", "shell"), //
            Map.entry("proto", "proto"), //
            Map.entry("http", "http"), //
            Map.entry("adoc", "asciidoc") //
    );

    static Result fix(File adoc) throws IOException {
        // split(-1) keeps trailing empties (and any \r), so String.join("\n", ...) round-trips exactly.
        var lines = new ArrayList<>(List.of(Files.readString(adoc.toPath()).split("\n", -1)));

        // component model: does the Nth listing block (document order) already declare a language?
        var declaresLanguage = listingBlocksDeclareLanguage(adoc);
        // raw text: the 0-based index of the opening `----` of the Nth listing block.
        var openings = openingDelimiterIndexes(lines);

        // Pair AST blocks with raw delimiters by order. They should match 1:1; if the structure is
        // unusual and they don't, fall back to a purely textual "is there a [source,lang] above?" test.
        var trustAst = declaresLanguage.size() == openings.size();
        if (!trustAst)
            IO.println("// note: AST listing count (" + declaresLanguage.size() + ") != raw `----` count ("
                    + openings.size() + "); falling back to text-only detection.");

        var fixes = new ArrayList<Fix>();
        // apply from the bottom up so earlier indexes stay valid as we insert/replace lines.
        for (var i = openings.size() - 1; i >= 0; i--) {
            var idx = openings.get(i); // index of the opening `----`

            var alreadyAttributed = trustAst //
                    ? declaresLanguage.get(i) //
                    : idx > 0 && SOURCE_WITH_LANG.matcher(lines.get(idx - 1)).matches();
            if (alreadyAttributed)
                continue;

            var body = bodyOf(lines, idx);
            var guess = inferLanguage(body);
            var attributeLine = leadingWhitespace(lines.get(idx)) + "[source," + guess.language() + ",indent=0]";

            // if the line directly above is already a bracketed attribute line (a bare `[listing]`
            // or `[source]` with no language), upgrade it in place; otherwise insert a new line.
            if (idx > 0 && ATTRIBUTE_LINE.matcher(lines.get(idx - 1)).matches())
                lines.set(idx - 1, attributeLine);
            else
                lines.add(idx, attributeLine);

            fixes.add(new Fix(idx + 1, attributeLine.strip(), guess.reason()));
        }

        fixes.sort(Comparator.comparingInt(Fix::line));
        return new Result(String.join("\n", lines), fixes);
    }

    /**
     * Use the component model to report, in document order, whether each listing block has a language.
     */
    private static List<Boolean> listingBlocksDeclareLanguage(File adoc) {
        var out = new ArrayList<Boolean>();
        try (var asciidoctor = Asciidoctor.Factory.create()) {
            asciidoctor.registerLogHandler(record -> {
            }); // ignore unresolved-include noise; we only need block structure.
            var options = Options.builder() //
                    .safe(SafeMode.UNSAFE) //
                    .baseDir(adoc.getParentFile()) //
                    .sourcemap(true) //
                    .toFile(false) //
                    .build();
            collectListings(asciidoctor.loadFile(adoc, options), out);
        }
        return out;
    }

    private static void collectListings(StructuralNode node, List<Boolean> out) {
        // a `----` code block is context "listing"; a `[source,<lang>]` line sets the "language" attribute.
        if ("listing".equals(node.getContext()))
            out.add(node.getAttribute("language") != null);
        for (var child : node.getBlocks())
            if (child instanceof StructuralNode sn)
                collectListings(sn, out);
    }

    /**
     * 0-based indexes of every opening `----` delimiter, in document order (they alternate open/close).
     */
    private static List<Integer> openingDelimiterIndexes(List<String> lines) {
        var opens = new ArrayList<Integer>();
        var inside = false;
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().equals("----")) {
                if (!inside)
                    opens.add(i);
                inside = !inside;
            }
        }
        return opens;
    }

    /**
     * the raw lines inside the block: from just after the opening `----` to the matching close.
     */
    private static List<String> bodyOf(List<String> lines, int openIdx) {
        var body = new ArrayList<String>();
        for (var i = openIdx + 1; i < lines.size() && !lines.get(i).strip().equals("----"); i++)
            body.add(lines.get(i));
        return body;
    }

    private static String leadingWhitespace(String line) {
        return line.substring(0, line.length() - line.stripLeading().length());
    }

    /**
     * Best-guess language for a block: the include filename suffix first, then a light content sniff.
     */
    private static Guess inferLanguage(List<String> body) {
        // 1) primary clue: the suffix of an included file, e.g. include::.../Foo.java[] -> java
        for (var line : body) {
            var m = INCLUDE.matcher(line.strip());
            if (m.find()) {
                var suffix = suffixOf(m.group(1));
                var lang = LANGUAGES.get(suffix);
                if (lang != null)
                    return new Guess(lang, "from include suffix ." + suffix);
                if (!suffix.isEmpty())
                    return new Guess(suffix, "unmapped include suffix ." + suffix);
            }
        }
        // 2) otherwise sniff the literal content (rare - most blocks are includes).
        for (var raw : body) {
            var line = raw.strip();
            if (line.isEmpty())
                continue;
            if (line.startsWith("$ ") || line.startsWith("#") || line.matches("^(sdk|cd|export|echo|curl|mvn|gradle|docker|kubectl|git|npm|brew|sudo)\\b.*"))
                return new Guess("shell", "content looks like shell");
            if (line.startsWith("<"))
                return new Guess("xml", "content looks like xml");
            if (line.matches("(?i)^(select|insert|update|delete|create|alter|drop)\\b.*"))
                return new Guess("sql", "content looks like sql");
            if (line.contains("System.out") || line.contains("IO.print") ||
                    line.contains("public class") || line.startsWith("import ") || line.startsWith("package "))
                return new Guess("java", "content looks like java");
        }
        // 3) give up gracefully - a human can refine it, but the block is now attributed.
        return new Guess("text", "no include and content inconclusive");
    }

    private static String suffixOf(String includePath) {
        var path = includePath.trim();
        var slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        var name = slash >= 0 ? path.substring(slash + 1) : path;
        var dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    /**
     * a single correction: the (pre-edit, 1-based) delimiter line, the attribute line, and why.
     */
    record Fix(int line, String inserted, String reason) {
    }

    /**
     * the corrected document held in memory, plus the list of corrections applied.
     */
    record Result(String content, List<Fix> fixes) {
    }

    private record Guess(String language, String reason) {
    }
}
