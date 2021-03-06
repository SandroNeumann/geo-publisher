package nl.idgis.publisher.database.messages;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nl.idgis.publisher.domain.job.JobState;

public class DatasetInfo extends BaseDatasetInfo implements Serializable {

	private static final long serialVersionUID = -1977233205832290039L;
	
	private String sourceDatasetId, sourceDatasetName;
	private String categoryId, categoryName;
	private final String filterConditions;
	private final Boolean imported;	
	private final Boolean sourceDatasetColumnsChanged;
	private final Timestamp lastImportTime;
	private final JobState lastImportJobState;
	private final List<StoredNotification> notifications;	

	public DatasetInfo(String id, String name, String sourceDatasetId,
			String sourceDatasetName, String categoryId, String categoryName, 
			final String filterConditions,
			final Boolean imported,
			final Boolean sourceDatasetColumnsChanged,
			final Timestamp lastImportTime,
			final String lastImportJobState,
			final List<StoredNotification> notifications) {
		super(id, name);
		
		this.sourceDatasetId = sourceDatasetId;
		this.sourceDatasetName = sourceDatasetName;
		this.categoryId = categoryId;
		this.categoryName = categoryName;
		this.filterConditions = filterConditions;
		this.imported = imported;
		this.sourceDatasetColumnsChanged = sourceDatasetColumnsChanged;
		this.lastImportTime = lastImportTime;
		this.lastImportJobState = toJobState(lastImportJobState);		
		this.notifications = notifications == null ? Collections.<StoredNotification>emptyList () : new ArrayList<> (notifications);
	}
	
	private static JobState toJobState(String jobStateName) {
		if(jobStateName == null) {
			return null;
		} else {
			return JobState.valueOf(jobStateName);
		}
	}

	public String getSourceDatasetId() {
		return sourceDatasetId;
	}

	public String getSourceDatasetName() {
		return sourceDatasetName;
	}

	public String getCategoryId() {
		return categoryId;
	}

	public String getCategoryName() {
		return categoryName;
	}
	
	public String getFilterConditions () {
		return filterConditions;
	}

	public Boolean getImported() {
		return imported;
	}	

	public Boolean getSourceDatasetColumnsChanged() {
		return sourceDatasetColumnsChanged;
	}

	public Timestamp getLastImportTime() {
		return lastImportTime;
	}

	public JobState getLastImportJobState() {
		return lastImportJobState;
	}

	public List<StoredNotification> getNotifications () {
		return Collections.unmodifiableList (notifications);
	}

	@Override
	public String toString() {
		return "DatasetInfo [sourceDatasetId=" + sourceDatasetId
				+ ", sourceDatasetName=" + sourceDatasetName + ", categoryId="
				+ categoryId + ", categoryName=" + categoryName
				+ ", filterConditions=" + filterConditions + ", imported="
				+ imported + ", sourceDatasetColumnsChanged="
				+ sourceDatasetColumnsChanged + ", lastImportTime="
				+ lastImportTime + ", lastImportJobState=" + lastImportJobState
				+ ", notifications=" + notifications + "]";
	}
		
}
