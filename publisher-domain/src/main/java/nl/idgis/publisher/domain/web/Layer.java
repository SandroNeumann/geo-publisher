/**
 * 
 */
package nl.idgis.publisher.domain.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mysema.query.annotations.QueryProjection;

/**
 * Represents the Layer table.
 * @author Rob
 *
 */
public class Layer extends Identifiable {

	private static final long serialVersionUID = 8467658884503006245L;

	private final String name;
	private final String title;
	private final String abstract_;
	private final String keywords;
	private final String metadata;
	private final EntityRef tiledLayer;
	
	@JsonCreator
	@QueryProjection
	public Layer(
			final @JsonProperty("id") String id, 
			final @JsonProperty("name") String name, 
			final @JsonProperty("title") String title, 
			final @JsonProperty("abstract") String abstract_, 
			final @JsonProperty("keywords") String keywords, 
			final @JsonProperty("metadata") String metadata,
			final @JsonProperty("tiledLayer") EntityRef tiledLayer) {
		super(id);
		this.name = name;
		this.title = title;
		this.abstract_ = abstract_;
		this.keywords = keywords;
		this.metadata = metadata;
		this.tiledLayer = tiledLayer;
	}

	@JsonGetter
	public String getName() {
		return name;
	}

	@JsonGetter
	public String getTitle() {
		return title;
	}

	@JsonGetter
	public String getAbstract() {
		return abstract_;
	}

	@JsonGetter
	public String getKeywords() {
		return keywords;
	}

	@JsonGetter
	public String getMetadata() {
		return metadata;
	}
	
	@JsonGetter
	public EntityRef getEntityRef() {
		return tiledLayer;
	}

	
}
