//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25


import java.io.*;
import java.util.*;
import java.util.concurrent.*;

void main(String[] args) throws Exception {

    var cwd = new File(".").getAbsolutePath();
    var ghOrg = "git@github.com:bootiful-media-mogul";
    var clone = new File(System.getenv("HOME") + "/code/mogul");

    if (!clone.exists()) clone.mkdirs();

    try (var ex = Executors.newCachedThreadPool()) {

        var repositories = ("clip gateway client api cleanvoice-client" +
            " workspace pipeline processors settings-client utils storage"+
            " jobrunr github-actions-setup-jvm-build-env github-actions-setup-container-build-env")
                .split(" ");

        var waiting = new HashSet<Future<?>>();
        for (var repo : repositories)
            waiting.add(ex.submit(run(ghOrg + "/" + repo + ".git", new File(clone, repo.trim()))));

        for (var f : waiting)
            f.get();
    }

    exec ("cp " + clone.getAbsolutePath () + "/workspace/mise.toml " + clone.getAbsolutePath()+ "/mise.toml");

    // the agent guidance for the whole workspace lives in the workspace repository, so it
    // survives a new laptop. link rather than copy, so editing ~/code/mogul/CLAUDE.md edits the
    // file git sees.
    for (var doc : List.of("CLAUDE.md", "AGENTS.md"))
        exec("ln -sfn workspace/" + doc + " " + clone.getAbsolutePath() + "/" + doc);

    for (var f : clone.listFiles())
        IO.println("" + f.getAbsolutePath());

    IO.println("Finished initializing " + clone.getAbsolutePath());
}

private static void exec(String cmd) throws Exception {
    var proc = Runtime.getRuntime().exec(cmd);
    var exit = proc.waitFor();
    if (exit != 0)
        IO.println(cmd + " exited improperly.");
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
