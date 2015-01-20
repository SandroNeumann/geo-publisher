/**
 * 
 */
package nl.idgis.publisher.domain.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the LayerStyle Join table.
 * @author Rob
 *
 */
public class JoinLayerStyle extends Identifiable {

	private static final long serialVersionUID = 7536143133586903609L;

	private final Boolean defaultStyle;
	private final Layer layer;
	private final Style style;
	
	@JsonCreator
	public JoinLayerStyle(
			final @JsonProperty("id") String id, 
			final @JsonProperty("defaultStyle") Boolean defaultStyle, 
			final @JsonProperty("layer") Layer layer,
			final @JsonProperty("style") Style style
			) {
		super(id);
		this.defaultStyle = defaultStyle;
		this.layer = layer;
		this.style = style;
	}

	@JsonGetter
	public Boolean getDefaultStyle() {
		return defaultStyle;
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
