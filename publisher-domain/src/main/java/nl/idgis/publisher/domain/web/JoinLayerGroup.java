/**
 * 
 */
package nl.idgis.publisher.domain.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the LayerGroup Join table.
 * @author Rob
 *
 */
public class JoinLayerGroup extends Identifiable {

	private static final long serialVersionUID = 1476377550981081163L;
	
	private final Integer order;
	private final LayerGroup group;
	private final Layer layer;
	private final Style style;
	
	@JsonCreator
	public JoinLayerGroup(
			final @JsonProperty("id") String id, 
			final @JsonProperty("order") Integer order, 
			final @JsonProperty("group") LayerGroup group,
			final @JsonProperty("layer") Layer layer,
			final @JsonProperty("style") Style style
			) {
		super(id);
		this.order = order;
		this.group = group;
		this.layer = layer;
		this.style = style;
	}

	@JsonGetter
	public Integer getOrder() {
		return order;
	}

	@JsonGetter
	public LayerGroup getGroup() {
		return group;
	}

	@JsonGetter
	public Layer getLayer() {
		return layer;
	}

	@JsonGetter
	public Style getStyle() {
		return style;
	}

	
	
}
