package com.falkordb;

import java.util.List;

/**
 * Container for Graph result values.
 * 
 * List records are returned from Graph statement execution, contained within a ResultSet.
 */
public interface Record {

  /**
   * The value at the given field index
   * 
   * @param index field index
   * @param <T> return value type
   * 
   * @return the value
   */
  <T> T getValue(int index);

  /**
   * The value at the given field
   * 
   * @param key header key 
   * @param <T> return value type
   * 
   * @return the value
   */
  <T> T getValue(String key);  
  
  /**
   * The value at the given field index (represented as String)
   * 
   * @param index field index
   * @return string representation of the value
   */
  String getString(int index);

  /**
   * The value at the given field (represented as String)
   * 
   * @param key header key 
   * 
   * @return string representation of the value
   */
  String getString(String key);

  /**
   * The keys of the record
   * 
   * @return list of the record key
   */
  List<String> keys();

  /**
   * The values of the record
   * 
   * @return list of the record values
   */
  List<Object> values();

  /**
   * Check if the record header contains the given key
   * 
   * @param key header key 
   * 
   * @return <code>true</code> if the the key exists 
   */
  boolean	containsKey(String key);

  /**
   * The number of fields in this record
   * 
   * @return the number of fields
   */
  int size();
}
