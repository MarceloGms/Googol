# compile java files
compile:
	@javac -cp lib/* src/*.java -d bin

# run client
cli:
	@java -cp bin Client

# run gateway
gw: compile
	@java -cp bin Gateway

# run barrels
brl:
	@java -cp bin Barrel

# run downloaders
dl:
	@java -cp bin;lib/* Downloader
# for linux
# @java -cp bin:lib/* Downloader

# gen javadoc
# cd src
# javadoc -private -d javadoc -sourcepath src -classpath "../lib/*.jar" *.java