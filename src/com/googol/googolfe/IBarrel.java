package com.googol.googolfe;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The interface for a Barrel, which represents a component of the search engine responsible
 * for storing and managing indexed data.
 * This interface allows the Gateway to connect to the Barrel via RMI-callback.
 */
public interface IBarrel extends Remote {
  /**
   * Sends a message to the Barrel, mainly the shutdown message from the Gateway.
   * @param s The message to send.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public void send(String s) throws RemoteException;

  /**
   * Searches for the specified query string in the indexed data stored in the Barrel.
   * @param s The query string to search for.
   * @return The search results as a string.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public String search(String s) throws RemoteException;

  /**
   * Finds sub-links related to the specified URL in the indexed data stored in the Barrel.
   * @param s The URL for which to find sub-links.
   * @return The found sub-links as a string.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public String findSubLinks(String s) throws RemoteException;

  /**
   * Retrieves the top 10 searches performed in the indexed data stored in the Barrel.
   * @return The top 10 searches as a string.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public String getTop10Searches() throws RemoteException;

  /**
   * Retrieves the ID of the Barrel.
   * @return The ID of the Barrel.
   * @throws RemoteException If a communication-related exception occurs.
   */
  public int getId() throws RemoteException;
}