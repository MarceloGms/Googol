# How to Run

- Make sure you have `make` installed.
- Make sure you have `JDK 17` installed.

### Run Gateway

- Open a terminal.
- Navigate to the primary directory (googol).
- Run the command: `make gw`

##### Note:

- If the code is not compiling you might have to create a `bin` folder in the primary directory.
- For Linux users, use the commented command in the `dl` section of the makefile instead of the current one.

### Run Barrels

- Open a terminal (or multiple terminals).
- Navigate to the primary directory (googol).
- Run the command: `make brl`

##### Note:

- Make sure the gateway is running.

### Run Downloader

- Open a terminal.
- Navigate to the primary directory (googol).
- Run the command: `make dl`

##### Note:

- Make sure the gateway is running.

### Run Clients

- Open a terminal (or multiple terminals for multiple clients).
- Navigate to the primary directory (googol).
- Run the command for each client: `make cli`

##### Note:

- Make sure the gateway is running.

### Final considerations

- Ensure all of the server's components are running before starting to perform client operations.
- Modify the `assets/config.properties` file to change the server ip address, number of barrels or number of downloaders.
