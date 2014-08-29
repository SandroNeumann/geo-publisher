package nl.idgis.publisher.database;

import static nl.idgis.publisher.database.QCategory.category;
import static nl.idgis.publisher.database.QDataSource.dataSource;
import static nl.idgis.publisher.database.QDataset.dataset;
import static nl.idgis.publisher.database.QDatasetActiveNotification.datasetActiveNotification;
import static nl.idgis.publisher.database.QDatasetColumn.datasetColumn;
import static nl.idgis.publisher.database.QDatasetStatus.datasetStatus;
import static nl.idgis.publisher.database.QHarvestJob.harvestJob;
import static nl.idgis.publisher.database.QImportJob.importJob;
import static nl.idgis.publisher.database.QImportJobColumn.importJobColumn;
import static nl.idgis.publisher.database.QJob.job;
import static nl.idgis.publisher.database.QJobLog.jobLog;
import static nl.idgis.publisher.database.QJobState.jobState;
import static nl.idgis.publisher.database.QLastImportJob.lastImportJob;
import static nl.idgis.publisher.database.QNotification.notification;
import static nl.idgis.publisher.database.QNotificationResult.notificationResult;
import static nl.idgis.publisher.database.QServiceJob.serviceJob;
import static nl.idgis.publisher.database.QSourceDataset.sourceDataset;
import static nl.idgis.publisher.database.QSourceDatasetVersion.sourceDatasetVersion;
import static nl.idgis.publisher.database.QSourceDatasetVersionColumn.sourceDatasetVersionColumn;
import static nl.idgis.publisher.database.QVersion.version;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import nl.idgis.publisher.database.messages.AddNotification;
import nl.idgis.publisher.database.messages.AddNotificationResult;
import nl.idgis.publisher.database.messages.AlreadyRegistered;
import nl.idgis.publisher.database.messages.BaseDatasetInfo;
import nl.idgis.publisher.database.messages.CreateDataset;
import nl.idgis.publisher.database.messages.CreateHarvestJob;
import nl.idgis.publisher.database.messages.CreateImportJob;
import nl.idgis.publisher.database.messages.CreateServiceJob;
import nl.idgis.publisher.database.messages.DataSourceStatus;
import nl.idgis.publisher.database.messages.DatasetStatusInfo;
import nl.idgis.publisher.database.messages.DeleteDataset;
import nl.idgis.publisher.database.messages.GetCategoryInfo;
import nl.idgis.publisher.database.messages.GetCategoryListInfo;
import nl.idgis.publisher.database.messages.GetDataSourceInfo;
import nl.idgis.publisher.database.messages.GetDataSourceStatus;
import nl.idgis.publisher.database.messages.GetDatasetColumns;
import nl.idgis.publisher.database.messages.GetDatasetInfo;
import nl.idgis.publisher.database.messages.GetDatasetListInfo;
import nl.idgis.publisher.database.messages.GetDatasetStatus;
import nl.idgis.publisher.database.messages.GetHarvestJobs;
import nl.idgis.publisher.database.messages.GetImportJobs;
import nl.idgis.publisher.database.messages.GetJobLog;
import nl.idgis.publisher.database.messages.GetNotifications;
import nl.idgis.publisher.database.messages.GetServiceJobs;
import nl.idgis.publisher.database.messages.GetSourceDatasetColumns;
import nl.idgis.publisher.database.messages.GetSourceDatasetInfo;
import nl.idgis.publisher.database.messages.GetSourceDatasetListInfo;
import nl.idgis.publisher.database.messages.GetVersion;
import nl.idgis.publisher.database.messages.HarvestJobInfo;
import nl.idgis.publisher.database.messages.ImportJobInfo;
import nl.idgis.publisher.database.messages.InfoList;
import nl.idgis.publisher.database.messages.JobInfo;
import nl.idgis.publisher.database.messages.ListQuery;
import nl.idgis.publisher.database.messages.QCategoryInfo;
import nl.idgis.publisher.database.messages.QDataSourceInfo;
import nl.idgis.publisher.database.messages.QDataSourceStatus;
import nl.idgis.publisher.database.messages.QDatasetStatusInfo;
import nl.idgis.publisher.database.messages.QHarvestJobInfo;
import nl.idgis.publisher.database.messages.QServiceJobInfo;
import nl.idgis.publisher.database.messages.QSourceDatasetInfo;
import nl.idgis.publisher.database.messages.QVersion;
import nl.idgis.publisher.database.messages.Query;
import nl.idgis.publisher.database.messages.RegisterSourceDataset;
import nl.idgis.publisher.database.messages.Registered;
import nl.idgis.publisher.database.messages.RemoveNotification;
import nl.idgis.publisher.database.messages.ServiceJobInfo;
import nl.idgis.publisher.database.messages.SourceDatasetInfo;
import nl.idgis.publisher.database.messages.StoreLog;
import nl.idgis.publisher.database.messages.StoredJobLog;
import nl.idgis.publisher.database.messages.StoredNotification;
import nl.idgis.publisher.database.messages.TerminateJobs;
import nl.idgis.publisher.database.messages.UpdateDataset;
import nl.idgis.publisher.database.messages.UpdateJobState;
import nl.idgis.publisher.database.messages.Updated;
import nl.idgis.publisher.database.projections.QColumn;
import nl.idgis.publisher.domain.MessageProperties;
import nl.idgis.publisher.domain.MessageType;
import nl.idgis.publisher.domain.MessageTypeUtils;
import nl.idgis.publisher.domain.job.ConfirmNotificationResult;
import nl.idgis.publisher.domain.job.JobLog;
import nl.idgis.publisher.domain.job.JobState;
import nl.idgis.publisher.domain.job.JobType;
import nl.idgis.publisher.domain.job.LogLevel;
import nl.idgis.publisher.domain.job.Notification;
import nl.idgis.publisher.domain.job.NotificationResult;
import nl.idgis.publisher.domain.job.NotificationType;
import nl.idgis.publisher.domain.job.load.ImportNotificationType;
import nl.idgis.publisher.domain.response.Response;
import nl.idgis.publisher.domain.service.Column;
import nl.idgis.publisher.domain.service.CrudOperation;
import nl.idgis.publisher.domain.service.CrudResponse;
import nl.idgis.publisher.domain.service.Dataset;
import nl.idgis.publisher.domain.service.Table;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysema.query.Tuple;
import com.mysema.query.sql.SQLQuery;
import com.mysema.query.sql.SQLSubQuery;
import com.mysema.query.types.Expression;
import com.mysema.query.types.Order;
import com.mysema.query.types.expr.BooleanExpression;
import com.mysema.query.types.expr.ComparableExpressionBase;
import com.typesafe.config.Config;

public class PublisherTransaction extends QueryDSLTransaction {
	
	private final QSourceDatasetVersion sourceDatasetVersionSub = new QSourceDatasetVersion("source_dataset_version_sub");
	
	private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	
	public PublisherTransaction(Config config, Connection connection) {
		super(config, connection);
	}
	
	private void insertSourceDatasetColumns(QueryDSLContext context, int versionId, List<Column> columns) {
		int i = 0;
		for(Column column : columns) {			
			context.insert(sourceDatasetVersionColumn)
				.set(sourceDatasetVersionColumn.sourceDatasetVersionId, versionId)
				.set(sourceDatasetVersionColumn.index, i++)
				.set(sourceDatasetVersionColumn.name, column.getName())
				.set(sourceDatasetVersionColumn.dataType, column.getDataType().toString())
				.execute();
		}
	}
	
	private void insertDatasetColumns(QueryDSLContext context, int datasetId, List<Column> columns) {
		int i = 0;
		for(Column column : columns) {			
			context.insert(datasetColumn)
				.set(datasetColumn.datasetId, datasetId)
				.set(datasetColumn.index, i++)
				.set(datasetColumn.name, column.getName())
				.set(datasetColumn.dataType, column.getDataType().toString())
				.execute();
		}
	}
	
	private int getCategoryId(QueryDSLContext context, String identification) {
		Integer id = context.query().from(category)
			.where(category.identification.eq(identification))
			.singleResult(category.id);
		
		if(id == null) {
			context.insert(category)
				.set(category.identification, identification)
				.set(category.name, identification)
				.execute();
			
			return getCategoryId(context, identification);
		} else {
			return id;
		}
	}
	
	private <T extends Comparable<? super T>> SQLQuery applyListParams(SQLQuery query, ListQuery listQuery, ComparableExpressionBase<T> orderBy) {
		Order order = listQuery.getOrder();
		Long limit = listQuery.getLimit();
		Long offset = listQuery.getOffset();
		
		if(order != null) {
			if(order == Order.ASC) {
				query = query.orderBy(orderBy.asc());
			} else {
				query = query.orderBy(orderBy.desc());
			}
		}
		
		if(limit != null) {
			query = query.limit(limit);
		}
		
		if(offset != null) {
			query = query.offset(offset);
		}
		
		return query;
	}
	
	@Override
	protected void executeQuery(QueryDSLContext context, Query query) throws Exception {
		if(query instanceof GetVersion) {
			executeGetVersion(context);
		} else if(query instanceof RegisterSourceDataset) {
			executeRegisterSourceDataset(context, (RegisterSourceDataset)query);
		} else if(query instanceof GetCategoryListInfo) {
			executeGetCategoryListInfo(context);
		} else if(query instanceof GetCategoryInfo) {
			executeGetCategoryInfo(context, (GetCategoryInfo)query);			
		} else if(query instanceof GetDatasetListInfo) {
			executeGetDatasetListInfo(context, (GetDatasetListInfo)query);			
		} else if(query instanceof GetDataSourceInfo) {
			executeGetDataSourceInfo(context);
		} else if(query instanceof GetSourceDatasetInfo) {			
			executeGetSourceDatasetInfo(context, (GetSourceDatasetInfo)query);			
		} else if(query instanceof GetSourceDatasetListInfo) {			
			executeGetSourceDatasetListInfo(context, (GetSourceDatasetListInfo)query);			
		} else if(query instanceof StoreLog) {
			executeStoreLog(context, (StoreLog)query);
		} else if(query instanceof GetHarvestJobs){
			executeGetHarvestJobs(context);
		} else if(query instanceof GetSourceDatasetColumns) {
			executeGetSourceDatasetColumns(context, (GetSourceDatasetColumns)query);
		} else if(query instanceof GetDatasetColumns) {
			executeGetDatasetColumns(context, (GetDatasetColumns)query);
		} else if(query instanceof GetImportJobs) {				
			executeGetImportJobs(context);
		} else if(query instanceof CreateDataset) {
			executeCreateDataset(context, (CreateDataset)query);
		} else if(query instanceof GetDatasetInfo) {			
			executeGetDatasetInfo(context, (GetDatasetInfo)query);
		} else if(query instanceof UpdateDataset) {						
			executeUpdatedataset(context, (UpdateDataset)query);
		} else if(query instanceof DeleteDataset) {
			executeDeleteDataset(context, (DeleteDataset)query);
		} else if(query instanceof CreateHarvestJob) {
			executeCreateHarvestJob(context, (CreateHarvestJob)query);
		} else if(query instanceof CreateImportJob) {
			executeCreateImportJob(context, (CreateImportJob)query);
		} else if(query instanceof UpdateJobState) {
			executeUpdateJobState(context, (UpdateJobState)query);
		} else if(query instanceof GetDataSourceStatus) {
			executeGetDataSourceStatus(context);
		} else if(query instanceof GetJobLog) {
			executeGetJobLog(context, (GetJobLog)query);
		} else if(query instanceof GetServiceJobs) {
			executeGetServiceJobs(context);
		} else if(query instanceof CreateServiceJob) {
			executeCreateServiceJob(context, (CreateServiceJob)query);
		} else if(query instanceof GetDatasetStatus) {
			executeGetDatasetStatus(context, (GetDatasetStatus)query);
		} else if(query instanceof TerminateJobs) {
			executeTerminateJobs(context);
		} else if(query instanceof AddNotification) {
			executeAddNotification(context, (AddNotification)query);
		} else if(query instanceof AddNotificationResult) {
			executeAddNotificationResult(context, (AddNotificationResult)query);
		} else if(query instanceof RemoveNotification) {
			executeRemoveNotification(context, (RemoveNotification)query);
		} else if (query instanceof GetNotifications) {
			executeGetNotifications (context, (GetNotifications) query);
		} else {
			throw new IllegalArgumentException("Unknown query");
		}
	}

	private void executeRemoveNotification(QueryDSLContext context, RemoveNotification query) {	
		JobInfo job = query.getJob();
		NotificationType type = query.getNotificationType();
		
		context.delete(notificationResult)
			.where(new SQLSubQuery().from(notification)
				.where(notification.id.eq(notificationResult.notificationId)
					.and(notification.type.eq(type.name())
					.and(notification.jobId.eq(job.getId()))))
				.exists())
			.execute();
		
		context.delete(notification)
			.where(notification.type.eq(type.name())
				.and(notification.jobId.eq(job.getId())))
			.execute();
		
		context.ack();
	}

	private void executeAddNotificationResult(QueryDSLContext context, AddNotificationResult query) {
		JobInfo job = query.getJob();
		NotificationType type = query.getNotificationType();
		NotificationResult result = query.getNotificationResult();
		
		context.insert(notificationResult)
			.columns(
				notificationResult.notificationId,
				notificationResult.result)
			.select(new SQLSubQuery().from(notification)
				.where(notification.jobId.eq(job.getId())
					.and(notification.type.eq(type.name())))				
				.list(
					notification.id,
					result.name()))
			.execute();
		
		context.ack();
	}

	private void executeAddNotification(QueryDSLContext context, AddNotification query) {
		JobInfo job = query.getJob();
		NotificationType notificationType = query.getNotificationType();
		
		context.insert(notification)
			.set(notification.jobId, job.getId())
			.set(notification.type, notificationType.name())
			.execute();
		
		context.ack();
	}

	private void executeTerminateJobs(QueryDSLContext context) {
		final QJobState jobStateSub = new QJobState("job_state_sub");
		
		long result = context.insert(jobState)
			.columns(
				jobState.jobId,
				jobState.state)
			.select(new SQLSubQuery().from(job)
				.where(new SQLSubQuery().from(jobStateSub)
						.where(jobStateSub.jobId.eq(job.id)
								.and(jobStateSub.state.in(
										enumsToStrings(JobState.getFinished()))))
						.notExists())
				.list(
					job.id, 
					JobState.FAILED.name()))
			.execute();
		
		log.debug("jobs terminated: " + result);
		
		context.ack();
	}
	
	private static class DatasetInfo {
		private String sourceDatasetId;
		private Timestamp revision;		
		private List<Column> columns;
		
		DatasetInfo(String sourceDatasetId, Timestamp revision) {
			this.sourceDatasetId = sourceDatasetId;
			this.revision = revision;
			
			columns = new ArrayList<>();
		}
		
		public void addColumn(Column column) {
			columns.add(column);
		}

		public Timestamp getRevision() {
			return revision;
		}

		public List<Column> getColumns() {
			return Collections.unmodifiableList(columns);
		}

		public String getSourceDatasetId() {
			return sourceDatasetId;
		}
	}

	private void executeGetDatasetStatus(QueryDSLContext context, GetDatasetStatus query) {		
		SQLQuery baseQuery = context.query().from(datasetStatus)
			.join(dataset).on(dataset.id.eq(datasetStatus.id));
		
		Expression<DatasetStatusInfo> expression = 
				new QDatasetStatusInfo(					
						dataset.identification, 
						datasetStatus.columnsChanged, 
						datasetStatus.filterConditionChanged, 
						datasetStatus.sourceDatasetChanged, 
						datasetStatus.imported, 
						datasetStatus.serviceCreated, 
						datasetStatus.sourceDatasetColumnsChanged, 
						datasetStatus.sourceDatasetRevisionChanged);
		
		String datasourceId = query.getDatasetId();
		if(datasourceId == null) {		
			context.answer(
				DatasetStatusInfo.class,
				
				baseQuery.list(expression));
		} else {
			context.answer(
				baseQuery.where(dataset.identification.eq(datasourceId))
				.singleResult(expression));
		}
	}

	private Map<String, DatasetInfo> readDatasetInfo(SQLQuery query) {
		Map<String, DatasetInfo> datasetInfos = new HashMap<>();
		
		String lastId = null;
		DatasetInfo currentDatasetInfo = null;
		for(Tuple t : 
			query.list(				
				dataset.identification,
				sourceDataset.identification,
				sourceDatasetVersion.revision,
				sourceDatasetVersionColumn.name,
				sourceDatasetVersionColumn.dataType)) {
			
			String currentDatasetId = t.get(dataset.identification);
			if(!currentDatasetId.equals(lastId)) {
				if(currentDatasetInfo != null) {
					datasetInfos.put(lastId, currentDatasetInfo);
				}
				
				lastId = currentDatasetId;
				
				currentDatasetInfo = new DatasetInfo(
						t.get(sourceDataset.identification),
						t.get(sourceDatasetVersion.revision));				
			}
			
			currentDatasetInfo.addColumn(
				new Column(
					t.get(sourceDatasetVersionColumn.name),
					t.get(sourceDatasetVersionColumn.dataType)));
		}
		
		if(currentDatasetInfo != null) {
			datasetInfos.put(lastId, currentDatasetInfo);
		}
		
		return datasetInfos;
	}

	private void executeCreateServiceJob(QueryDSLContext context, CreateServiceJob query) {
		log.debug("creating service job: " + query);
		
		if(context.query().from(job)
			.join(serviceJob).on(serviceJob.jobId.eq(job.id))
			.join(dataset).on(dataset.id.eq(serviceJob.datasetId))
			.where(dataset.identification.eq(query.getDatasetId()))
			.where(new SQLSubQuery().from(jobState)
					.where(jobState.jobId.eq(job.id))
					.where(isFinished(jobState))
					.notExists())
			.notExists()) {
			
			int jobId = context.insert(job)
					.set(job.type, "SERVICE")
					.executeWithKey(job.id);
			
			int datasetId = getDatasetId(context, query.getDatasetId());
			
			int versionId = getLastVersionId(context, query.getDatasetId());
			
			context.insert(serviceJob)
				.set(serviceJob.jobId, jobId)
				.set(serviceJob.datasetId, datasetId)
				.set(serviceJob.sourceDatasetVersionId, versionId)
				.execute();
			
			log.debug("service job created");
		} else {
			log.debug("already exist an service job for this dataset");
		}		
				
		context.ack();
	}

	private Integer getLastVersionId(QueryDSLContext context,
			String datasetIdentification) {
		return context.query().from(sourceDatasetVersion)
				.join(sourceDataset).on(sourceDataset.id.eq(sourceDatasetVersion.sourceDatasetId))
				.join(dataset).on(dataset.sourceDatasetId.eq(sourceDataset.id))
				.where(dataset.identification.eq(datasetIdentification))
				.singleResult(sourceDatasetVersion.id.max());
	}

	private Integer getDatasetId(QueryDSLContext context,
			String datasetIdentification) {
		return context.query().from(dataset)
			.where(dataset.identification.eq(datasetIdentification))
			.singleResult(dataset.id);
	}

	private void executeGetServiceJobs(QueryDSLContext context) {
		context.answer(
			context.query().from(serviceJob)
				.join(dataset).on(dataset.id.eq(serviceJob.datasetId))
				.join(sourceDatasetVersion).on(sourceDatasetVersion.id.eq(serviceJob.sourceDatasetVersionId))
				.join(category).on(category.id.eq(sourceDatasetVersion.categoryId))
				.where(new SQLSubQuery().from(jobState)
						.where(jobState.jobId.eq(serviceJob.jobId))
						.notExists())
				.list(new QServiceJobInfo(
						serviceJob.jobId, 
						category.identification, 
						dataset.identification)));
			
	}

	private void executeGetJobLog(QueryDSLContext context, GetJobLog query) throws Exception {
		final SQLQuery baseQuery = context.query().from(jobLog)
				.join(jobState).on(jobState.id.eq(jobLog.jobStateId))
				.join(job).on(job.id.eq(jobState.jobId));
		
		if (query.getLogLevels () != null && !query.getLogLevels ().isEmpty ()) {
			final List<String> logLevelStrings = new ArrayList<> (query.getLogLevels ().size ());
			for (final LogLevel ll: query.getLogLevels ()) {
				logLevelStrings.add (ll.name ());
			}
			
			baseQuery.where (jobLog.level.in (logLevelStrings));
		}
		
		if (query.getSince () != null) {
			baseQuery.where (jobLog.createTime.gt (query.getSince ()));
		}
		
		List<StoredJobLog> jobLogs = new ArrayList<>();
		for(Tuple t : 
			applyListParams(baseQuery.clone (), query, jobLog.createTime)
				.list(
					job.id, 
					job.type,
					jobLog.level, 
					jobLog.type, 
					jobLog.content,
					jobLog.createTime)) {
			
			JobType jobType = JobType.valueOf(t.get(job.type));
			
			JobInfo jobInfo = new JobInfo(
					t.get(job.id), jobType);
			
			LogLevel logLevel = LogLevel.valueOf(t.get(jobLog.level));
			
			Class<? extends MessageType<?>> logTypeClass = jobType.getLogMessageEnum ();
			MessageType<?> logType = MessageTypeUtils.valueOf(logTypeClass, t.get(jobLog.type));
			
			String content = t.get(jobLog.content);
			
			MessageProperties contentObject;
			if(content == null) {
				contentObject = null;
			} else {
				contentObject = fromJson(logType.getContentClass(), content); 
			}			
			
			final Timestamp when = t.get (jobLog.createTime);
			
			jobLogs.add(new StoredJobLog(jobInfo, logLevel, logType, when, contentObject));
		}
		
		context.answer(new InfoList<StoredJobLog> (jobLogs, baseQuery.count ()));
	}
	
	private void executeGetNotifications (final QueryDSLContext context, final GetNotifications query) throws Exception {
		
		final SQLQuery baseQuery = context.query ().from (datasetActiveNotification);
		
		if (!query.isIncludeRejected ()) {
			baseQuery.where (datasetActiveNotification.notificationResult.ne (ConfirmNotificationResult.NOT_OK.name ()));
		}
		
		if (query.getSince () != null) {
			baseQuery.where (datasetActiveNotification.jobCreateTime.gt (query.getSince ()));
		}
		
		final List<StoredNotification> notifications = new ArrayList<> ();
		
		for (final Tuple t: applyListParams (baseQuery.clone (), query, datasetActiveNotification.jobCreateTime)
				.list (
					datasetActiveNotification.notificationId,
					datasetActiveNotification.notificationType,
					datasetActiveNotification.notificationResult,
					datasetActiveNotification.jobId,
					datasetActiveNotification.jobType,
					datasetActiveNotification.datasetId,
					datasetActiveNotification.datasetName
				)) {
			notifications.add (new StoredNotification (
					t.get (datasetActiveNotification.notificationId), 
					ImportNotificationType.valueOf (t.get (datasetActiveNotification.notificationType)),
					ConfirmNotificationResult.valueOf (t.get (datasetActiveNotification.notificationResult)),
					new JobInfo (
						 t.get (datasetActiveNotification.jobId),
						JobType.valueOf (t.get (datasetActiveNotification.jobType))
					), 
					new BaseDatasetInfo (
						t.get (datasetActiveNotification.datasetIdentification), 
						t.get (dataset.name)
					)
				));
		}
		
		context.answer (new InfoList<StoredNotification> (notifications, baseQuery.count ()));
						
	}
	
	private <T, U> Map<T, List<U>> treeFold(SQLQuery query, Expression<T> id, Expression<U> value) {
		Map<T, List<U>> retval = new HashMap<>();
		
		T lastId = null;
		List<U> currentValues = null;
		for(Tuple t : 
			query
				.list(
					id,
					value)) {
			
			T currentId = t.get(id);
			if(!currentId.equals(lastId)) {
				if(currentValues != null) {
					retval.put(lastId, currentValues);
				}
				
				lastId = currentId;
				currentValues = new ArrayList<>();
			}
			
			currentValues.add(t.get(value));
		}
		
		if(currentValues != null) {
			retval.put(lastId, currentValues);
		}
		
		return retval;
	}

	private void executeGetDataSourceStatus(QueryDSLContext context) {
		QJobState jobStateSub = new QJobState("job_state_sub");			
		QHarvestJob harvestJobSub = new QHarvestJob("harvest_job_sub");			
		
		context.answer(
			DataSourceStatus.class,
				
			context.query().from(jobState)
				.join(harvestJob).on(harvestJob.jobId.eq(jobState.jobId))
				.rightJoin(dataSource).on(dataSource.id.eq(harvestJob.dataSourceId))
				.where(isFinished(jobState))
				.where(new SQLSubQuery().from(jobStateSub)
					.join(harvestJobSub).on(harvestJobSub.jobId.eq(jobStateSub.jobId))
					.where(jobStateSub.createTime.after(jobState.createTime))
					.notExists())
				.list(new QDataSourceStatus(
						dataSource.identification, 
						jobState.createTime, 
						jobState.state)));
	}

	private BooleanExpression isFinished(QJobState jobState) {
		return jobState.state.isNull().or(jobState.state.in(enumsToStrings(JobState.getFinished())));
	}

	private void executeUpdateJobState(QueryDSLContext context, UpdateJobState query) {
		log.debug("updating job state: " + query);
		
		int jobId = getJobQuery(context,  query.getJob())
				.singleResult(job.id);
		
		context.insert(jobState)
			.set(jobState.jobId, jobId)
			.set(jobState.state, query.getState().name())
			.execute();
		
		context.ack();
	}

	private SQLQuery getJobQuery(QueryDSLContext context, JobInfo job) {		
		if(job instanceof ImportJobInfo) {
			return getJobQuery(context, (ImportJobInfo)job);
		} else if(job instanceof HarvestJobInfo) {
			return getJobQuery(context, (HarvestJobInfo)job);
		} else if(job instanceof ServiceJobInfo) {
			return getJobQuery(context, (ServiceJobInfo)job);		
		} else {
			throw new IllegalArgumentException("unknown job type");
		}
	}	
	
	private <T extends Collection<? extends Enum<?>>> Collection<String> enumsToStrings(final T enums) {
		return new AbstractCollection<String>() {

			@Override
			public Iterator<String> iterator() {
				final Iterator<? extends Enum<?>> enumIterator = enums.iterator();
				
				return new Iterator<String>() {

					@Override
					public boolean hasNext() {
						return enumIterator.hasNext();
					}

					@Override
					public String next() {
						return enumIterator.next().name();
					}

					@Override
					public void remove() {
						enumIterator.remove();
					}					
				};	
			}

			@Override
			public int size() {				
				return enums.size();
			}
		};
	}

	private SQLQuery getJobQuery(QueryDSLContext context, ImportJobInfo ij) {
		
		
		return context.query().from(job)
				.join(importJob).on(importJob.jobId.eq(job.id))
				.join(dataset).on(dataset.id.eq(importJob.datasetId))
				.where(dataset.identification.eq(ij.getDatasetId()))
				.where(unfinishedState());
	}

	private BooleanExpression unfinishedState() {
		QJobState jobStateSub = new QJobState("job_state_sub");
		
		return new SQLSubQuery().from(jobStateSub)
				.where(jobStateSub.jobId.eq(job.id)
					.and(isFinished(jobStateSub)))
				.notExists();
	}
	
	private SQLQuery getJobQuery(QueryDSLContext context, ServiceJobInfo sj) {
		return context.query().from(job)
				.join(serviceJob).on(serviceJob.jobId.eq(job.id))
				.join(dataset).on(dataset.id.eq(serviceJob.datasetId))
				.join(sourceDataset).on(sourceDataset.id.eq(dataset.sourceDatasetId))
				.join(sourceDatasetVersion).on(sourceDatasetVersion.id.eq(serviceJob.sourceDatasetVersionId))
				.join(category).on(category.id.eq(sourceDatasetVersion.categoryId))
				.where(dataset.identification.eq(sj.getTableName()))
				.where(category.identification.eq(sj.getSchemaName()))
				.where(unfinishedState());
	}

	private SQLQuery getJobQuery(QueryDSLContext context, HarvestJobInfo hj) {
		return context.query().from(job)
				.join(harvestJob).on(harvestJob.jobId.eq(job.id))
				.join(dataSource).on(dataSource.id.eq(harvestJob.dataSourceId))
				.where(dataSource.identification.eq(hj.getDataSourceId()))
				.where(unfinishedState());
	}

	private void executeCreateImportJob(QueryDSLContext context, CreateImportJob query) {
		log.debug("creating import job: " + query);
		
		if(context.query().from(job)
			.join(importJob).on(importJob.jobId.eq(job.id))
			.join(dataset).on(dataset.id.eq(importJob.datasetId))
			.where(dataset.identification.eq(query.getDatasetId()))
			.where(new SQLSubQuery().from(jobState)
					.where(jobState.jobId.eq(job.id))
					.where(isFinished(jobState))
					.notExists())
			.notExists()) {
			
			int jobId = context.insert(job)
					.set(job.type, "IMPORT")
					.executeWithKey(job.id);
			
			int versionId = getLastVersionId(context, query.getDatasetId());
			
			int importJobId = 
				context.insert(importJob)
					.columns(
						importJob.jobId,
						importJob.datasetId,
						importJob.sourceDatasetVersionId,
						importJob.filterConditions)
					.select(new SQLSubQuery().from(dataset)
							.where(dataset.identification.eq(query.getDatasetId()))
							.list(
								jobId,
								dataset.id,
								versionId,
								dataset.filterConditions))
					.executeWithKey(importJob.id);
			
				context.insert(importJobColumn)
					.columns(
						importJobColumn.importJobId,
						importJobColumn.index,
						importJobColumn.name,
						importJobColumn.dataType)
					.select(new SQLSubQuery().from(datasetColumn)
						.join(dataset).on(dataset.id.eq(datasetColumn.datasetId))
						.where(dataset.identification.eq(query.getDatasetId()))
						.list(
							importJobId,
							datasetColumn.index,
							datasetColumn.name,
							datasetColumn.dataType))
					.execute();
			
			log.debug("import job created");
		} else {
			log.debug("already exist an import job for this dataset");
		}
		
		context.ack();
	}

	private void executeCreateHarvestJob(QueryDSLContext context, CreateHarvestJob query) {
		log.debug("creating harvest job: " + query);
		
		if(context.query().from(job)
			.join(harvestJob).on(harvestJob.jobId.eq(job.id))
			.join(dataSource).on(dataSource.id.eq(harvestJob.dataSourceId))
			.where(dataSource.identification.eq(query.getDataSourceId()))
			.where(new SQLSubQuery().from(jobState)
					.where(jobState.jobId.eq(job.id))
					.where(isFinished(jobState))
					.notExists())
			.notExists()) {
			
			int jobId = context.insert(job)
				.set(job.type, "HARVEST")
				.executeWithKey(job.id);
			
			int dataSourceId = context.query().from(dataSource)
				.where(dataSource.identification.eq(query.getDataSourceId()))
				.singleResult(dataSource.id);
			
			context.insert(harvestJob)
				.set(harvestJob.jobId, jobId)				
				.set(harvestJob.dataSourceId, dataSourceId)
				.execute();
			
			log.debug("harvest job created");
		} else {
			log.debug("already exist a harvest job for this dataSource");
		}
		
		context.ack();
	}

	private void executeDeleteDataset(QueryDSLContext context, DeleteDataset dds) {
		Long nrOfDatasetColumnsDeleted = context.delete(datasetColumn)
				.where(
					new SQLSubQuery().from(dataset)
					.where(dataset.identification.eq(dds.getId())
					.and(dataset.id.eq(datasetColumn.datasetId))).exists())
				.execute();
		log.debug("nrOfDatasetColumnsDeleted: " + nrOfDatasetColumnsDeleted);
		
		Long nrOfDatasetsDeleted = context.delete(dataset)
			.where(dataset.identification.eq(dds.getId()))
			.execute();
		log.debug("nrOfDatasetsDeleted: " + nrOfDatasetsDeleted);
		
		if (nrOfDatasetsDeleted > 0 || nrOfDatasetColumnsDeleted >= 0){
			context.answer(new Response<Long>(CrudOperation.DELETE, CrudResponse.OK, nrOfDatasetColumnsDeleted));
		} else {
			context.answer(new Response<String>(CrudOperation.DELETE, CrudResponse.NOK, dds.getId()));
		}
	}

	private void executeUpdatedataset(QueryDSLContext context, UpdateDataset uds) {
		String sourceDatasetIdent = uds.getSourceDatasetIdentification();
		String datasetIdent = uds.getDatasetIdentification();
		String datasetName = uds.getDatasetName();
		final String filterConditions = uds.getFilterConditions ();
		log.debug("update dataset" + datasetIdent);
		
		Integer sourceDatasetId = context.query().from(sourceDataset)
				.where(sourceDataset.identification.eq(sourceDatasetIdent))
				.singleResult(sourceDataset.id);

		context.update(dataset)
			.set(dataset.name, datasetName)
			.set(dataset.sourceDatasetId, sourceDatasetId)
			.set(dataset.filterConditions, filterConditions)
			.where(dataset.identification.eq(datasetIdent))
			.execute();
			
		Integer datasetId = getDatasetId(context, datasetIdent);
		
		context.delete(datasetColumn)
			.where(datasetColumn.datasetId.eq(datasetId))
			.execute();
		
		insertDatasetColumns(context, datasetId, uds.getColumnList());
		context.answer(new Response<String>(CrudOperation.UPDATE, CrudResponse.OK, datasetIdent));
		
		log.debug("dataset updated");
	}

	private void executeGetDatasetInfo(QueryDSLContext context, GetDatasetInfo gds) {
		String datasetIdent = gds.getId();
		log.debug("get dataset " + datasetIdent);

		final SQLQuery query = context.query().from(dataset)
			.join (sourceDataset).on(dataset.sourceDatasetId.eq(sourceDataset.id))
			.join(sourceDatasetVersion).on(
				sourceDatasetVersion.sourceDatasetId.eq(dataset.sourceDatasetId)
				.and(new SQLSubQuery().from(sourceDatasetVersionSub)
						.where(sourceDatasetVersionSub.sourceDatasetId.eq(sourceDatasetVersion.sourceDatasetId)
							.and(sourceDatasetVersionSub.id.gt(sourceDatasetVersion.id)))
						.notExists()))
			.leftJoin (category).on(sourceDatasetVersion.categoryId.eq(category.id))
			.leftJoin (datasetStatus).on (datasetStatus.id.eq (dataset.id))
			.leftJoin (lastImportJob).on (lastImportJob.datasetId.eq (dataset.id))
			.leftJoin (datasetActiveNotification).on (datasetActiveNotification.datasetId.eq (dataset.id))
			.where(dataset.identification.eq( datasetIdent ))
			.orderBy (datasetActiveNotification.jobCreateTime.desc ());
		
		final List<StoredNotification> notifications = new ArrayList<> ();
		Tuple lastTuple = null;
		
		for (final Tuple t: query.list (
				dataset.identification, 
				dataset.name, 
				sourceDataset.identification, 
				sourceDatasetVersion.name,
				category.identification,
				category.name, 
				dataset.filterConditions,
				datasetStatus.imported,
				datasetStatus.serviceCreated,
				datasetStatus.sourceDatasetColumnsChanged,
				lastImportJob.finishTime,
				lastImportJob.finishState,
				datasetActiveNotification.notificationId,
				datasetActiveNotification.notificationType,
				datasetActiveNotification.notificationResult,
				datasetActiveNotification.jobId,
				datasetActiveNotification.jobType
			)) {
			
			if (t.get (datasetActiveNotification.notificationId) != null) {
				notifications.add (createStoredNotification (t));
			}
			
			lastTuple = t;
		}
		
		if (lastTuple == null) {
			context.answer ((Object) null);
		}
		
		context.answer (createDatasetInfo (lastTuple, notifications));
	}

	private void executeCreateDataset(QueryDSLContext context, CreateDataset cds) {
		String sourceDatasetIdent = cds.getSourceDatasetIdentification();
		String datasetIdent = cds.getDatasetIdentification();
		String datasetName = cds.getDatasetName();
		final String filterConditions = cds.getFilterConditions ();
		log.debug("create dataset " + datasetIdent);

		Integer sourceDatasetId = context.query().from(sourceDataset)
				.where(sourceDataset.identification.eq(sourceDatasetIdent))
				.singleResult(sourceDataset.id);
			if(sourceDatasetId == null) {
				log.error("sourceDataset not found: " + sourceDatasetIdent);
				context.answer(new Response<String>(CrudOperation.CREATE, CrudResponse.NOK, datasetIdent));
			} else {
				context.insert(dataset)
					.set(dataset.identification, datasetIdent)
					.set(dataset.name, datasetName)
					.set(dataset.sourceDatasetId, sourceDatasetId)
					.set(dataset.filterConditions, filterConditions)
					.execute();
				
				Integer datasetId = getDatasetId(context, datasetIdent);
				
				insertDatasetColumns(context, datasetId, cds.getColumnList());					
				context.answer(new Response<String>(CrudOperation.CREATE, CrudResponse.OK, datasetIdent));
				
				log.debug("dataset inserted");
			}
	}

	private void executeGetImportJobs(QueryDSLContext context) {
		SQLQuery query = context.query().from(job)
			.join(importJob).on(importJob.jobId.eq(job.id))			
			.join(dataset).on(dataset.id.eq(importJob.datasetId))
			.join(sourceDatasetVersion).on(sourceDatasetVersion.id.eq(importJob.sourceDatasetVersionId))
			.join(sourceDataset).on(sourceDataset.id.eq(sourceDatasetVersion.sourceDatasetId))
			.join(category).on(category.id.eq(sourceDatasetVersion.categoryId))
			.join(dataSource).on(dataSource.id.eq(sourceDataset.dataSourceId))
			.orderBy(job.id.asc(), job.createTime.asc())
			.where(new SQLSubQuery().from(jobState)
					.where(jobState.jobId.eq(job.id))
					.notExists());
		
		List<Tuple> baseList = query.clone()			
			.list(
					job.id,
					importJob.filterConditions,
					category.identification,
					dataSource.identification,
					sourceDataset.identification,
					dataset.id,
					dataset.identification);
		
		List<Tuple> columnList = 
			query.clone()
				.join(importJobColumn).on(importJobColumn.importJobId.eq(importJob.id))							
				.orderBy(importJobColumn.index.asc())
				.list(job.id, importJobColumn.name, importJobColumn.dataType);
		
		List<Tuple> notificationList =
				query.clone()
					.join(notification).on(notification.jobId.eq(job.id))
					.leftJoin(notificationResult).on(notificationResult.notificationId.eq(notification.id))
					.list(job.id, notification.type, notificationResult.result);
		
		ListIterator<Tuple> columnIterator = columnList.listIterator();
		ListIterator<Tuple> notificationIterator = notificationList.listIterator();
		ArrayList<ImportJobInfo> jobs = new ArrayList<>();
		for(Tuple t : baseList) {
			int jobId = t.get(job.id);
			
			List<Column> columns = new ArrayList<>();
			for(; columnIterator.hasNext();) {
				Tuple tc = columnIterator.next();
				
				int columnJobId = tc.get(job.id);				
				if(columnJobId != jobId) {
					columnIterator.previous();
					break;
				}
				
				columns.add(new Column(
						tc.get(importJobColumn.name), 
						tc.get(importJobColumn.dataType)));
			}
			
			List<Notification> notifications = new ArrayList<>();
			for(; notificationIterator.hasNext();) {
				Tuple tn = notificationIterator.next();
				
				int notificationJobId = tn.get(job.id);				
				if(notificationJobId != jobId) {
					notificationIterator.previous();
					break;
				}
				
				ImportNotificationType notificationType = ImportNotificationType.valueOf(tn.get(notification.type));
				
				NotificationResult result;
				String resultName = tn.get(notificationResult.result);
				if(resultName == null) {
					result = null;
				} else {
					result = notificationType.getResult(resultName);
				}
				
				notifications.add(new Notification(notificationType, result));
			}
			
			jobs.add(new ImportJobInfo(
					t.get(job.id),
					t.get(category.identification),
					t.get(dataSource.identification), 
					t.get(sourceDataset.identification),
					t.get(dataset.identification),
					t.get(importJob.filterConditions),
					columns,
					notifications));
		}
		
		context.answer(jobs);
	}

	private void executeGetDatasetColumns(QueryDSLContext context, GetDatasetColumns dc) {		
		log.debug("get columns for dataset: " + dc.getDatasetId());
		
		context.answer(
			context.query().from(datasetColumn)
			.join(dataset).on(dataset.id.eq(datasetColumn.datasetId))
			.where(dataset.identification.eq(dc.getDatasetId()))
			.list(new QColumn(datasetColumn.name, datasetColumn.dataType)));
	}

	private void executeGetSourceDatasetColumns(QueryDSLContext context, GetSourceDatasetColumns sdc) {
		log.debug("get columns for sourcedataset: " + sdc.getSourceDatasetId());

		context.answer(
			context.query().from(sourceDatasetVersionColumn)
			.join(sourceDatasetVersion).on(sourceDatasetVersion.id.eq(sourceDatasetVersionColumn.sourceDatasetVersionId)
					.and(new SQLSubQuery().from(sourceDatasetVersionSub)
							.where(sourceDatasetVersionSub.sourceDatasetId.eq(sourceDatasetVersion.sourceDatasetId)
								.and(sourceDatasetVersionSub.id.gt(sourceDatasetVersion.id)))
							.notExists()))
			.join(sourceDataset).on(sourceDataset.id.eq(sourceDatasetVersion.sourceDatasetId))
			.join(dataSource).on(dataSource.id.eq(sourceDataset.dataSourceId))
			.where(sourceDataset.identification.eq(sdc.getSourceDatasetId())
				.and(dataSource.identification.eq(sdc.getDataSourceId())))
			.list(new QColumn(sourceDatasetVersionColumn.name, sourceDatasetVersionColumn.dataType)));
	}

	private void executeGetHarvestJobs(QueryDSLContext context) {
		context.answer(
			context.query().from(job)
				.join(harvestJob).on(harvestJob.jobId.eq(job.id))
				.join(dataSource).on(dataSource.id.eq(harvestJob.dataSourceId))
				.orderBy(job.createTime.asc())
				.where(new SQLSubQuery().from(jobState)
						.where(jobState.jobId.eq(job.id))
						.notExists())
				.list(new QHarvestJobInfo(job.id, dataSource.identification)));
	}

	private void executeStoreLog(QueryDSLContext context, StoreLog query) throws Exception {
		log.debug("storing log line: " + query);
		
		int jobStateId = getJobQuery(context, query.getJob())
			.join(jobState).on(jobState.jobId.eq(job.id))
			.orderBy(jobState.id.desc())
			.limit(1)
			.singleResult(jobState.id);
		
		JobLog jl = query.getJobLog();
		
		context.insert(jobLog)
			.set(jobLog.jobStateId, jobStateId)
			.set(jobLog.level, jl.getLevel().name())
			.set(jobLog.type, jl.getType().name())
			.set(jobLog.content, toJson(jl.getContent()))
			.execute();
		
		context.ack();
	}
	
	private <T> T fromJson(Class<T> clazz, String json) throws JsonProcessingException, IOException {
		ObjectMapper om = new ObjectMapper();
		return om.reader(clazz).readValue(json);
	}

	private String toJson(Object content) throws JsonProcessingException {
		ObjectMapper om = new ObjectMapper();
		om.setSerializationInclusion(Include.NON_NULL);
		return om.writeValueAsString(content);
	}

	private void executeGetSourceDatasetListInfo(QueryDSLContext context,
			GetSourceDatasetListInfo sdi) {
		log.debug(sdi.toString());
		
		String categoryId = sdi.getCategoryId();
		String dataSourceId = sdi.getDataSourceId();
		String searchStr = sdi.getSearchString();
		
		SQLQuery baseQuery = context.query().from(sourceDataset)
				.join (sourceDatasetVersion).on(sourceDatasetVersion.sourceDatasetId.eq(sourceDataset.id)
					.and(new SQLSubQuery().from(sourceDatasetVersionSub)
							.where(sourceDatasetVersionSub.sourceDatasetId.eq(sourceDatasetVersion.sourceDatasetId)
									.and(sourceDatasetVersionSub.id.gt(sourceDatasetVersion.id)))
								.notExists()))
				.join (dataSource).on(dataSource.id.eq(sourceDataset.dataSourceId))
				.join (category).on(sourceDatasetVersion.categoryId.eq(category.id));
		
		if(categoryId != null) {				
			baseQuery.where(category.identification.eq(categoryId));
		}
		
		if(dataSourceId != null) {				
			baseQuery.where(dataSource.identification.eq(dataSourceId));
		}
		
		if (!(searchStr == null || searchStr.isEmpty())){
			baseQuery.where(sourceDatasetVersion.name.containsIgnoreCase(searchStr)); 				
		}
			
		SQLQuery listQuery = baseQuery.clone()					
				.leftJoin(dataset).on(dataset.sourceDatasetId.eq(sourceDataset.id));
		
		applyListParams(listQuery, sdi, sourceDatasetVersion.name);
		
		context.answer(
			new InfoList<SourceDatasetInfo>(			
				listQuery					
					.groupBy(sourceDataset.identification).groupBy(sourceDatasetVersion.name)
					.groupBy(dataSource.identification).groupBy(dataSource.name)
					.groupBy(category.identification).groupBy(category.name)						
					.list(new QSourceDatasetInfo(sourceDataset.identification, sourceDatasetVersion.name, 
							dataSource.identification, dataSource.name,
							category.identification,category.name,
							dataset.count())),
							
				baseQuery.count()
			)
		);
	}

	private void executeGetSourceDatasetInfo(QueryDSLContext context,
			GetSourceDatasetInfo sdi) {
		log.debug(sdi.toString());
		String sourceDatasetId = sdi.getId();
		
		SQLQuery baseQuery = context.query().from(sourceDataset)
				.join (sourceDatasetVersion).on(sourceDatasetVersion.sourceDatasetId.eq(sourceDataset.id)
						.and(new SQLSubQuery().from(sourceDatasetVersionSub)
								.where(sourceDatasetVersionSub.sourceDatasetId.eq(sourceDatasetVersion.sourceDatasetId)
										.and(sourceDatasetVersionSub.id.gt(sourceDatasetVersion.id)))
									.notExists()))
				.join (dataSource).on(dataSource.id.eq(sourceDataset.dataSourceId))
				.join (category).on(sourceDatasetVersion.categoryId.eq(category.id));
		
		if(sourceDatasetId != null) {				
			baseQuery.where(sourceDataset.identification.eq(sourceDatasetId));
		}
			
		SQLQuery listQuery = baseQuery.clone()					
				.leftJoin(dataset).on(dataset.sourceDatasetId.eq(sourceDataset.id));
		
		context.answer(
			listQuery					
				.groupBy(sourceDataset.identification).groupBy(sourceDatasetVersion.name)
				.groupBy(dataSource.identification).groupBy(dataSource.name)
				.groupBy(category.identification).groupBy(category.name)						
				.singleResult(new QSourceDatasetInfo(sourceDataset.identification, sourceDatasetVersion.name, 
						dataSource.identification, dataSource.name,
						category.identification,category.name,
						dataset.count())
			)
		);
	}

	private void executeGetDataSourceInfo(QueryDSLContext context) {
		context.answer(
			context.query().from(dataSource)
				.orderBy(dataSource.identification.asc())
				.list(new QDataSourceInfo(dataSource.identification, dataSource.name)));
	}

	private StoredNotification createStoredNotification (final Tuple t) {
		return new StoredNotification (
				t.get (datasetActiveNotification.notificationId), 
				ImportNotificationType.valueOf (t.get (datasetActiveNotification.notificationType)), 
				ConfirmNotificationResult.valueOf (t.get (datasetActiveNotification.notificationResult)), 
				new JobInfo (
					t.get (datasetActiveNotification.jobId), 
					JobType.valueOf (t.get (datasetActiveNotification.jobType))
				), 
				new BaseDatasetInfo (
					t.get (dataset.identification), 
					t.get (dataset.name)
				)
			);
	}
	
	private nl.idgis.publisher.database.messages.DatasetInfo createDatasetInfo (final Tuple t, final List<StoredNotification> notifications) {
		return new nl.idgis.publisher.database.messages.DatasetInfo (
				t.get (dataset.identification), 
				t.get (dataset.name), 
				t.get (sourceDataset.identification), 
				t.get (sourceDatasetVersion.name),
				t.get (category.identification),
				t.get (category.name), 
				t.get (dataset.filterConditions),
				t.get (datasetStatus.imported),
				t.get (datasetStatus.serviceCreated),
				t.get (datasetStatus.sourceDatasetColumnsChanged),
				t.get (lastImportJob.finishTime),
				t.get (lastImportJob.finishState),
				notifications
			);
	}
	
	private void executeGetDatasetListInfo(QueryDSLContext context, GetDatasetListInfo dli) {
		String categoryId = dli.getCategoryId();
		
		SQLQuery baseQuery = context.query().from(dataset)
			.join (sourceDataset).on(dataset.sourceDatasetId.eq(sourceDataset.id))
			.join (sourceDatasetVersion).on(sourceDatasetVersion.sourceDatasetId.eq(sourceDataset.id)
						.and(new SQLSubQuery().from(sourceDatasetVersionSub)
								.where(sourceDatasetVersionSub.sourceDatasetId.eq(sourceDatasetVersion.sourceDatasetId)
										.and(sourceDatasetVersionSub.id.gt(sourceDatasetVersion.id)))
									.notExists()))
			.leftJoin (category).on(sourceDatasetVersion.categoryId.eq(category.id))
			.leftJoin (datasetStatus).on (dataset.id.eq (datasetStatus.id))
			.leftJoin (lastImportJob).on (dataset.id.eq (lastImportJob.datasetId))
			.leftJoin (datasetActiveNotification).on (dataset.id.eq (datasetActiveNotification.datasetId));
			
		if(categoryId != null) {
			baseQuery.where(category.identification.eq(categoryId));
		}

		baseQuery
			.orderBy (dataset.identification.asc ())
			.orderBy (datasetActiveNotification.jobCreateTime.desc ());
		
		final List<nl.idgis.publisher.database.messages.DatasetInfo> datasetInfos = new ArrayList<> ();
		String currentIdentification = null;
		final List<StoredNotification> notifications = new ArrayList<> ();
		Tuple lastTuple = null;
		
		for (final Tuple t: baseQuery.list (
				dataset.identification,
				dataset.name,
				sourceDataset.identification,
				sourceDatasetVersion.name,
				category.identification,
				category.name,
				dataset.filterConditions,
				datasetStatus.imported,
				datasetStatus.serviceCreated,
				datasetStatus.sourceDatasetColumnsChanged,
				lastImportJob.finishTime,
				lastImportJob.finishState,
				datasetActiveNotification.notificationId,
				datasetActiveNotification.notificationType,
				datasetActiveNotification.notificationResult,
				datasetActiveNotification.jobId,
				datasetActiveNotification.jobType
			)) {
		
			// Emit a new dataset info:
			final String datasetIdentification = t.get (dataset.identification);
			if (currentIdentification != null && !datasetIdentification.equals (currentIdentification)) {
				datasetInfos.add (createDatasetInfo (lastTuple, notifications));
				notifications.clear ();
			}
			
			// Store the last seen tuple:
			currentIdentification = datasetIdentification; 
			lastTuple = t;
			
			// Add a notification:
			final Integer notificationId = t.get (datasetActiveNotification.notificationId);
			if (notificationId != null) {
				notifications.add (createStoredNotification (t));
			}
		}
		
		if (currentIdentification != null) {
			datasetInfos.add (createDatasetInfo (lastTuple, notifications));
		}

		context.answer (datasetInfos);
	}

	private void executeGetCategoryInfo(QueryDSLContext context, GetCategoryInfo query) {
		context.answer(
				context.query().from(category)
				.where(category.identification.eq(query.getId()))
				.singleResult(new QCategoryInfo(category.identification,category.name)));
	}

	private void executeGetCategoryListInfo(QueryDSLContext context) {
		context.answer(
				context.query().from(category)
				.orderBy(category.identification.asc())
				.list(new QCategoryInfo(category.identification,category.name)));
	}

	private void executeRegisterSourceDataset(QueryDSLContext context, RegisterSourceDataset rsd) {
		log.debug("registering source dataset: " + rsd);
		
		Dataset dataset = rsd.getDataset();
		Timestamp revision = new Timestamp(dataset.getRevisionDate().getTime());
		Table table = dataset.getTable();
		
		final Integer versionId =
			context.query().from(sourceDatasetVersion)
				.join(sourceDataset).on(sourceDataset.id.eq(sourceDatasetVersion.sourceDatasetId))
				.join(dataSource).on(dataSource.id.eq(sourceDataset.dataSourceId))
				.where(dataSource.identification.eq(rsd.getDataSource())
					.and(sourceDataset.identification.eq(dataset.getId())))
				.singleResult(sourceDatasetVersion.id.max());
		
		Integer sourceDatasetId = null;
		if(versionId == null) { // new dataset
			sourceDatasetId = 
				context.insert(sourceDataset)
					.columns(
						sourceDataset.dataSourceId,
						sourceDataset.identification)
					.select(
						new SQLSubQuery().from(dataSource)
							.list(
								dataSource.id,
								dataset.getId()))
				.executeWithKey(sourceDataset.id);
		} else { // existing dataset
			Tuple existing = 
					context.query().from(sourceDataset)
						.join(dataSource).on(dataSource.id.eq(sourceDataset.dataSourceId))
						.join(sourceDatasetVersion).on(sourceDatasetVersion.id.eq(versionId))
						.join(category).on(category.id.eq(sourceDatasetVersion.categoryId))
						.where(dataSource.identification.eq(rsd.getDataSource())
							.and(sourceDataset.identification.eq(dataset.getId())))
						.singleResult(
							sourceDataset.id,
							sourceDatasetVersion.name,
							category.identification,
							sourceDatasetVersion.revision,
							sourceDataset.deleteTime);
			
			sourceDatasetId = existing.get(sourceDataset.id);
			
			String existingName = existing.get(sourceDatasetVersion.name);
			String existingCategoryIdentification = existing.get(category.identification);
			Timestamp existingRevision = existing.get(sourceDatasetVersion.revision);
			Timestamp existingDeleteTime = existing.get(sourceDataset.deleteTime);
			
			List<Column> existingColumns = context.query().from(sourceDatasetVersionColumn)
					.where(sourceDatasetVersionColumn.sourceDatasetVersionId.eq(versionId))
					.orderBy(sourceDatasetVersionColumn.index.asc())
					.list(new QColumn(sourceDatasetVersionColumn.name, sourceDatasetVersionColumn.dataType));
			
			if(existingName.equals(table.getName()) // still identical
					&& existingCategoryIdentification.equals(dataset.getCategoryId())
					&& existingRevision.equals(revision)
					&& existingDeleteTime == null
					&& existingColumns.equals(table.getColumns())) {
				
				context.answer(new AlreadyRegistered());
				return;
			} else {
				if(existingDeleteTime != null) { // reviving dataset
					context.update(sourceDataset)
						.setNull(sourceDataset.deleteTime)						
						.execute();
				}
			}
		}
		
		int newVersionId = 
			context.insert(sourceDatasetVersion)
				.set(sourceDatasetVersion.sourceDatasetId, sourceDatasetId)
				.set(sourceDatasetVersion.name, table.getName())
				.set(sourceDatasetVersion.categoryId, getCategoryId(context, dataset.getCategoryId()))
				.set(sourceDatasetVersion.revision, new Timestamp(dataset.getRevisionDate().getTime()))
				.executeWithKey(sourceDatasetVersion.id);
		
		insertSourceDatasetColumns(context, newVersionId, table.getColumns());
		
		if(versionId == null) {
			context.answer(new Registered());
		} else {
			context.answer(new Updated());
		}
	}

	private void executeGetVersion(QueryDSLContext context) {
		log.debug("database version requested");
		
		context.answer(
			context.query().from(version)
				.orderBy(version.id.desc())
				.limit(1)
				.singleResult(new QVersion(version.id, version.createTime)));
	}
}
