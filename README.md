## How to Run

Make sure you have `make` installed.

#### Run Gateway:

- Open a terminal.
- Navigate to the primary directory (googol).
- Run the command: `make gw`

#### Run Barrels:

- Open a terminal.
- Navigate to the primary directory (googol).
- Run the command: `make brl`

##### Note:

- Make sure the gateway is running.

#### Run Downloader:

- Open a terminal.
- Navigate to the primary directory (googol).
- Run the command: `make dl`

##### Note:

- Make sure the gateway is running.
- For Linux users, use the commented command in the `dl` section of the makefile instead of the current one.

#### Run Clients:

- Open another terminal (or multiple terminals for multiple clients).
- Navigate to the primary directory (googol).
- Run the command for each client: `make cli`

Ensure that the server is running before you start the clients. This setup will allow you to run the server and multiple clients concurrently.
