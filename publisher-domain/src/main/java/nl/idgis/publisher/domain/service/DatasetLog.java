package nl.idgis.publisher.domain.service;

import nl.idgis.publisher.domain.EntityType;
import nl.idgis.publisher.domain.MessageProperties;

public abstract class DatasetLog<T extends DatasetLog<T>> implements MessageProperties {
	
	private static final long serialVersionUID = 2742502035495391359L;
	
	private final Dataset dataset;
	
	public DatasetLog() {
		this(null);
	}
	
	public DatasetLog(Dataset dataset) {
		this.dataset = dataset;
	}

	@Override
	public EntityType getEntityType() {
		return EntityType.DATASET;
	}

	@Override
	public String getIdentification() {
		if(dataset == null) {
			return null;
		}
		
		return dataset.getId();
	}

	@Override
	public String getTitle() {
		if(dataset == null) {
			return null;
		}
		
		return dataset.getId();
	}
	
	public abstract T withDataset(Dataset dataset);
}
