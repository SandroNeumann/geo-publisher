package nl.idgis.publisher.domain.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import nl.idgis.publisher.domain.EntityType;
import nl.idgis.publisher.domain.MessageProperties;

public final class DefaultMessageProperties implements MessageProperties {
	
	private static final long serialVersionUID = 240068555815262512L;

	private final EntityType entityType;
	
	private final String identification;
	
	private final String title;
	
	@JsonCreator
	public DefaultMessageProperties(@JsonProperty("entityType") EntityType entityType,
			@JsonProperty("identification") String identification, @JsonProperty("title") String title) {
	
		this.entityType = entityType;
		this.identification = identification;
		this.title = title;
	}

	@Override
	public EntityType getEntityType () {
		return entityType;
	}

	@Override
	public String getIdentification () {
		return identification;
	}

	@Override
	public String getTitle () {
		return title;
	}

}
