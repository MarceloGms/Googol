# compile java files
compile:
	@javac -cp lib/* src/*.java -d bin

# run client
# usage: make cli id=n
cli: compile
	@java -cp bin Client $(id)

# run gateway
gw: compile
	@java -cp bin Gateway

# run barrels
brl:
	@java -cp bin Barrel

# run downloaders
dl: compile
	@java -cp bin;lib/* Downloader
# for linux
# @java -cp bin:lib/* Downloader

# run googol app
run: compile gw brl dl
