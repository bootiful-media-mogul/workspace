import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.io.*;

class Runner {


    static List<String> runAndReturnOutputLines(Path p, String... cmd) throws IOException, InterruptedException {
        var ps = runAndReturnProcess(p, cmd);
        try (var r = new BufferedReader(new InputStreamReader(ps.getInputStream()))) {
            return r.readAllLines();
        }
    }

    static Process runAndReturnProcess(Path path, String... cmd) throws IOException, InterruptedException {
        var repo = path.getParent().toAbsolutePath().normalize();
        var p = new ProcessBuilder(cmd).directory(repo.toFile()).redirectErrorStream(true).start();
        p.waitFor();
        return p;
    }

    static Process runButDontWait(Path dir, String... cmd) throws IOException, InterruptedException {
        return new ProcessBuilder(cmd).directory(dir.toFile()).inheritIO().start();
    }

    static int run(Path dir, String... cmd) throws IOException, InterruptedException {
        return runButDontWait(dir, cmd)
                .waitFor();
    }

}
