package nl.idgis.publisher.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;

import nl.idgis.publisher.database.AbstractDatabaseTest;
import nl.idgis.publisher.database.messages.AddNotificationResult;
import nl.idgis.publisher.database.messages.CreateDataset;
import nl.idgis.publisher.database.messages.CreateImportJob;
import nl.idgis.publisher.database.messages.GetImportJobs;
import nl.idgis.publisher.database.messages.GetJobLog;
import nl.idgis.publisher.database.messages.ImportJobInfo;
import nl.idgis.publisher.database.messages.InfoList;
import nl.idgis.publisher.database.messages.InsertRecord;
import nl.idgis.publisher.database.messages.RegisterSourceDataset;
import nl.idgis.publisher.database.messages.Registered;
import nl.idgis.publisher.database.messages.StartTransaction;
import nl.idgis.publisher.database.messages.StoredJobLog;
import nl.idgis.publisher.database.messages.TransactionCreated;
import nl.idgis.publisher.database.messages.UpdateDataset;
import nl.idgis.publisher.database.messages.UpdateJobState;
import nl.idgis.publisher.database.messages.Updated;
import nl.idgis.publisher.domain.MessageProperties;
import nl.idgis.publisher.domain.job.ConfirmNotificationResult;
import nl.idgis.publisher.domain.job.JobState;
import nl.idgis.publisher.domain.job.LogLevel;
import nl.idgis.publisher.domain.job.Notification;
import nl.idgis.publisher.domain.job.load.ImportLogType;
import nl.idgis.publisher.domain.job.load.ImportNotificationType;
import nl.idgis.publisher.domain.job.load.MissingColumnsLog;
import nl.idgis.publisher.domain.service.Column;
import nl.idgis.publisher.domain.service.Dataset;
import nl.idgis.publisher.domain.service.Table;
import nl.idgis.publisher.domain.web.EntityType;
import nl.idgis.publisher.harvester.messages.GetDataSource;
import nl.idgis.publisher.harvester.sources.messages.GetDataset;
import nl.idgis.publisher.harvester.sources.messages.StartImport;
import nl.idgis.publisher.protocol.messages.Ack;
import nl.idgis.publisher.provider.protocol.database.Record;
import nl.idgis.publisher.provider.protocol.database.Records;
import nl.idgis.publisher.stream.messages.End;
import nl.idgis.publisher.stream.messages.NextItem;
import nl.idgis.publisher.utils.TypedIterable;

public class LoaderTest extends AbstractDatabaseTest {
	
	static class GetInsertCount {
		
	}
	
	static class TransactionMock extends UntypedActor {
		
		final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
		
		int insertCount = 0;

		@Override
		public void onReceive(Object msg) throws Exception {
			log.debug("received: " + msg);
			
			if(msg instanceof GetInsertCount) {
				getSender().tell(insertCount, getSelf());
			} else {			
				if(msg instanceof InsertRecord) {
					insertCount++;
				}
			
				getSender().tell(new Ack(), getSelf());
			}
		}
		
	}
	
	static class GeometryDatabaseMock extends UntypedActor {
		
		final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

		@Override
		public void onReceive(Object msg) throws Exception {
			log.debug("received: " + msg);
			
			if(msg instanceof StartTransaction) {
				ActorRef transaction = getContext().actorOf(Props.create(TransactionMock.class));				
				getSender().tell(new TransactionCreated(transaction), getSelf());
			} else {
				unhandled(msg);
			}
		}		
	}
	
	static class DataSourceMock extends UntypedActor {
		
		final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

		@Override
		public void onReceive(Object msg) throws Exception {
			log.debug("received: " + msg);
			
			if(msg instanceof GetDataset) {
				GetDataset gd = (GetDataset)msg;				
				ActorRef receiver = getContext().actorOf(gd.getReceiverProps(), "receiver");
				receiver.tell(new StartImport(10), getSelf());
				getContext().become(waitingForStart());
			} else {
				unhandled(msg);
			}
		}
		
		private Procedure<Object> sendingRecords(Iterable<Records> records) {
			final Iterator<Records> itr = records.iterator();			
			
			return new Procedure<Object>() {
				
				{
					sendResponse();
				}
				
				void sendResponse() {
					if(itr.hasNext()) {
						getSender().tell(itr.next(), getSelf());
					} else {
						getSender().tell(new End(), getSelf());						
					}
				}
				
				@Override
				public void apply(Object msg) throws Exception {
					log.debug("received: " + msg);
					
					if(msg instanceof NextItem) {
						sendResponse();
					} else {
						unhandled(msg);
					}
				}
			};
		}

		private Procedure<Object> waitingForStart() {
			return new Procedure<Object>() {

				@Override
				public void apply(Object msg) throws Exception {
					log.debug("received: " + msg);
					
					if(msg instanceof Ack) {
						List<Records> records = new ArrayList<>();
						for(int i = 0; i < 2; i++) {
							
							List<Record> currentRecords = new ArrayList<>();
							for(int j = 0; j < 5; j++) {
								int value = i * 5 + j;
								
								currentRecords.add(
									new Record(
										Arrays.<Object>asList(
												"value: " + j, 
												value)));
							}
							
							records.add(new Records(currentRecords));
						}
						
						getContext().become(sendingRecords(records));
					} else {
						unhandled(msg);
					}
				}
				
			};
		}
		
	}
	
	static class HarvesterMock extends UntypedActor {
		
		final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
		
		final ActorRef dataSource;
		
		HarvesterMock(ActorRef dataSource) {
			this.dataSource = dataSource;
		}

		@Override
		public void onReceive(Object msg) throws Exception {
			log.debug("received: " + msg);
			
			if(msg instanceof GetDataSource) {
				getSender().tell(dataSource, getSelf());
			} else {
				unhandled(msg);
			}
		}		
	}
	
	static class GetFinishedState {
		
	}
	
	static class DatabaseAdapter extends UntypedActor {
		
		final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
		
		final ActorRef database;
		
		ActorRef sender = null;
		JobState finishedState = null;
		
		DatabaseAdapter(ActorRef database) {
			this.database = database;
		}

		@Override
		public void onReceive(Object msg) throws Exception {
			log.debug("received: " + msg);
			
			if(msg instanceof GetFinishedState) {
				sender = getSender();
				sendFinishedState();
			} else {			
				if(msg instanceof UpdateJobState) {
					UpdateJobState ujs = (UpdateJobState)msg;
					
					JobState currentState = ujs.getState();
					if(currentState.isFinished()) {					
						finishedState = currentState;
						sendFinishedState();					
					}
				}
				
				database.tell(msg, getSender());
			}
		}
		
		void sendFinishedState() {
			if(sender != null && finishedState != null) {
				sender.tell(finishedState, getSelf());
				
				sender = null;
				finishedState = null;
			}
		}
		
	}
	
	ActorRef loader;
	ActorRef databaseAdapter;
	ActorRef geometryDatabaseMock;
	
	@Before
	public void actors() {
		geometryDatabaseMock = actorOf(Props.create(GeometryDatabaseMock.class), "geometryDatabaseMock");
		ActorRef dataSourceMock = actorOf(Props.create(DataSourceMock.class), "dataSourceMock");
		ActorRef harvesterMock = actorOf(Props.create(HarvesterMock.class, dataSourceMock), "harvesterMock");		
		databaseAdapter = actorOf(Props.create(DatabaseAdapter.class, database), "databaseAdapter");
		
		loader = actorOf(Loader.props(geometryDatabaseMock, databaseAdapter, harvesterMock), "loader");
	}

	@Test
	public void testExecuteImportJob() throws Exception {
		insertDataset();
		ask(database, new CreateImportJob("testDataset"));
		
		TypedIterable<?> iterable = askAssert(database, new GetImportJobs(), TypedIterable.class);
		assertTrue(iterable.contains(ImportJobInfo.class));
		for(ImportJobInfo job : iterable.cast(ImportJobInfo.class)) {
			askAssert(loader, job, Ack.class);
		}
		
		assertEquals(
				JobState.SUCCEEDED, 				
				askAssert(databaseAdapter, new GetFinishedState(), JobState.class));
		
		int insertCount = askAssert(
				ActorSelection.apply(geometryDatabaseMock, "*"), 
				new GetInsertCount(), 
				Integer.class);
		
		assertEquals(10, insertCount);
	}
	
	@Test
	public void testAddColumnsChangedNotification() throws Exception {
		insertDataSource();		
		
		Dataset testDataset = createTestDataset();
		Table testTable = testDataset.getTable();
		List<Column> testColumns = testTable.getColumns();
		
		askAssert(database, new RegisterSourceDataset("testDataSource", testDataset), Registered.class);
		
		ask(database, new CreateDataset(
				"testDataset", 
				"My Test Dataset", 
				testDataset.getId(), 
				testColumns, 
				"{ \"expression\": null }"));
				
		ask(database, new CreateImportJob("testDataset"));
		executeJobs(new GetImportJobs());
		
		Table updatedTable = new Table(
				testTable.getName(), 
				Arrays.asList(testColumns.get(0)));		
		Dataset updatedDataset = new Dataset(
				testDataset.getId(),
				testDataset.getCategoryId(),
				updatedTable,
				testDataset.getRevisionDate());
		
		askAssert(database, new RegisterSourceDataset("testDataSource", updatedDataset), Updated.class);
		ask(database, new CreateImportJob("testDataset"));
		
		TypedIterable<?> iterable = askAssert(database, new GetImportJobs(), TypedIterable.class);
		assertTrue(iterable.contains(ImportJobInfo.class));
		for(ImportJobInfo jobInfo : iterable.cast(ImportJobInfo.class)) {
			assertFalse(jobInfo.hasNotification(ImportNotificationType.SOURCE_COLUMNS_CHANGED));
			askAssert(loader, jobInfo, Ack.class);
		}
		
		iterable = askAssert(database, new GetImportJobs(), TypedIterable.class);
		assertTrue(iterable.contains(ImportJobInfo.class));
		
		Set<Integer> pendingJobs = new HashSet<>();
		for(ImportJobInfo jobInfo : iterable.cast(ImportJobInfo.class)) {
			pendingJobs.add(jobInfo.getId());
			
			assertTrue(jobInfo.hasNotification(ImportNotificationType.SOURCE_COLUMNS_CHANGED));
			
			for(Notification notification : jobInfo.getNotifications()) {
				if(notification.getType() == ImportNotificationType.SOURCE_COLUMNS_CHANGED) {
					assertNull(notification.getResult());
				}
			}
		}	
		
		for(ImportJobInfo jobInfo : iterable.cast(ImportJobInfo.class)) {
			if(jobInfo.hasNotification(ImportNotificationType.SOURCE_COLUMNS_CHANGED)) {
				ask(database, new AddNotificationResult(
						jobInfo, 
						ImportNotificationType.SOURCE_COLUMNS_CHANGED, 
						ConfirmNotificationResult.OK));
			}
		}
				
		iterable = askAssert(database, new GetImportJobs(), TypedIterable.class);
		assertTrue(iterable.contains(ImportJobInfo.class));
		for(ImportJobInfo jobInfo : iterable.cast(ImportJobInfo.class)) {
			assertTrue(jobInfo.hasNotification(ImportNotificationType.SOURCE_COLUMNS_CHANGED));
			
			for(Notification notification : jobInfo.getNotifications()) {
				if(notification.getType() == ImportNotificationType.SOURCE_COLUMNS_CHANGED) {
					assertEquals(ConfirmNotificationResult.OK, notification.getResult());
				}
			}
			
			askAssert(loader, jobInfo, Ack.class);
		}
		
		assertEquals(
				JobState.FAILED,
				askAssert(databaseAdapter, new GetFinishedState(), JobState.class));
		
		InfoList<?> infoList = askAssert(database, new GetJobLog(LogLevel.ERROR), InfoList.class);
		assertEquals(1, infoList.getCount().intValue());
		
		StoredJobLog jobLog = (StoredJobLog)infoList.getList().get(0);
		assertEquals(LogLevel.ERROR, jobLog.getLevel());
		assertEquals(ImportLogType.MISSING_COLUMNS, jobLog.getType());
		
		MessageProperties logContent = jobLog.getContent();
		assertNotNull(logContent);
		assertTrue(logContent instanceof MissingColumnsLog);
		
		MissingColumnsLog missingColumnsLog = (MissingColumnsLog)logContent;
		assertEquals("testDataset", missingColumnsLog.getIdentification());
		assertEquals("My Test Dataset", missingColumnsLog.getTitle());
		assertEquals(EntityType.DATASET, missingColumnsLog.getEntityType());
		
		Set<Column> missingColumns = missingColumnsLog.getColumns();
		assertNotNull(missingColumns);
		assertTrue(missingColumns.contains(testColumns.get(1)));
		
		ask(database, new UpdateDataset(
				"testDataset", 
				"My Test Dataset", 
				"testSourceDataset", 
				Arrays.asList(testColumns.get(0)),
				"{ \"expression\": null }"));
		
		ask(database, new CreateImportJob("testDataset"));
		
		iterable = askAssert(database, new GetImportJobs(), TypedIterable.class);
		assertTrue(iterable.contains(ImportJobInfo.class));
		for(ImportJobInfo jobInfo : iterable.cast(ImportJobInfo.class)) {
			askAssert(loader, jobInfo, Ack.class);
		}
		
		assertEquals(
				JobState.SUCCEEDED,
				askAssert(databaseAdapter, new GetFinishedState(), JobState.class));		
		
		int count = askAssert(ActorSelection.apply(geometryDatabaseMock, "*"), new GetInsertCount(), Integer.class);
		assertEquals(10, count);
	}
}
