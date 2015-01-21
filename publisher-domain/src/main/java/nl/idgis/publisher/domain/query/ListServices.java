package nl.idgis.publisher.domain.query;

import nl.idgis.publisher.domain.response.Page;
import nl.idgis.publisher.domain.web.LayerGroup;
import nl.idgis.publisher.domain.web.Service;

public class ListServices implements DomainQuery<Page<Service>>{

	private static final long serialVersionUID = -4287124609844744944L;

	private final String layerGroupId;
	
	public ListServices (LayerGroup layerGroup) {
		this.layerGroupId = (layerGroup == null ? null : layerGroup.id());
	}
	
	public String getLayerGroupId () {
		return this.layerGroupId;
	}
	
}
