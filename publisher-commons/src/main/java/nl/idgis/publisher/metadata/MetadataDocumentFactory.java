package nl.idgis.publisher.metadata;

import nl.idgis.publisher.xml.XMLDocument;
import nl.idgis.publisher.xml.XMLDocumentFactory;

public class MetadataDocumentFactory {
		
	private XMLDocumentFactory xmlDocumentFactory;
	
	public MetadataDocumentFactory() throws Exception {
		xmlDocumentFactory = new XMLDocumentFactory();
	}	

	public MetadataDocument parseDocument(byte[] content) throws Exception {
		XMLDocument xmlDocument = xmlDocumentFactory.parseDocument(content);
		
		return new MetadataDocument(xmlDocument);
	}

}
