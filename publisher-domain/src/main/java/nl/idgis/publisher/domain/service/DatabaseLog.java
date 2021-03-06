package nl.idgis.publisher.domain.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DatabaseLog extends DatasetLog<DatabaseLog> {

	private static final long serialVersionUID = 8126046350738527476L;
	
	private final String tableName;

	@JsonCreator
	public DatabaseLog(
			@JsonProperty("tableName") String tableName) {
		
		this.tableName = tableName;
	}
	
	private DatabaseLog(Dataset dataset, String tableName) {
		super(dataset);
		
		this.tableName = tableName;
	}
	
	public String getTableName() {
		return this.tableName;
	}
	
	@Override
	public DatabaseLog withDataset(Dataset dataset) {
		return new DatabaseLog(dataset, tableName);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((tableName == null) ? 0 : tableName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DatabaseLog other = (DatabaseLog) obj;
		if (tableName == null) {
			if (other.tableName != null)
				return false;
		} else if (!tableName.equals(other.tableName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "DatabaseLog [tableName=" + tableName + "]";
	}
}
