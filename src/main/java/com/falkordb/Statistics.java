package com.falkordb;


/**
 * An interface for the statistics of a result set.
 */
public interface Statistics {
	
	/**
	 * Different Statistics labels 
	 */
	enum Label{
		/**
		 * The number of labels added.
		 */
		LABELS_ADDED("Labels added"),
		/**
		 * The number of indices created.
		 */
		INDICES_ADDED("Indices created"),
		/**
		 * The number of indices deleted.
		 */
		INDICES_DELETED("Indices deleted"),
		/**
		 * The number of nodes created.
		 */
		NODES_CREATED("Nodes created"),
		/**
		 * The number of nodes deleted.
		 */
		NODES_DELETED("Nodes deleted"),
		/**
		 * The number of relationships deleted.
		 */
		RELATIONSHIPS_DELETED("Relationships deleted"),
		/**
		 * The number of properties set.
		 */
		PROPERTIES_SET("Properties set"),
		/**
		 * The number of relationships created.
		 */
		RELATIONSHIPS_CREATED("Relationships created"),
		/**
		 * Whether the execution was cached.
		 */
		CACHED_EXECUTION("Cached execution"),
		/**
		 * The query internal execution time.
		 */
		QUERY_INTERNAL_EXECUTION_TIME("Query internal execution time");

	    private final String text;

		Label(String text) {
			this.text = text;
		}
		
		@Override
		public String toString() {
	        return this.text;
	    }

		/**
		 * Get a Label by label text
		 * 
		 * @param value label text
		 * @return the matching Label
		 */
	    public static Label getEnum(String value) {
	        for(Label v : values()) {
	            if(v.toString().equalsIgnoreCase(value)) return v;
	        }
	        return null;
	    }
	}
	
	/**
	 * Retrieves the relevant statistic  
	 * 
	 * @param label the requested statistic label 
	 * @return a String representation of the specific statistic or null
	 */
	String getStringValue(Statistics.Label label);

	/**
	 * @return the number of nodes created
	 */
	int nodesCreated();
	
	/**
	 * @return the number of nodes deleted
	 */
	int nodesDeleted();
	
	/**
	 * @return the number of indices added
	 */
	int indicesAdded();

	/**
	 * @return the number of indices deleted
	 */
	int indicesDeleted();
	
	/**
	 * @return the number of labels added
	 */
	int labelsAdded();
	
	/**
	 * @return the number of relationships deleted
	 */
	int relationshipsDeleted();
	
	/**
	 * @return the number of relationships created
	 */
	int relationshipsCreated();
	
	/**
	 * @return the number of properties set
	 */
	int propertiesSet();

	/**
	 * @return whether the execution was cached
	 */
	boolean cachedExecution();
}