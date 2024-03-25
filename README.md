## How to Run

Make sure you have `make` installed.

#### Run Server:

- Open a terminal.
- Navigate to the primary directory (googol).
- Run the command: `make run`

#### Run Clients:

- Open another terminal (or multiple terminals for multiple clients).
- Navigate to the primary directory (googol).
- Run the command for each client: `make cli id=n`, where `n` is the ID of the client.
  - Example: `make cli id=1`, `make cli id=2`, etc.

Ensure that the server is running before you start the clients. This setup will allow you to run the server and multiple clients concurrently.
