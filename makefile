# compile java files
compile:
	@javac -d bin src/*.java

# run client
# usage: make cli id=n
cli:
	@java -cp bin Client $(id)

# run gateway
gw: compile
	@java -cp bin Gateway

# run barrels
brl:
	@java -cp bin Barrel

# run downloaders
dl:
	@java -cp bin Downloader

# run googol app
run: compile gw brl dl
