package nl.idgis.publisher.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import scala.concurrent.duration.Duration;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;

import nl.idgis.publisher.AbstractServiceTest;

import nl.idgis.publisher.database.messages.JobInfo;

import nl.idgis.publisher.domain.job.JobState;

import nl.idgis.publisher.job.manager.JobManager;
import nl.idgis.publisher.job.manager.messages.CreateHarvestJob;
import nl.idgis.publisher.job.manager.messages.CreateImportJob;
import nl.idgis.publisher.job.manager.messages.CreateServiceJob;
import nl.idgis.publisher.job.manager.messages.GetHarvestJobs;
import nl.idgis.publisher.job.manager.messages.GetImportJobs;
import nl.idgis.publisher.job.manager.messages.GetServiceJobs;
import nl.idgis.publisher.job.manager.messages.HarvestJobInfo;
import nl.idgis.publisher.job.manager.messages.ImportJobInfo;
import nl.idgis.publisher.job.manager.messages.ServiceJobInfo;
import nl.idgis.publisher.job.manager.messages.UpdateState;
import nl.idgis.publisher.protocol.messages.Ack;

import static nl.idgis.publisher.database.QGenericLayer.genericLayer;
import static nl.idgis.publisher.database.QService.service;

public class InitiatorTest extends AbstractServiceTest {
	
	static class GetReceivedJobs {
		
		final int count;
		
		GetReceivedJobs(int count) {
			this.count = count;
		}
		
		int getCount() {
			return count;
		}

		@Override
		public String toString() {
			return "GetReceivedJobs [count=" + count + "]";
		}
		
	}	
	
	static class JobReceiver extends UntypedActor {
		
		final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
		
		Integer count = null;
		ActorRef sender = null;
		List<JobInfo> jobs = new ArrayList<>();
		
		final ActorRef jobManager;
		
		JobReceiver(ActorRef jobManager) {
			this.jobManager = jobManager;
		}
		
		static Props props(ActorRef jobManager) {
			return Props.create(JobReceiver.class, jobManager);
		}
		
		Procedure<Object> waitingForDatabaseAck(final JobInfo job, final ActorRef initiator) {
			return new Procedure<Object>() {

				@Override
				public void apply(Object msg) throws Exception {
					if(msg instanceof Ack) {
						jobs.add(job);				
						sendJobs();
						
						initiator.tell(new Ack(), getSelf());
						getContext().unbecome();
					} else {
						onElse(msg);
					}
				}
				
			};
		}

		@Override
		public void onReceive(Object msg) throws Exception {
			log.debug("message received: " + msg);
			
			if(msg instanceof JobInfo) {
				jobManager.tell(new UpdateState((JobInfo)msg, JobState.SUCCEEDED), getSelf());
				
				getContext().become(waitingForDatabaseAck((JobInfo)msg, getSender()));
			} else {
				onElse(msg);
			}
		}
		
		void onElse(Object msg) {
			if(msg instanceof GetReceivedJobs) {
				sender = getSender();
				count = ((GetReceivedJobs) msg).getCount();
				sendJobs();	 
			} else {
				unhandled(msg);
			}
		}		
		
		void sendJobs() {
			if(sender != null && count != null && jobs.size() == count) {
				sender.tell(jobs, getSelf());
				
				sender = null;
				count = null;
				jobs = new ArrayList<>();
			}
		}
		
	}
	
	static class BrokenJobReceiver extends JobReceiver {
		
		int skipMessageCount = 0;
		
		BrokenJobReceiver(ActorRef jobManager) {
			super(jobManager);
		}

		static Props props(ActorRef jobManager) {
			return Props.create(BrokenJobReceiver.class, jobManager);
		}
		
		@Override
		public void onReceive(Object msg) throws Exception {
			log.debug("message received");
			
			if(msg instanceof GetReceivedJobs) {
				super.onReceive(msg);
			} else {
				if(++skipMessageCount == 5) {
					log.debug("message processed");
					
					skipMessageCount = 0;
					super.onReceive(msg);
				} else {
					log.debug("message ignored");
				}
			}
		}
	}
	
	@Before
	public void databaseContent() throws Exception {
		insertDataset("testDataset0");
		insertDataset("testDataset1");
		
		int rootId = insert(genericLayer)
			.set(genericLayer.identification, "root")
			.set(genericLayer.name, "rootName")
			.executeWithKey(genericLayer.id);
		
		insert(service)
			.set(service.identification, "testService")
			.set(service.name, "testServiceName")
			.set(service.rootgroupId, rootId)
			.execute();
	}
	
	ActorRef manager;
	
	@Before
	public void actors() throws Exception {
		manager = actorOf(JobManager.props(database), "manager");
	}
	
	@Test
	public void testHarvestJob() throws Exception {
		sync.ask(manager, new CreateHarvestJob("testDataSource"));
		
		ActorRef harvester = actorOf(JobReceiver.props(jobManager), "harvesterMock");
		actorOf(
			Initiator.props()
				.add(harvester, "harvester", new GetHarvestJobs())
				.create(manager), 
			"initiator");
		
		List<?> list = sync.ask(harvester, new GetReceivedJobs(1), List.class);
		assertEquals(HarvestJobInfo.class, list.get(0).getClass());
	}
	
	@Test
	public void testImportJob() throws Exception {
		sync.ask(manager, new CreateImportJob("testDataset0"));
		sync.ask(manager, new CreateImportJob("testDataset1"));
		
		ActorRef loader = actorOf(JobReceiver.props(jobManager), "loaderMock");
		actorOf(
			Initiator.props()
				.add(loader, "loader", new GetImportJobs())
				.create(manager), 
			"initiator");
		
		List<?> list = sync.ask(loader, new GetReceivedJobs(2), List.class);
		
		Object job0 = list.get(0);
		Object job1 = list.get(1);
		
		assertEquals(ImportJobInfo.class, job0.getClass());
		assertEquals(ImportJobInfo.class, job1.getClass());
		
		Set<String> datasets = new HashSet<>();
		datasets.add(((ImportJobInfo)job0).getDatasetId());
		datasets.add(((ImportJobInfo)job1).getDatasetId());
		
		assertTrue(datasets.contains("testDataset0"));
		assertTrue(datasets.contains("testDataset1"));
	}

	@Test
	public void testServiceJob() throws Exception {		
		sync.ask(manager, new CreateServiceJob("testService"));

		ActorRef service = actorOf(JobReceiver.props(jobManager), "serviceMock");
		actorOf(
			Initiator.props()
				.add(service, "service", new GetServiceJobs())
				.create(manager), 
			"initiator");
		
		List<?> list = sync.ask(service, new GetReceivedJobs(1), List.class);
		assertEquals(ServiceJobInfo.class, list.get(0).getClass());
	}
	
	@Test
	public void testInterval() throws Exception {
		ActorRef service = actorOf(JobReceiver.props(jobManager), "serviceMock");
		actorOf(
			Initiator.props()
				.add(service, "service", new GetServiceJobs())
				.create(manager, Duration.create(1, TimeUnit.MILLISECONDS)), 
			"initiator");
		
		Thread.sleep(100);
		
		sync.ask(manager, new CreateServiceJob("testService"));
		sync.ask(service, new GetReceivedJobs(1), List.class);
		
		Thread.sleep(100);
		
		sync.ask(manager, new CreateServiceJob("testService"));
		sync.ask(service, new GetReceivedJobs(1), List.class);
		
		Thread.sleep(100);
		
		sync.ask(manager, new CreateServiceJob("testService"));
		sync.ask(service, new GetReceivedJobs(1), List.class);
	}
	
	@Test
	public void testTimeout() throws Exception {
		sync.ask(manager, new CreateServiceJob("testService"));
		
		ActorRef service = actorOf(BrokenJobReceiver.props(jobManager), "serviceMock");
		actorOf(
			Initiator.props()
				.add(service, "service", new GetServiceJobs())
				.create(
						manager, 
						Duration.create(1, TimeUnit.MILLISECONDS), 
						Duration.create(1, TimeUnit.MILLISECONDS)), 
			"initiator");
		
		sync.ask(service, new GetReceivedJobs(1), List.class);
	}
}
