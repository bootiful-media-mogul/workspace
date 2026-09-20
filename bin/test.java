//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS org.springframework.boot:spring-boot-starter:4.1.0
//DEPS com.joshlong:bitwarden-cli-client-spring-boot-starter:0.0.4
//DEPS com.jayway.jsonpath:json-path:3.0.0

import com.joshlong.bitwarden.Bitwarden;
import com.joshlong.bitwarden.DefaultBitwarden;
import tools.jackson.databind.JsonNode;


void main(String[] args) throws Exception {
     IO.println("export FOO=baz");
}