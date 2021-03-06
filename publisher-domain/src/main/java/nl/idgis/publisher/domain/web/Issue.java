package nl.idgis.publisher.domain.web;

import java.sql.Timestamp;

import nl.idgis.publisher.domain.job.JobType;
import nl.idgis.publisher.domain.job.LogLevel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class Issue extends DashboardItem {

	private static final long serialVersionUID = -3635517395233123137L;
	
	private final JobType jobType;
	private final LogLevel logLevel;
	private final Timestamp when;
	
	@JsonCreator
	public Issue(
			final @JsonProperty("id") String id, 
			final @JsonProperty("message") Message message,
			final @JsonProperty("logLevel") LogLevel logLevel,
			final @JsonProperty("jobType") JobType jobType,
			final @JsonProperty("when") Timestamp when) {
		
		super(id, message);
		
		this.jobType = jobType;
		this.logLevel = logLevel;
		this.when = when;
	}

	@JsonGetter
	public JobType jobType() {
		return jobType;
	}
	
	@JsonGetter
	public LogLevel logLevel() {
		return logLevel;
	}
	
	@JsonGetter
	public Timestamp when () {
		return when;
	}

}
