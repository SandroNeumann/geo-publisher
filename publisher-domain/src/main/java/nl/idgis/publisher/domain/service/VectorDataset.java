package nl.idgis.publisher.domain.service;

import java.util.Date;
import java.util.Set;

import nl.idgis.publisher.domain.Log;

public final class VectorDataset extends Dataset {
	
	private static final long serialVersionUID = -1297933860250008426L;
	
	private final Table table;
	
	public VectorDataset(String id, String categoryId, Date revisionDate, Set<Log> logs, Table table) {
		super(id, categoryId, revisionDate, logs);
		
		this.table = table;
	}

	public Table getTable() {
		return table;
	}
	
	@Override
	public String toString() {
		return "Dataset [id=" + id + ", categoryId=" + categoryId + ", table="
				+ table + ", revisionDate=" + revisionDate + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((table == null) ? 0 : table.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		VectorDataset other = (VectorDataset) obj;
		if (table == null) {
			if (other.table != null)
				return false;
		} else if (!table.equals(other.table))
			return false;
		return true;
	}
}