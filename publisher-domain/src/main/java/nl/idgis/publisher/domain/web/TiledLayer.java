/**
 * 
 */
package nl.idgis.publisher.domain.web;

import java.awt.Dimension;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mysema.query.annotations.QueryProjection;

/**
 * Represents the TiledLayer table.
 * @author Rob
 *
 */
public class TiledLayer extends Identifiable {

	private static final long serialVersionUID = 6823352848522244095L;

	private final String name;
	private final Boolean enabled;
	private final String mimeFormats;
	private final Integer metaWidth, metaHeight;
	private final Integer expireCache;
	private final Integer expireClients;
	private final Integer gutter;
	
	@JsonCreator
	@QueryProjection
	public TiledLayer(
			final @JsonProperty("id") String id, 
			final @JsonProperty("name") String name, 
			final @JsonProperty("enabled") Boolean enabled, 
			final @JsonProperty("mimeFormats") String mimeFormats,
			final @JsonProperty("metaWidth") Integer metaWidth,
			final @JsonProperty("metaHeight") Integer metaHeight, 
			final @JsonProperty("expireCache") Integer expireCache, 
			final @JsonProperty("expireClients") Integer expireClients, 
			final @JsonProperty("gutter") Integer gutter) {
		super(id);
		this.name = name;
		this.enabled = enabled;
		this.mimeFormats = mimeFormats;
		this.metaWidth = metaWidth;
		this.metaHeight = metaHeight;
		this.expireCache = expireCache;
		this.expireClients = expireClients;
		this.gutter = gutter;
	}

	@JsonGetter
	public String getName() {
		return name;
	}

	@JsonGetter
	public Boolean getEnabled() {
		return enabled;
	}

	@JsonGetter
	public String getMimeFormats() {
		return mimeFormats;
	}

	@JsonGetter
	public Integer getMetaWidth() {
		return metaWidth;
	}

	@JsonGetter
	public Integer getMetaHeight() {
		return metaHeight;
	}

	@JsonGetter
	public Integer getExpireCache() {
		return expireCache;
	}

	@JsonGetter
	public Integer getExpireClients() {
		return expireClients;
	}

	@JsonGetter
	public Integer getGutter() {
		return gutter;
	}
	
}
