import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class Init {

    public static void main(String[] args) throws Exception {
        var cwd = new File(".").getAbsolutePath();
        var ghOrg = "git@github.com:bootiful-media-mogul";
        var clone = new File(System.getenv("HOME") + "/code/mogul");
        if (!clone.exists()) clone.mkdirs();
        try (var ex = Executors.newCachedThreadPool()) {

            var repositories = ("mogul-podcast-audio-processor mogul-gateway" +
                    " mogul-client mogul-service workspace pipeline")
                    .split(" ");

            var waiting = new HashSet<Future<?>>();
            for (var repo : repositories)
                waiting.add(ex.submit(run(ghOrg + "/" + repo + ".git", new File(clone, repo.trim()))));

            for (var f : waiting) f.get();
        }

        for (var tc : new File(cwd, "to-copy").listFiles())
            exec("cp -r  " + tc.getAbsolutePath() + " " + new File(clone, tc.getName()).getAbsolutePath());

        for (var f : clone.listFiles())
            System.out.println("" + f.getAbsolutePath());

        System.out.println("Finished initializing " + clone.getAbsolutePath());
    }

    private static void exec(String cmd) throws Exception {
        var proc = Runtime.getRuntime().exec(cmd);
        var exit = proc.waitFor();
        if (exit != 0)
            System.out.println(cmd + " exited improperly.");
    }

    private static Runnable run(String gitUrl, File folder) {
        return (Runnable) () -> {
            try {
                var fullPath = folder.getAbsolutePath();
                var cmd = folder.exists() ? "cd " + fullPath + " ; git pull " : "git clone " + gitUrl + " " + fullPath;
                exec(cmd);
            } //
            catch (Exception ioException) {
                System.err.println("got an exception: [" + ioException + "]");
            }
        };
    }
}