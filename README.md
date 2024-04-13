# workspace

clone this to incept your local development environment.

## usage
if you have `direnv` installed, then you need only allow the `.envrc` file to run: `direnv allow`

otherwise, just run the `Init.java` program directly: `java Init.java`

if you want it go even faster, compile to a native image:

`javac Main.java && native-image Init `

this will leave you, ten to twenty seconds later, with a new native binary for your operating system: `init`.

run it: `./init` 

enjoy!
