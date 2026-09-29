//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS org.springframework.boot:spring-boot-starter:4.1.0
//SOURCES utils.java

void main() throws IOException {
    var start = Paths.get("").toAbsolutePath();
    try (var stream = Files.walk(start)) {
        stream.forEach(path -> {
            if (Files.isDirectory(path) && path.getFileName().toString().equalsIgnoreCase(".git")) {
                try {
                    var proc = Runner.runAndReturnProcess(path, "git", "status", "--porcelain");
                    try (var in = proc.getInputStream()) {
                        if (!new String(in.readAllBytes()).isBlank())
                            IO.println(path.getParent().toAbsolutePath().toString());
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
