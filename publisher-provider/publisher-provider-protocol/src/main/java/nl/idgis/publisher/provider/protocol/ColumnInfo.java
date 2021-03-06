package nl.idgis.publisher.provider.protocol;

import java.io.Serializable;

import nl.idgis.publisher.domain.service.Type;

/**
 * A column belonging to a {@link TableInfo} of a {@link VectorDatasetInfo}.
 * 
 * @author copierrj
 *
 */
public class ColumnInfo implements Serializable {
	
	private static final long serialVersionUID = 4496684640742666661L;
	
	private final String name;
	
	private final Type type;
	
	/**
	 * Creates a column.
	 * @param name the column name.
	 * @param type the data type.
	 */
	public ColumnInfo(String name, Type type) {
		this.name = name;
		this.type = type;
	}

	/**
	 * 
	 * @return the column name
	 */
	public String getName() {
		return name;
	}

	/**
	 * 
	 * @return the data type
	 */
	public Type getType() {
		return type;
	}

	@Override
	public String toString() {
		return "ColumnInfo [name=" + name + ", type=" + type + "]";
	}
}
