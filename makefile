# run client
# usage: make cli id=n
cli:
	@java -cp bin Client $(id)

# run gateway
gw:
	@java -cp bin Gateway

# run barrels
brl:
	@java -cp bin Barrel

# run downloaders
dl:
	@java -cp bin Downloader