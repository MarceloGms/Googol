# compile java files
compile:
	@javac -cp lib/* src/com/googol/googolfe/*.java -d bin

# run client
cli:
	@java -cp bin com.googol.googolfe.Client

# run gateway
gw: compile
	@java -cp bin com.googol.googolfe.Gateway

# run barrels
brl:
	@java -cp bin com.googol.googolfe.Barrel

# run downloaders
dl:
	@java -cp bin;lib/* Downloader
# for linux
# @java -cp bin:lib/* Downloader

# generate jar file
jar:
	@jar cf Googol.jar bin com.googol.googolfe.*.class

# gen javadoc
# cd src
# javadoc -private -d javadoc -sourcepath src -classpath "../lib/*.jar" *.java