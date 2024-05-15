package com.googol.googolfe;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The interface for a Client of the search engine system.
 * This interface allows the Gateway to connect to the Client via RMI-callback.
 */
public interface IClient extends Remote {
  /**
   * Prints a message on the client's side.
   * @param s The message to print.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public void printOnClient(String s) throws RemoteException;
}