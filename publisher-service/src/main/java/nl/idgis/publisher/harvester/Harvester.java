package nl.idgis.publisher.harvester;

import java.util.ArrayList;

import nl.idgis.publisher.domain.job.JobState;

import nl.idgis.publisher.harvester.messages.DataSourceConnected;
import nl.idgis.publisher.harvester.messages.GetActiveDataSources;
import nl.idgis.publisher.harvester.messages.GetDataSource;
import nl.idgis.publisher.harvester.messages.NotConnected;
import nl.idgis.publisher.harvester.server.Server;
import nl.idgis.publisher.harvester.sources.messages.ListDatasets;
import nl.idgis.publisher.job.context.messages.UpdateJobState;
import nl.idgis.publisher.job.manager.messages.HarvestJobInfo;
import nl.idgis.publisher.messages.ActiveJob;
import nl.idgis.publisher.messages.ActiveJobs;
import nl.idgis.publisher.messages.GetActiveJobs;
import nl.idgis.publisher.protocol.messages.Ack;
import nl.idgis.publisher.utils.ConfigUtils;
import nl.idgis.publisher.utils.FutureUtils;
import nl.idgis.publisher.utils.UniqueNameGenerator;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.typesafe.config.Config;

public class Harvester extends UntypedActor {
	
	private final Config config;
	
	private final ActorRef datasetManager;
	
	private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	
	private final UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
	
	private BiMap<String, ActorRef> dataSources;
	
	private BiMap<HarvestJobInfo, ActorRef> sessions;
	
	private FutureUtils f;

	public Harvester(ActorRef datasetManager, Config config) {			
		this.datasetManager = datasetManager;
		this.config = config;
	}
	
	public static Props props(ActorRef datasetManager, Config config) {
		return Props.create(Harvester.class, datasetManager, config);
	}

	@Override
	public void preStart() {
		final String name = config.getString("name");		
		final int port = config.getInt("port");
		
		final Config sslConfig = ConfigUtils.getOptionalConfig(config, "ssl");
		
		getContext().actorOf(Server.props(name, getSelf(), port, sslConfig), "server");
		
		dataSources = HashBiMap.create();
		
		sessions = HashBiMap.create();
		
		f = new FutureUtils(getContext().dispatcher());
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		log.debug("message: " + msg);
		
		if(msg instanceof DataSourceConnected) {
			handleDataSourceConnected((DataSourceConnected)msg);
		} else if (msg instanceof Terminated) {
			handleTerminated((Terminated)msg);
		} else if (msg instanceof HarvestJobInfo) {
			handleHarvestJob((HarvestJobInfo)msg);			
		} else if(msg instanceof GetActiveDataSources) {
			handleGetActiveDataSources();
		} else if(msg instanceof GetDataSource) {
			handleGetDataSource((GetDataSource)msg);
		} else if(msg instanceof GetActiveJobs) {
			handleGetActiveJobs();
		} else {
			unhandled(msg);
		}
	}

	private void handleGetActiveJobs() {
		ArrayList<ActiveJob> activeJobs = new ArrayList<>();
		for(HarvestJobInfo harvestJob : sessions.keySet()) {
			activeJobs.add(new ActiveJob(harvestJob));
		}
		
		getSender().tell(new ActiveJobs(activeJobs), getSelf());
	}

	private void handleGetDataSource(GetDataSource msg) {
		log.debug("dataSource requested");
		
		final String dataSourceId = msg.getDataSourceId();
		if(dataSources.containsKey(dataSourceId)) {
			getSender().tell(dataSources.get(dataSourceId), getSelf());
		} else {
			log.warning("dataSource not connected: " + dataSourceId);
			getSender().tell(new NotConnected(), getSelf());
		}
	}

	private void handleGetActiveDataSources() {
		log.debug("connected datasources requested");
		getSender().tell(dataSources.keySet(), getSelf());
	}
	
	private boolean isHarvesting(String dataSourceId) {
		for(HarvestJobInfo job : sessions.keySet()) {
			if(job.getDataSourceId().equals(dataSourceId)) {
				return true;
			}
		}
		
		return false;
	}

	private void handleHarvestJob(HarvestJobInfo harvestJob) {
		String dataSourceId = harvestJob.getDataSourceId();
		if(dataSources.containsKey(dataSourceId)) {
			if(isHarvesting(dataSourceId)) {
				log.debug("already harvesting dataSource: " + dataSourceId);
			} else {
				log.debug("Initializing harvesting for dataSource: " + dataSourceId);
			
				startHarvesting(harvestJob);
			}
		} else {
			getSender().tell(new Ack(), getSelf());
			
			log.debug("dataSource not connected: " + dataSourceId);
		}
	}

	private void handleTerminated(Terminated msg) {
		ActorRef actor = msg.getActor();
		
		log.debug("actor terminated: " + actor);
		
		String dataSourceName = dataSources.inverse().remove(actor);
		if(dataSourceName != null) {
			log.debug("connection lost, dataSource: " + dataSourceName);
		}
		
		HarvestJobInfo harvestJob = sessions.inverse().remove(actor);
		if(harvestJob != null) {
			log.debug("harvest job completed: " + harvestJob);			
		}
	}

	private void handleDataSourceConnected(DataSourceConnected msg) {
		log.debug("dataSource connected: {}", msg);
		
		String dataSourceId = msg.getDataSourceId();
		ActorRef dataSource = msg.getDataSource();
		
		getContext().watch(dataSource);
		dataSources.put(dataSourceId, dataSource);
		
		getSender().tell(new Ack(), getSelf());
	}	

	private void startHarvesting(final HarvestJobInfo harvestJob) {
		
		ActorRef sender = getSender();
		f.ask(sender, new UpdateJobState(JobState.STARTED)).whenComplete((msg, t) -> {
			if(t != null) {
				log.error("couldn't change job state: {}", t);
				sender.tell(new Ack(), getSelf());
			} else {
				log.debug("starting harvesting for dataSource: " + harvestJob);
				
				sender.tell(new Ack(), getSelf());
				
				ActorRef session = getContext().actorOf(
						HarvestSession.props(sender, datasetManager, harvestJob), 
						nameGenerator.getName(HarvestSession.class));
				
				getContext().watch(session);
				sessions.put(harvestJob, session);
				
				dataSources.get(harvestJob.getDataSourceId()).tell(new ListDatasets(), session);
			}
		});
	}
}
