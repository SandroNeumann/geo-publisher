/**
 * 
 */
package nl.idgis.publisher.database.messages;

import java.io.Serializable;

import com.mysema.query.annotations.QueryProjection;

/**
 * @author Rob
 *
 */
public class LayerInfo implements Serializable {

	private static final long serialVersionUID = 7733921490811501510L;

	private final String identification;
	private final String name;
	private final String title;
	private final String abstract_;
	private final String keywords;
	private final String metadata;
	private final String tiledLayerId;
	private final String tiledLayerName;
	
	@QueryProjection
	public LayerInfo(String identification, String name, String title, String abstract_, String keywords, String metadata,
			String tiledLayerId, String tiledLayerName) {
		super();
		this.identification = identification;
		this.name = name;
		this.title = title;
		this.abstract_ = abstract_;
		this.keywords = keywords;
		this.metadata = metadata;
		this.tiledLayerId = tiledLayerId;
		this.tiledLayerName = tiledLayerName;
	}

	public String getIdentification() {
		return identification;
	}

	public String getName() {
		return name;
	}

	public String getTitle() {
		return title;
	}

	public String getAbstract() {
		return abstract_;
	}

	public String getKeywords() {
		return keywords;
	}

	public String getMetadata() {
		return metadata;
	}

	public String getTiledLayerId() {
		return tiledLayerId;
	}

	public String getTiledLayerName() {
		return tiledLayerName;
	}

	
}
