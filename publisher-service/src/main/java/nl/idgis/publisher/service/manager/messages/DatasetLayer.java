package nl.idgis.publisher.service.manager.messages;

public interface DatasetLayer extends Layer {

	String getSchemaName();
	
	String getTableName();
}