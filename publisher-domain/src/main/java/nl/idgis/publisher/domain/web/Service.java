/**
 * 
 */
package nl.idgis.publisher.domain.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the Service table.
 * @author Rob
 *
 */
public class Service extends Identifiable {

	private static final long serialVersionUID = 5100298928846396739L;

	private final String name;
	private final String title;
	private final String alternateTitle;
	private final String abstract_;
	private final String keywords;
	private final String metadata;
	private final String watermark;
	private final LayerGroup rootGroup;
	private final Category category;
	
	@JsonCreator
	public Service(
			final @JsonProperty("id") String id, 
			final @JsonProperty("name") String name, 
			final @JsonProperty("title") String title, 
			final @JsonProperty("title") String alternateTitle, 
			final @JsonProperty("abstract") String abstract_, 
			final @JsonProperty("keywords") String keywords, 
			final @JsonProperty("metadata") String metadata,
			final @JsonProperty("watermark") String watermark,
			final @JsonProperty("rootGroup") LayerGroup rootGroup,
			final @JsonProperty("category") Category category) {
		super(id);
		this.name = name;
		this.title = title;
		this.alternateTitle = alternateTitle;
		this.abstract_ = abstract_;
		this.keywords = keywords;
		this.metadata = metadata;
		this.watermark = watermark;
		this.rootGroup = rootGroup;
		this.category = category;
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
	public String getAlternateTitle() {
		return alternateTitle;
	}

	@JsonGetter
	public String getAbstract_() {
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
	public String getWatermark() {
		return watermark;
	}

	@JsonGetter
	public LayerGroup getRootGroup() {
		return rootGroup;
	}

	@JsonGetter
	public Category getCategory() {
		return category;
	}
	
}
