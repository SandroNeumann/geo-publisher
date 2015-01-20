/**
 * 
 */
package nl.idgis.publisher.domain.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the Layer table.
 * @author Rob
 *
 */
public class LayerGroup extends Identifiable {

	private static final long serialVersionUID = 1476377550981081163L;
	
	private final String name;
	private final String title;
	private final String abstract_;
	private final LayerGroup parentGroup;
	private final TiledLayer tiledLayer;
	
	@JsonCreator
	public LayerGroup(
			final @JsonProperty("id") String id, 
			final @JsonProperty("name") String name, 
			final @JsonProperty("title") String title, 
			final @JsonProperty("abstract") String abstract_, 
			final @JsonProperty("parentGroup") LayerGroup parentGroup,
			final @JsonProperty("tiledLayer") TiledLayer tiledLayer) {
		super(id);
		this.name = name;
		this.title = title;
		this.abstract_ = abstract_;
		this.parentGroup = parentGroup;
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
	public LayerGroup getParentGroup() {
		return parentGroup;
	}
	
	@JsonGetter
	public TiledLayer getTiledLayer() {
		return tiledLayer;
	}

	
}
