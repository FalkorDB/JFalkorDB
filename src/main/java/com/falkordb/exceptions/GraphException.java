package com.falkordb.exceptions;

import redis.clients.jedis.exceptions.JedisDataException;

/**
 * Graph query evaluation exception. An instance of GraphException is
 * thrown when Graph encounters an error during query evaluation.
 */
public class GraphException extends JedisDataException {
  private static final long serialVersionUID = -476099681322055468L;

  /**
   * @param message the error message
   */
  public GraphException(String message) {
    super(message);
  }

  /**
   * @param cause the cause of the exception
   */
  public GraphException(Throwable cause) {
    super(cause);
  }

  /**
   * @param message the error message
   * @param cause the cause of the exception
   */
  public GraphException(String message, Throwable cause) {
    super(message, cause);
  }
}