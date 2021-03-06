package nl.idgis.publisher.job.manager;

import static nl.idgis.publisher.database.QCategory.category;
import static nl.idgis.publisher.database.QDataSource.dataSource;
import static nl.idgis.publisher.database.QDataset.dataset;
import static nl.idgis.publisher.database.QDatasetColumn.datasetColumn;
import static nl.idgis.publisher.database.QHarvestJob.harvestJob;
import static nl.idgis.publisher.database.QImportJob.importJob;
import static nl.idgis.publisher.database.QImportJobColumn.importJobColumn;
import static nl.idgis.publisher.database.QJob.job;
import static nl.idgis.publisher.database.QRemoveJob.removeJob;
import static nl.idgis.publisher.database.QJobState.jobState;
import static nl.idgis.publisher.database.QLastSourceDatasetVersion.lastSourceDatasetVersion;
import static nl.idgis.publisher.database.QNotification.notification;
import static nl.idgis.publisher.database.QNotificationResult.notificationResult;
import static nl.idgis.publisher.database.QServiceJob.serviceJob;
import static nl.idgis.publisher.database.QService.service;
import static nl.idgis.publisher.database.QJobLog.jobLog;
import static nl.idgis.publisher.database.QSourceDataset.sourceDataset;
import static nl.idgis.publisher.database.QSourceDatasetVersion.sourceDatasetVersion;
import static nl.idgis.publisher.database.QSourceDatasetVersionColumn.sourceDatasetVersionColumn;
import static nl.idgis.publisher.utils.EnumUtils.enumsToStrings;
import static nl.idgis.publisher.utils.JsonUtils.toJson;
import static nl.idgis.publisher.database.DatabaseUtils.consumeList;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import nl.idgis.publisher.protocol.messages.Failure;

import nl.idgis.publisher.database.AsyncHelper;
import nl.idgis.publisher.database.AsyncSQLQuery;
import nl.idgis.publisher.database.AsyncDatabaseHelper;
import nl.idgis.publisher.database.QJobState;

import nl.idgis.publisher.domain.Log;
import nl.idgis.publisher.domain.MessageProperties;
import nl.idgis.publisher.domain.job.JobState;
import nl.idgis.publisher.domain.job.JobType;
import nl.idgis.publisher.domain.job.Notification;
import nl.idgis.publisher.domain.job.NotificationResult;
import nl.idgis.publisher.domain.job.load.ImportNotificationType;
import nl.idgis.publisher.domain.service.Column;

import nl.idgis.publisher.job.manager.messages.AddNotification;
import nl.idgis.publisher.job.manager.messages.CreateHarvestJob;
import nl.idgis.publisher.job.manager.messages.CreateImportJob;
import nl.idgis.publisher.job.manager.messages.CreateRemoveJob;
import nl.idgis.publisher.job.manager.messages.CreateServiceJob;
import nl.idgis.publisher.job.manager.messages.GetHarvestJobs;
import nl.idgis.publisher.job.manager.messages.GetImportJobs;
import nl.idgis.publisher.job.manager.messages.GetRemoveJobs;
import nl.idgis.publisher.job.manager.messages.GetServiceJobs;
import nl.idgis.publisher.job.manager.messages.HarvestJobInfo;
import nl.idgis.publisher.job.manager.messages.ImportJobInfo;
import nl.idgis.publisher.job.manager.messages.QHarvestJobInfo;
import nl.idgis.publisher.job.manager.messages.QRemoveJobInfo;
import nl.idgis.publisher.job.manager.messages.QServiceJobInfo;
import nl.idgis.publisher.job.manager.messages.RemoveJobInfo;
import nl.idgis.publisher.job.manager.messages.RemoveNotification;
import nl.idgis.publisher.job.manager.messages.ServiceJobInfo;
import nl.idgis.publisher.job.manager.messages.StoreLog;
import nl.idgis.publisher.job.manager.messages.UpdateState;
import nl.idgis.publisher.protocol.messages.Ack;
import nl.idgis.publisher.utils.FutureUtils;
import nl.idgis.publisher.utils.TypedList;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.dispatch.Mapper;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.util.Timeout;

import com.mysema.query.Tuple;
import com.mysema.query.sql.SQLSubQuery;
import com.mysema.query.types.expr.BooleanExpression;

public class JobManager extends UntypedActor {
	
	private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	
	private final ActorRef database;
	
	private final Timeout timeout = new Timeout(15, TimeUnit.SECONDS);
	
	private AsyncDatabaseHelper db;
	
	private FutureUtils f;
	
	public JobManager(ActorRef database) {
		this.database = database;
	}
	
	public static Props props(ActorRef database) {
		return Props.create(JobManager.class, database);
	}
	
	private <T> void returnToSender(CompletableFuture<T> future) {
		ActorRef sender = getSender();
		future.whenComplete((msg, t) -> {
			if(t != null) {
				sender.tell(new Failure(t), getSelf());
			} else {			
				sender.tell(msg, getSelf());
			}
		});
	}
	
	@Override
	public void preStart() throws Exception {
		f = new FutureUtils(getContext().dispatcher(), timeout);
		db = new AsyncDatabaseHelper(database, f, log);		
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		log.debug("jobs: " + msg);
		
		if(msg instanceof UpdateState) {
			returnToSender(handleUpdateState((UpdateState)msg));			
		} else if(msg instanceof StoreLog) {
			returnToSender(handleStoreLog((StoreLog)msg));
		} else if(msg instanceof AddNotification) {
			returnToSender(handleAddNotification((AddNotification)msg));
		} else if(msg instanceof RemoveNotification) {
			returnToSender(handleRemoveNotification((RemoveNotification)msg));
		} else if(msg instanceof GetImportJobs) {
			returnToSender(handleGetImportJobs());
		} else if(msg instanceof GetRemoveJobs) {
			returnToSender(handleGetRemoveJobs());
		} else if(msg instanceof GetHarvestJobs) {
			returnToSender(handleGetHarvestJobs());
		} else if(msg instanceof GetServiceJobs) {
			returnToSender(handleGetServiceJobs());			
		} else if(msg instanceof CreateHarvestJob) {
			returnToSender(handleCreateHarvestJob((CreateHarvestJob)msg));			
		} else if(msg instanceof CreateImportJob) {
			returnToSender(handleCreateImportJob((CreateImportJob)msg));			
		} else if(msg instanceof CreateServiceJob) {
			returnToSender(handleCreateServiceJob((CreateServiceJob)msg));
		} else if(msg instanceof CreateRemoveJob) {
			returnToSender(handleCreateRemoveJob((CreateRemoveJob)msg));
		} else {
			unhandled(msg);
		}
	}
	
	private CompletableFuture<TypedList<RemoveJobInfo>> handleGetRemoveJobs() {
		return 
			db.query().from(removeJob)
				.join(dataset).on(removeJob.datasetId.eq(dataset.id))
				.list(new QRemoveJobInfo(removeJob.jobId, dataset.identification));
	}

	private CompletableFuture<Ack> handleRemoveNotification(RemoveNotification msg) {
		String type = msg.getNotificationType().name();
		int jobId = msg.getJob().getId();
		
		return db.transactional(tx ->
			f.collect(					
				tx.delete(notificationResult)
				.where(new SQLSubQuery().from(notification)
					.where(notification.id.eq(notificationResult.notificationId)
						.and(notification.type.eq(type)
						.and(notification.jobId.eq(jobId))))
					.exists())
				.execute())
			.collect(
				tx.delete(notification)
				.where(notification.type.eq(type)
					.and(notification.jobId.eq(jobId)))
				.execute()).thenApply((l0, l1) -> new Ack()));
	}

	private CompletableFuture<Ack> handleAddNotification(AddNotification msg) {
		return db.insert(notification)
			.set(notification.jobId, msg.getJob().getId())
			.set(notification.type, msg.getNotificationType().name())
			.execute().thenApply(l -> new Ack());
	}

	private CompletableFuture<Ack> handleStoreLog(StoreLog msg) {
		Log log = msg.getJobLog();
		
		final String jsonContent;
		MessageProperties content = log.getContent();
		if(content == null) {
			jsonContent = null;
		} else {
			try {
				jsonContent = toJson(content);
			} catch(Throwable t) {
				this.log.error("couldn't serialize log content to json: {}", t);
				
				return f.failed(t);
			}
		}
		
		return db.transactional(tx ->
			tx.query().from(jobState)
				.where(jobState.jobId.eq(msg.getJob().getId()))
				.orderBy(jobState.id.desc())
				.singleResult(jobState.id).thenCompose(jobStateId ->					
					tx.insert(jobLog)
						.set(jobLog.jobStateId, jobStateId
							.orElseThrow(() -> new IllegalStateException("job state missing")))
						.set(jobLog.level, log.getLevel().name())
						.set(jobLog.type, log.getType().name())
						.set(jobLog.content, jsonContent)
						.execute().thenApply(l -> new Ack())));
	}

	private BooleanExpression isFinished(QJobState jobState) {
		return jobState.state.isNull().or(jobState.state.in(enumsToStrings(JobState.getFinished())));
	}
	
	private CompletableFuture<Ack> handleUpdateState(UpdateState msg) {
		log.debug("updating job state: " + msg);
		
		return 
			db.insert(jobState)
				.set(jobState.jobId, msg.getJob().getId())
				.set(jobState.state, msg.getState().name())
				.execute().thenApply(l -> new Ack());
	}
	
	private CompletableFuture<Ack> handleCreateServiceJob(CreateServiceJob msg) {
		final String serviceId = msg.getServiceId();
		
		log.debug("creating service job: {}", serviceId);
		
		return db.transactional(tx ->
			tx.query().from(job)
				.join(serviceJob).on(serviceJob.jobId.eq(job.id))
				.join(service).on(service.id.eq(serviceJob.serviceId))
				.where(service.identification.eq(serviceId))
				.where(new SQLSubQuery().from(jobState)
						.where(jobState.jobId.eq(job.id))
						.where(isFinished(jobState))
						.notExists())
				.notExists()
			
			.thenCompose(notExists -> {
				if(notExists) {
					return createServiceJob(tx, serviceId)
						.thenApply(l -> {
							log.debug("service job created");
							
							return new Ack();
						});
				} else {
					log.debug("already exist a service job for this dataset");
					return f.successful(new Ack());
				}
			}));
	}
	
	private CompletableFuture<Long> createServiceJob(final AsyncHelper tx, final String serviceId) {
		return
			createJob(tx, JobType.SERVICE).thenCompose(jobId ->
				tx.insert(serviceJob)
					.columns(
						serviceJob.jobId,
						serviceJob.serviceId)
					.select(new SQLSubQuery().from(service)
						.where(service.identification.eq(serviceId))
						.list(jobId, service.id))
					.execute());
				
	}
	
	private CompletableFuture<Long> createImportJob(final AsyncHelper tx, final String datasetId) {
		return
			createJob(tx, JobType.IMPORT).thenCompose(jobId -> 
				getSourceDatasetVersion(tx, datasetId).thenCompose(datasetVersionId ->
					tx.insert(importJob)
						.columns(
							importJob.jobId,
							importJob.datasetId,
							importJob.sourceDatasetVersionId,
							importJob.filterConditions)
						.select(new SQLSubQuery().from(dataset)
								.where(dataset.identification.eq(datasetId))
								.list(
									jobId,
									dataset.id,
									datasetVersionId,
									dataset.filterConditions))
						.executeWithKey(importJob.id).thenCompose(importJobId ->
							tx.insert(importJobColumn)
								.columns(
									importJobColumn.importJobId,
									importJobColumn.index,
									importJobColumn.name,
									importJobColumn.dataType)
								.select(new SQLSubQuery().from(datasetColumn)
									.join(dataset).on(dataset.id.eq(datasetColumn.datasetId))
									.where(dataset.identification.eq(datasetId))
									.list(
										importJobId,
										datasetColumn.index,
										datasetColumn.name,
										datasetColumn.dataType))
										.execute())));
	}	

	private CompletableFuture<Integer> getSourceDatasetVersion(AsyncHelper tx, String datasetId) {
		return tx.query().from(sourceDatasetVersion)
			.join(sourceDataset).on(sourceDataset.id.eq(sourceDatasetVersion.sourceDatasetId))
			.join(dataset).on(dataset.sourceDatasetId.eq(sourceDataset.id))
			.where(dataset.identification.eq(datasetId))
			.singleResult(sourceDatasetVersion.id.max()).thenApply(id -> {			
				// Optional.orElseThrow cannot be used here because of a bug in javac (JDK-8054569)
				if(id.isPresent()) {
					return id.get();
				} else {					 
					throw new IllegalStateException("dataset missing");
				}
			});
							
	}

	private CompletableFuture<Integer> createJob(AsyncHelper tx, JobType jobType) {
		return tx.insert(job)
			.set(job.type, jobType.name())
			.executeWithKey(job.id);
	}
	
	private CompletableFuture<Ack> handleCreateImportJob(CreateImportJob msg) {
		final String datasetId = msg.getDatasetId();
		
		log.debug("creating import job: {}", datasetId);
		
		return db.transactional(tx ->
				tx.query().from(job)
					.join(importJob).on(importJob.jobId.eq(job.id))
					.join(dataset).on(dataset.id.eq(importJob.datasetId))
					.where(dataset.identification.eq(datasetId))
					.where(new SQLSubQuery().from(jobState)
							.where(jobState.jobId.eq(job.id))
							.where(isFinished(jobState))
							.notExists())
					.notExists()
					
				.thenCompose(notExists -> {
					if(notExists) {
						return createImportJob(tx, datasetId)									
							.thenApply(l -> {
								log.debug("import job created");
								
								return new Ack();
							});							
					} else {
						log.debug("already exist an import job for this dataset");
						return f.successful(new Ack());
					}
				}));
	}
	
	private CompletableFuture<Ack> handleCreateRemoveJob(final CreateRemoveJob msg) {
		String datasetIdentification = msg.getDatasetId();
		
		log.debug("creating remove job: {}", datasetIdentification);
		
		return db.transactional(tx ->
			tx.query().from(removeJob)
				.join(dataset).on(dataset.id.eq(removeJob.datasetId))
				.where(dataset.identification.eq(datasetIdentification))
				.exists().thenCompose(exists -> {
					if(exists) {
						return f.successful(new Ack());
					} else {
						return 
							f.collect(
								tx.insert(job)
									.set(job.type, "REMOVE")
									.executeWithKey(job.id))
							.collect(
								tx.query().from(dataset)
									.where(dataset.identification.eq(datasetIdentification))
									.singleResult(dataset.id))
							.thenCompose((jobId, datasetId) -> 
								tx.insert(removeJob)
									.set(removeJob.jobId, jobId)
									.set(removeJob.datasetId, datasetId
										.orElseThrow(() -> new IllegalArgumentException("dataset not exists")))
									.execute().thenApply(l -> new Ack()));
					}
				})
		);
	}
	
	private CompletableFuture<Ack> handleCreateHarvestJob(final CreateHarvestJob msg) {
		log.debug("creating harvest job: {}", msg.getDataSourceId());
		
		return db.transactional(tx ->				
				tx.query().from(job)
					.join(harvestJob).on(harvestJob.jobId.eq(job.id))
					.join(dataSource).on(dataSource.id.eq(harvestJob.dataSourceId))
					.where(dataSource.identification.eq(msg.getDataSourceId()))
					.where(new SQLSubQuery().from(jobState)
							.where(jobState.jobId.eq(job.id))
							.where(isFinished(jobState))
							.notExists())
					.notExists()
				
				.thenCompose(notExists -> {
					if(notExists) {
						return f.collect(
							tx.query().from(dataSource)
								.where(dataSource.identification.eq(msg.getDataSourceId()))
								.singleResult(dataSource.id))
						.collect(
							tx.insert(job)
								.set(job.type, "HARVEST")
								.executeWithKey(job.id))
								
						.thenCompose((dataSourceId, jobId) -> {
							log.debug("job created and dataSourceId determined");
							
							return 
								tx.insert(harvestJob)
									.set(harvestJob.jobId, jobId)				
									.set(harvestJob.dataSourceId, dataSourceId
										.orElseThrow(() -> new IllegalArgumentException("data source not exists")))
									.execute();
						})
						
						.thenApply(l -> {
							log.debug("harvest job created");
							
							return new Ack();
						});
					} else {
						log.debug("already exist a harvest job for this dataSource");
						
						return f.successful(new Ack());
					}
				}));
	}
	
	private CompletableFuture<TypedList<ServiceJobInfo>> handleGetServiceJobs() {
		log.debug("fetching service jobs");
		
		return
			db.query().from(serviceJob)
				.join(service).on(service.id.eq(serviceJob.serviceId))				
				.where(new SQLSubQuery().from(jobState)
						.where(jobState.jobId.eq(serviceJob.jobId))
						.notExists())
				.list(new QServiceJobInfo(
						serviceJob.jobId, 
						service.identification));
	}

	private CompletableFuture<TypedList<ImportJobInfo>> handleGetImportJobs() {
		log.debug("fetching import jobs");
		
		return
			db.transactional(tx -> {
					AsyncSQLQuery query = tx.query().from(job)
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
					
					return (CompletableFuture<TypedList<ImportJobInfo>>)f.collect(
						query.clone()			
							.list(
								job.id,
								importJob.filterConditions,
								category.identification,
								dataSource.identification,
								sourceDataset.identification,
								dataset.id,
								dataset.name,
								dataset.identification))
					
					.collect(
						query.clone()
							.join(importJobColumn).on(importJobColumn.importJobId.eq(importJob.id))							
							.orderBy(importJobColumn.index.asc())
							.list(job.id, importJobColumn.name, importJobColumn.dataType))
						
					.collect(
						query.clone()
							.join(lastSourceDatasetVersion).on(lastSourceDatasetVersion.datasetId.eq(dataset.id))
							.join(sourceDatasetVersionColumn).on(sourceDatasetVersionColumn.sourceDatasetVersionId.eq(lastSourceDatasetVersion.sourceDatasetVersionId))
							.orderBy(sourceDatasetVersionColumn.index.asc())
							.list(job.id, sourceDatasetVersionColumn.name, sourceDatasetVersionColumn.dataType))
						
					.collect(
						query.clone()
							.join(notification).on(notification.jobId.eq(job.id))
							.leftJoin(notificationResult).on(notificationResult.notificationId.eq(notification.id))
							.list(job.id, notification.type, notificationResult.result))
						
					.thenApply((							
						TypedList<Tuple> baseList,						
						TypedList<Tuple> importJobColumnsList, 
						TypedList<Tuple> sourceDatasetColumnsList, 
						TypedList<Tuple> jobNotificationsList) -> {
						
						ArrayList<ImportJobInfo> jobs = new ArrayList<>();
						
						ListIterator<Tuple> importJobColumns = importJobColumnsList.listIterator();
						ListIterator<Tuple> sourceDatasetColumns = sourceDatasetColumnsList.listIterator();
						ListIterator<Tuple> jobNotifications = jobNotificationsList.listIterator();
						
						for(Tuple t : baseList) {
							int jobId = t.get(job.id);
							
							List<Notification> notifications = new ArrayList<>();
							for(; jobNotifications.hasNext();) {
								Tuple tn = jobNotifications.next();
								
								int notificationJobId = tn.get(job.id);				
								if(notificationJobId != jobId) {
									jobNotifications.previous();
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
									t.get(dataset.name),
									t.get(importJob.filterConditions),
									consumeList(importJobColumns, jobId, job.id, new Mapper<Tuple, Column>() {
										
										@Override
										public Column apply(Tuple t) {
											return new Column(
												t.get(importJobColumn.name), 
												t.get(importJobColumn.dataType));
										}
									}),
									consumeList(sourceDatasetColumns, jobId, job.id, new Mapper<Tuple, Column>() {
										
										@Override
										public Column apply(Tuple t) {
											return new Column(
												t.get(sourceDatasetVersionColumn.name),
												t.get(sourceDatasetVersionColumn.dataType));
										} 
									}),
									notifications));
						}						

						return new TypedList<>(ImportJobInfo.class, jobs);
					});
			});
	}
	
	private CompletableFuture<TypedList<HarvestJobInfo>> handleGetHarvestJobs() {
		log.debug("fetching harvest jobs");

		return
			db.query().from(job)
				.join(harvestJob).on(harvestJob.jobId.eq(job.id))
				.join(dataSource).on(dataSource.id.eq(harvestJob.dataSourceId))
				.orderBy(job.createTime.asc())
				.where(new SQLSubQuery().from(jobState)
						.where(jobState.jobId.eq(job.id))
						.notExists())
				.list(new QHarvestJobInfo(job.id, dataSource.identification));
	}
}
