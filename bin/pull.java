//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS org.springframework.boot:spring-boot-starter:4.1.0
//SOURCES utils.java

void main() throws IOException {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor();) {
        var callables = new ArrayList<Callable<Void>>();
        var start = Paths.get("").toAbsolutePath();
        try (var stream = Files.walk(start)) {
            stream.forEach(path -> {
                if (isDirectory(path)) callables.add(buildCallable(path));
            });
        }
        executor.invokeAll(callables);
    }//
    catch (Exception e) {
        throw new RuntimeException(e);
    }
}

private boolean isDirectory(Path path) {
    return Files.isDirectory(path) && path.getFileName().toString().equalsIgnoreCase(".git");
}

private Callable<Void> buildCallable(Path path) {
    return () -> {
        try {
            var proc = Runner.runAndReturnProcess(path, "git", "pull");
            try (var in = proc.getInputStream()) {
                var output = new String(in.readAllBytes());
                if (!output.isBlank() && !output.contains("Already up to date."))
                    IO.println(path.getParent().toAbsolutePath().toString() + "\n\t" + output);
            }
        }// 
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    };
}

