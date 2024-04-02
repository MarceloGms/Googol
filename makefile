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

THREADS = 5
# run downloaders
dl:
	@java -cp bin;lib/* Downloader $(THREADS)
# for linux
# @java -cp bin:lib/* Downloader

# run googol app
# (not working)
# run: compile gw dl brl
