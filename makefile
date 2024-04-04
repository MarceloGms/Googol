# compile java files
compile:
	@javac -cp lib/* -Xlint:unchecked src/*.java -d bin

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

# run googol app
# (not working)
# run: compile gw dl brl
