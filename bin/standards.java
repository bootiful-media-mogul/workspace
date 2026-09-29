//usr/bin/env jbang "$0" "$0" "$@" ; exit $?
//JAVA 25
//DEPS org.springframework.boot:spring-boot-starter:4.1.0
//SOURCES utils.java

import org.springframework.util.Assert;

/**
 * NOTE: the shebang line passes the script name as a parameter and is
 * thus different to 99% of the other JBang.dev scripts
 */
void main(String[] args) throws Exception {
    var fqnScriptName = args[0];
    var bad = (fqnScriptName == null || fqnScriptName.isEmpty());
    Assert.state(!bad, "what is the name of the current script?");
    var here = new File(new File(fqnScriptName).getParent())
            .getAbsoluteFile();
    for (var scriptName : new String[]{"adoc_standards.java", "code_standards.java"}) {
        var scriptFile = new File(here, scriptName);
        Assert.state(scriptFile.exists(), "the script named " + scriptFile.getAbsolutePath() + " doesn't exist!");
        var fqn = scriptFile.getAbsolutePath();
        IO.println("invoking " + fqn);
        Runner.run(Paths.get("."), fqn);
    }

}