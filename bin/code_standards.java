//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS org.springframework.boot:spring-boot-starter:4.1.0
//SOURCES utils.java

import org.springframework.util.function.ThrowingConsumer;
import org.yaml.snakeyaml.Yaml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

private static void write(Document doc, boolean preflight, Path pom) throws Exception {
    stripWhitespaceNodes(doc);

    try (var sw = preflight ? new StringWriter() : new FileWriter(pom.toFile())) {
        write(doc, sw);
        if (preflight)
            IO.println("transformed pom.xml: " + sw);
    }
}

static void stripWhitespaceNodes(Document doc) throws XPathExpressionException {
    var xp = XPathFactory.newInstance().newXPath();
    var empty = (NodeList) xp.evaluate(
            "//text()[normalize-space(.)='']", doc, XPathConstants.NODESET);
    for (var i = 0; i < empty.getLength(); i++) {
        var node = empty.item(i);
        node.getParentNode().removeChild(node);
    }
}

/**
 * True if a <plugin> with our groupId + artifactId already exists anywhere.
 */
static boolean hasPlugin(Document doc, String groupId, String artifactId) {
    var plugins = doc.getElementsByTagName("plugin");
    for (var i = 0; i < plugins.getLength(); i++) {
        var plugin = (Element) plugins.item(i);
        var gid = childText(plugin, "groupId");
        var aid = childText(plugin, "artifactId");
        if (groupId.equals(gid) && artifactId.equals(aid)) {
            return true;
        }
    }
    return false;
}

/**
 * Find or create <project><build><plugins>, returning the plugins element.
 */
static Element ensurePluginsElement(Document doc) {
    var project = doc.getDocumentElement();
    var build = firstChildElement(project, "build");
    if (build == null) {
        build = doc.createElement("build");
        project.appendChild(build);
    }
    var plugins = firstChildElement(build, "plugins");
    if (plugins == null) {
        plugins = doc.createElement("plugins");
        build.appendChild(plugins);
    }
    return plugins;
}

static Element buildPluginElement(Document doc, String groupId, String artifactId, String version) {
    var plugin = doc.createElement("plugin");
    plugin.appendChild(textElement(doc, "groupId", groupId));
    plugin.appendChild(textElement(doc, "artifactId", artifactId));
    plugin.appendChild(textElement(doc, "version", version));
    return plugin;
}

// --- small DOM helpers ---
static Element firstChildElement(Element parent, String name) {
    var children = parent.getChildNodes();
    for (var i = 0; i < children.getLength(); i++) {
        var n = children.item(i);
        if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) {
            return (Element) n;
        }
    }
    return null;
}

static String childText(Element parent, String name) {
    var e = firstChildElement(parent, name);
    return e == null ? null : e.getTextContent().trim();
}

static Element textElement(Document doc, String name, String value) {
    var e = doc.createElement(name);
    e.setTextContent(value);
    return e;
}

static void write(Document doc, Writer pom) throws Exception {
    var t = TransformerFactory.newInstance().newTransformer();
    t.setOutputProperty(OutputKeys.INDENT, "yes");
    t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
    t.transform(new DOMSource(doc), new StreamResult(pom));
}

void main() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor();) {
        var start = System.currentTimeMillis();
        var callables = new ArrayList<Callable<Void>>();
        var root = Paths.get(".");
        var preflight = false;
        try (var paths = Files.walk(root)) {
            var poms = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .toList();
            for (var pom : poms)
                callables.add(() -> {
                    process(pom, preflight);
                    return null;
                });
            executor.invokeAll(callables);
            var stop = System.currentTimeMillis();
            IO.println("total time in ms.:" + (stop - start));
        }
    }
}

void process(Path pom, boolean preflight) throws Exception {
    var springBootVersion = "4.1.0";
    var mavenJavaFormatMavenPlugin = "0.0.47";
    var springAiVersion = "2.0.0";
    var javaVersion = "25";
    var springCloudVersion = "2025.1.2";
    var springModulithVersion = "2.1.0";
    var version = "1.0.0-SNAPSHOT";
    var processors = List.of(
            new VersionMavenProjectTransformer (version),//
            new GroupIdMavenProjectTransformer("com.secureallthethingsbook"), //
            new SpringBootParentVersionMavenProjectTransformer(springBootVersion), //
            new JavaformatPluginAddingMavenProjectTransformer(mavenJavaFormatMavenPlugin), //
            new JavaformatPluginApplyingMavenProjectTransformer(),
            new PropertiesOverridingMavenProjectTransformer("java.version", javaVersion),
            new PropertiesOverridingMavenProjectTransformer("spring-ai.version", springAiVersion),
            new PropertiesOverridingMavenProjectTransformer("spring-cloud.version", springCloudVersion),
            new PropertiesOverridingMavenProjectTransformer("spring-modulith.version", springModulithVersion),
            new VirtualThreadsEnabledMavenProjectTransformer()
    );

    var dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(false);
    dbf.setIgnoringElementContentWhitespace(true);

    var doc = dbf.newDocumentBuilder().parse(pom.toFile());
    doc.getDocumentElement().normalize();

    var mavenProject = new MavenProject(pom, doc);

    for (var p : processors) {
        p.accept(mavenProject);
        write(doc, preflight, pom);
    }
}

interface MavenProjectTransformer extends ThrowingConsumer<MavenProject> {
}

record MavenProject(Path pomFile, Document pom) {
}

static class PropertiesOverridingMavenProjectTransformer implements MavenProjectTransformer {

    private final String propertyValue;

    private final String propertyName;

    PropertiesOverridingMavenProjectTransformer(String propertyName, String propertyValue) {
        this.propertyValue = propertyValue;
        this.propertyName = propertyName;
    }

    @Override
    public void acceptWithException(MavenProject mp) throws Exception {
        var doc = mp.pom();
        var project = doc.getDocumentElement();
        var properties = firstChildElement(project, "properties");
        if (properties == null) {
            properties = doc.createElement("properties");
            project.appendChild(properties);
        }
        var propertyElement = firstChildElement(properties, this.propertyName);
        if (propertyElement == null) {
            propertyElement = doc.createElement(this.propertyName);
            properties.appendChild(propertyElement);
        }
        if (!propertyValue.equals(propertyElement.getTextContent().trim())) {
            propertyElement.setTextContent(this.propertyValue);
        }
    }
}

static class JavaformatPluginApplyingMavenProjectTransformer implements MavenProjectTransformer {

    @Override
    public void acceptWithException(MavenProject document) throws Exception {
        var pomFile = new File(document
                .pomFile() //
                .normalize() //
                .toFile()
                .getAbsolutePath());
        var parent = pomFile.getParentFile();
        var mvnw = new File(parent, "mvnw");
        Runner.run(parent.toPath(), mvnw.getAbsolutePath(), "spring-javaformat:apply");
    }
}

static class JavaformatPluginAddingMavenProjectTransformer implements MavenProjectTransformer {

    private final String groupId = "io.spring.javaformat";
    private final String artifactId = "spring-javaformat-maven-plugin";
    private final String version;

    JavaformatPluginAddingMavenProjectTransformer(String version) {
        this.version = version;
    }

    @Override
    public void acceptWithException(MavenProject doc) throws Exception {
        if (hasPlugin(doc.pom(), groupId, artifactId)) {
            return;
        }
        var plugins = ensurePluginsElement(doc.pom());
        plugins.appendChild(buildPluginElement(doc.pom(), groupId, artifactId, version));
    }

}

static class VersionMavenProjectTransformer implements MavenProjectTransformer {

    private final String version;

    VersionMavenProjectTransformer(String version) {
        this.version = version;
    }

    @Override
    public void acceptWithException(MavenProject mp) throws Exception {
        var doc = mp.pom();
        var project = doc.getDocumentElement();
        var versionEl = firstChildElement(project, "version");
        if (versionEl == null) {
            versionEl = doc.createElement("version");
            // keep the conventional gid/aid/version ordering when we have to create it
            var artifactId = firstChildElement(project, "artifactId");
            if (artifactId != null) {
                project.insertBefore(versionEl, artifactId.getNextSibling());
            }//
            else {
                project.appendChild(versionEl);
            }
        }
        if (!version.equals(versionEl.getTextContent().trim())) {
            versionEl.setTextContent(this.version);
        }
    }
}

static class GroupIdMavenProjectTransformer implements MavenProjectTransformer {

    private final String groupId;

    GroupIdMavenProjectTransformer(String groupId) {
        this.groupId = groupId;
    }

    @Override
    public void acceptWithException(MavenProject mp) throws Exception {
        var doc = mp.pom();
        var project = doc.getDocumentElement();
        var groupIdEl = firstChildElement(project, "groupId");
        if (groupIdEl == null) {
            groupIdEl = doc.createElement("groupId");
            project.appendChild(groupIdEl);
        }
        if (!groupId.equals(groupIdEl.getTextContent().trim())) {
            groupIdEl.setTextContent(this.groupId);
        }
    }

}

static class SpringBootParentVersionMavenProjectTransformer implements MavenProjectTransformer {

    private final String groupId = "org.springframework.boot";
    private final String artifactId = "spring-boot-starter-parent";
    private final String version;

    SpringBootParentVersionMavenProjectTransformer(String version) {
        this.version = version;
    }

    @Override
    public void acceptWithException(MavenProject doc) throws Exception {

        var parent = firstChildElement(doc.pom().getDocumentElement(), "parent");
        if (parent == null) {
            return; // no parent block; nothing to pin
        }
        var gid = childText(parent, "groupId");
        var aid = childText(parent, "artifactId");
        if (!groupId.equals(gid) || !artifactId.equals(aid)) {
            return; // some other parent; leave it alone
        }

        var versionEl = firstChildElement(parent, "version");
        if (versionEl == null) {
            versionEl = doc.pom().createElement("version");
            versionEl.setTextContent(version);
            parent.appendChild(versionEl);
        }//
        else if (!version.equals(versionEl.getTextContent().trim())) {
            versionEl.setTextContent(version);
        }
    }

}

/**
 * Ensures every project enables virtual threads by guaranteeing the equivalent of
 * {@code spring.threads.virtual.enabled=true} lives in its {@code application.properties}
 * or {@code application.yml}/{@code application.yaml}. If the setting is already present
 * (in any form) the file is left untouched; otherwise it is added at the very bottom,
 * using the proper nested hierarchy when the file is YAML.
 */
static class VirtualThreadsEnabledMavenProjectTransformer implements MavenProjectTransformer {

    static final String VT_KEY = "spring.threads.virtual.enabled";

    @Override
    public void acceptWithException(MavenProject mp) throws Exception {
        var moduleDir = mp.pomFile().toAbsolutePath().normalize().getParent();
        var resources = moduleDir.resolve("src/main/resources");
        if (!Files.isDirectory(resources)) {
            return; // not an application module (e.g. a parent / BOM pom)
        }

        var properties = resources.resolve("application.properties");
        var yml = resources.resolve("application.yml");
        var yaml = resources.resolve("application.yaml");

        var handled = false;
        if (Files.isRegularFile(properties)) {
            ensureInProperties(properties);
            handled = true;
        }
        if (Files.isRegularFile(yml)) {
            ensureInYaml(yml);
            handled = true;
        }
        if (Files.isRegularFile(yaml)) {
            ensureInYaml(yaml);
            handled = true;
        }
        if (!handled) {
            // No config file at all yet; establish the standard in application.properties.
            //ensureInProperties(properties);
        }
    }

    static void ensureInProperties(Path file) throws Exception {
        var exists = Files.isRegularFile(file);
        var text = exists ? Files.readString(file) : "";
        if (exists && propertiesHasKey(text, VT_KEY)) {
            return; // already present; leave it alone
        }
        var sb = new StringBuilder(text);
        if (!text.isEmpty() && !text.endsWith("\n")) {
            sb.append('\n');
        }
        sb.append(VT_KEY).append("=true").append('\n');
        Files.createDirectories(file.getParent());
        Files.writeString(file, sb.toString());
    }

    static void ensureInYaml(Path file) throws Exception {
        var text = Files.readString(file);
        // SnakeYAML is used only to *detect* whether the key already exists; it never
        // rewrites the file, so existing comments (e.g. AsciiDoctor callouts) stay intact.
        if (yamlHasKey(asStringKeyedMap(new Yaml().load(text)), VT_KEY)) {
            return; // already present; leave it alone
        }
        // Missing — splice the minimal set of nested lines into the existing hierarchy
        // (or, if there is no spring: block, append the whole chain at the very bottom)
        // via pure text editing, so nothing else in the file moves or changes.
        Files.writeString(file, insertYamlPath(text, List.of("spring", "threads", "virtual", "enabled"), "true"));
    }

    /**
     * Inserts {@code path} (e.g. spring/threads/virtual/enabled) with {@code value} into a
     * block-style YAML document using text editing only. Reuses whatever leading segments
     * already exist, appends the remaining segments to the bottom of the deepest matching
     * block, and leaves every other line — including comments and blank lines — untouched.
     */
    static String insertYamlPath(String text, List<String> path, String value) {
        var eol = text.contains("\r\n") ? "\r\n" : "\n";
        var lines = new ArrayList<>(Arrays.asList(text.split("\r\n|\r|\n", -1)));
        var step = detectStep(lines);

        var start = 0;              // start of the current parent's block (inclusive)
        var end = lines.size();     // end of the current parent's block (exclusive)
        var parentIndent = -step;   // sentinel so top-level children sit at indent 0
        var matched = 0;            // number of leading path segments already present

        for (; matched < path.size(); matched++) {
            var childIndent = parentIndent + step;
            var found = -1;
            for (var i = start; i < end; i++) {
                var line = lines.get(i);
                if (isSkippable(line)) {
                    continue;
                }
                var indent = indentOf(line);
                if (indent <= parentIndent) {
                    break; // fell out of the parent's block
                }
                if (indent == childIndent && path.get(matched).equals(keyOf(line))) {
                    found = i;
                    break;
                }
            }
            if (found == -1) {
                break; // this segment (and everything below it) needs to be inserted
            }
            // Descend into the matched key's own block.
            parentIndent = childIndent;
            start = found + 1;
            var blockEnd = end;
            for (var i = found + 1; i < end; i++) {
                var line = lines.get(i);
                if (!isSkippable(line) && indentOf(line) <= childIndent) {
                    blockEnd = i;
                    break;
                }
            }
            end = blockEnd;
        }

        if (matched == path.size()) {
            return text; // fully present already (guarded by the caller, but be safe)
        }

        // Insert at the bottom of the deepest matched block, above any trailing blank lines.
        var insertAt = end;
        while (insertAt > start && lines.get(insertAt - 1).strip().isEmpty()) {
            insertAt--;
        }
        var baseIndent = parentIndent + step;
        var newLines = new ArrayList<String>();
        for (var d = matched; d < path.size(); d++) {
            var indent = " ".repeat(baseIndent + (d - matched) * step);
            var last = d == path.size() - 1;
            newLines.add(indent + path.get(d) + (last ? ": " + value : ":"));
        }
        lines.addAll(insertAt, newLines);
        return String.join(eol, lines);
    }

    static int detectStep(List<String> lines) {
        var min = Integer.MAX_VALUE;
        for (var line : lines) {
            if (isSkippable(line)) {
                continue;
            }
            var indent = indentOf(line);
            if (indent > 0) {
                min = Math.min(min, indent);
            }
        }
        return min == Integer.MAX_VALUE ? 2 : min;
    }

    static int indentOf(String line) {
        var i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    static boolean isSkippable(String line) {
        var t = line.strip();
        return t.isEmpty() || t.startsWith("#");
    }

    static String keyOf(String line) {
        var t = line.strip();
        if (t.isEmpty() || t.startsWith("#")) {
            return null;
        }
        var colon = t.indexOf(':');
        return colon < 0 ? null : t.substring(0, colon).strip();
    }

    static boolean propertiesHasKey(String text, String key) {
        for (var raw : text.split("\\R", -1)) {
            var line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            var end = line.length();
            for (var i = 0; i < line.length(); i++) {
                var c = line.charAt(i);
                if (c == '=' || c == ':' || Character.isWhitespace(c)) {
                    end = i;
                    break;
                }
            }
            if (line.substring(0, end).equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** True if the flattened, dot-joined form of the YAML contains {@code key}. */
    static boolean yamlHasKey(Map<String, Object> root, String key) {
        var flat = new LinkedHashMap<String, Object>();
        flatten("", root, flat);
        return flat.containsKey(key);
    }

    static void flatten(String prefix, Map<?, ?> map, Map<String, Object> out) {
        for (var e : map.entrySet()) {
            var k = prefix.isEmpty() ? String.valueOf(e.getKey()) : prefix + "." + e.getKey();
            if (e.getValue() instanceof Map<?, ?> nested) {
                flatten(k, nested, out);
            }
            else {
                out.put(k, e.getValue());
            }
        }
    }

    static Map<String, Object> asStringKeyedMap(Object loaded) {
        var out = new LinkedHashMap<String, Object>();
        if (loaded instanceof Map<?, ?> m) {
            for (var e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

}
