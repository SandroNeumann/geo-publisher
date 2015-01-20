/**
 * 
 */
package nl.idgis.publisher.domain.web;

import java.awt.Dimension;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the TiledLayer table.
 * @author Rob
 *
 */
public class TiledLayer extends Identifiable {

	private static final long serialVersionUID = 6823352848522244095L;

	private final String name;
	private final String identification;
	private final Boolean enabled;
	private final String mimeFormats;
	private final Dimension metaWidthHeight;
	private final Integer expireCache;
	private final Integer expireClients;
	private final Integer gutter;
	
	@JsonCreator
	public TiledLayer(
			final @JsonProperty("id") String id, 
			final @JsonProperty("name") String name, 
			final @JsonProperty("identification") String identification, 
			final @JsonProperty("enabled") Boolean enabled, 
			final @JsonProperty("mimeFormats") String mimeFormats,
			final @JsonProperty("metaWidthHeight") Dimension metaWidthHeight, 
			final @JsonProperty("expireCache") Integer expireCache, 
			final @JsonProperty("expireClients") Integer expireClients, 
			final @JsonProperty("gutter") Integer gutter) {
		super(id);
		this.name = name;
		this.identification = identification;
		this.enabled = enabled;
		this.mimeFormats = mimeFormats;
		this.metaWidthHeight = metaWidthHeight;
		this.expireCache = expireCache;
		this.expireClients = expireClients;
		this.gutter = gutter;
	}

	@JsonGetter
	public String getName() {
		return name;
	}

	@JsonGetter
	public String getIdentification() {
		return identification;
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
	public Dimension getMetaWidthHeight() {
		return metaWidthHeight;
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
