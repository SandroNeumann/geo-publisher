package nl.idgis.publisher.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.mysema.query.Tuple;
import com.mysema.query.sql.SQLSubQuery;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import akka.dispatch.OnComplete;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;
import akka.util.Timeout;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;
import scala.runtime.AbstractFunction2;
import nl.idgis.publisher.database.DatabaseRef;
import nl.idgis.publisher.metadata.messages.GenerateMetadata;
import nl.idgis.publisher.protocol.messages.Ack;
import nl.idgis.publisher.service.messages.GetContent;
import nl.idgis.publisher.service.messages.Layer;
import nl.idgis.publisher.service.messages.ServiceContent;
import nl.idgis.publisher.service.messages.VirtualService;
import nl.idgis.publisher.utils.FutureUtils;
import nl.idgis.publisher.utils.TypedList;
import nl.idgis.publisher.database.QServiceJob;
import nl.idgis.publisher.database.QJobState;
import nl.idgis.publisher.domain.job.JobState;
import nl.idgis.publisher.harvester.messages.GetDataSource;
import nl.idgis.publisher.harvester.sources.messages.GetDatasetMetadata;

import static nl.idgis.publisher.database.QDataset.dataset;
import static nl.idgis.publisher.database.QServiceJob.serviceJob;
import static nl.idgis.publisher.database.QJobState.jobState;
import static nl.idgis.publisher.database.QSourceDataset.sourceDataset;
import static nl.idgis.publisher.database.QDataSource.dataSource;
import static nl.idgis.publisher.database.QSourceDatasetVersion.sourceDatasetVersion;
import static nl.idgis.publisher.database.QCategory.category;

public class MetadataGenerator extends UntypedActor {
	
	private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	
	private final ActorRef service, harvester;
	
	private final DatabaseRef database;
	
	private final MetadataStore serviceMetadataSource, datasetMetadataTarget, serviceMetadataTarget;
	
	private final Config constants;
	
	private FutureUtils f;
	
	public MetadataGenerator(ActorRef database, ActorRef service, ActorRef harvester, MetadataStore serviceMetadataSource, MetadataStore datasetMetadataTarget, MetadataStore serviceMetadataTarget, Config constants) {
		this.database = new DatabaseRef(database, Timeout.apply(15, TimeUnit.SECONDS), getContext().dispatcher(), log);
		this.service = service;
		this.harvester = harvester;
		this.serviceMetadataSource = serviceMetadataSource;
		this.datasetMetadataTarget = datasetMetadataTarget;
		this.serviceMetadataTarget = serviceMetadataTarget;
		this.constants = constants;
	}
	
	public static Props props(ActorRef database, ActorRef service, ActorRef harvester, MetadataStore serviceMetadataSource, MetadataStore datasetMetadataTarget, MetadataStore serviceMetadataTarget, Config constants) {
		return Props.create(MetadataGenerator.class, database, service, harvester, serviceMetadataSource, datasetMetadataTarget, serviceMetadataTarget, constants);
	}
	
	@Override
	public void preStart() throws Exception {		
		f = new FutureUtils(getContext().dispatcher());
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if(msg instanceof GenerateMetadata) {
			generateMetadata();
		} else {
			unhandled(msg);
		}
	}

	private void generateMetadata() {		
		log.debug("generating metadata");
		
		getContext().become(new Procedure<Object>() {

			@Override
			public void apply(Object msg) throws Exception {
				log.debug("busy");
				
				unhandled(msg);
			}
			
		});
		
		final ActorRef sender = getSender();
		
		QServiceJob otherServiceJob = new QServiceJob("otherServiceJob");
		QJobState otherJobState = new QJobState("otherJobState");
		
		f.collect(
			database.query()
				.from(serviceJob)
				.join(dataset).on(dataset.id.eq(serviceJob.datasetId))
				.join(sourceDatasetVersion).on(sourceDatasetVersion.id.eq(serviceJob.sourceDatasetVersionId))
				.join(sourceDataset).on(sourceDataset.id.eq(sourceDatasetVersion.sourceDatasetId))
				.join(dataSource).on(dataSource.id.eq(sourceDataset.dataSourceId))
				.join(category).on(category.id.eq(sourceDatasetVersion.categoryId))			
				.join(jobState).on(
						jobState.jobId.eq(serviceJob.jobId)
					.and(
						jobState.state.eq(JobState.SUCCEEDED.name())))			
				.where(new SQLSubQuery()
					.from(otherServiceJob)
					.join(otherJobState).on(
							otherJobState.jobId.eq(otherServiceJob.jobId)
						.and(
							otherJobState.state.eq(JobState.SUCCEEDED.name())))
					.where(
							otherServiceJob.datasetId.eq(serviceJob.datasetId)
						.and(
							otherJobState.createTime.after(jobState.createTime)))
					.notExists())
				.list(
						dataSource.identification,
						sourceDataset.identification,
						category.identification, 
						dataset.identification,
						dataset.uuid))
			.collect(
				f.ask(service, new GetContent(), ServiceContent.class))		
			.result(new AbstractFunction2<TypedList<Tuple>, ServiceContent, Void>() {

				@Override
				public Void apply(final TypedList<Tuple> queryResult, final ServiceContent serviceContent) {
					log.debug("queryResult and serviceContent collected");
					
					Future<Map<String, ActorRef>> dataSources = getDataSources(queryResult);
					
					f					
						.collect(getMetadataDocuments(dataSources, queryResult))
						.result(new AbstractFunction1<Map<String, MetadataDocument>, Void>() {

							@Override
							public Void apply(Map<String, MetadataDocument> metadataDocuments) {
								log.debug("metadata documents collected");
								
								Map<String, Set<String>> operatesOn = new HashMap<String, Set<String>>();
								
								List<Future<Void>> pendingWork = new ArrayList<Future<Void>>();
								for(Tuple item : queryResult) {
									String sourceDatasetId = item.get(sourceDataset.identification);
									String datasetId = item.get(dataset.identification);
									String datasetUuid = item.get(dataset.uuid);
									
									MetadataDocument metadataDocument = metadataDocuments.get(sourceDatasetId);
									
									pendingWork.add(processDataset(operatesOn, metadataDocument, datasetId, datasetUuid, item.get(category.identification), datasetId, serviceContent));
								}
								
								for(Entry<String, Set<String>> operatesOnEntry : operatesOn.entrySet()) {
									String serviceName = operatesOnEntry.getKey();
									Set<String> serviceOperatesOn = operatesOnEntry.getValue();
									
									Future<MetadataDocument> metadataDocument = serviceMetadataSource.get(serviceName, getContext().dispatcher());
									pendingWork.add(processService(serviceName, metadataDocument, serviceOperatesOn));
								}
								
								Futures.sequence(pendingWork, getContext().dispatcher())
									.onComplete(new OnComplete<Iterable<Void>>() {

										@Override
										public void onComplete(Throwable t, Iterable<Void> i) throws Throwable {
											getContext().unbecome();
											
											sender.tell(new Ack(), getSelf());
											
											if(t != null) {
												log.error("metadata generation failed: {}", t);
											} else {											
												log.debug("metadata generated");	
											}
										}
										
									}, getContext().dispatcher());
								
								return null;
							}
							
						});
					
					return null;
				}
				
			});
	}

	private Future<Map<String, ActorRef>> getDataSources(TypedList<Tuple> queryResult) {
		Map<String, Future<ActorRef>> dataSources = new HashMap<String, Future<ActorRef>>();
		
		for(Tuple item : queryResult) {
			log.debug(item.toString());
			
			String dataSourceId = item.get(dataSource.identification);
			if(!dataSources.containsKey(dataSourceId)) {
				log.debug("fetching dataSource: " + dataSourceId);
				
				dataSources.put(dataSourceId, f.ask(harvester, new GetDataSource(dataSourceId), ActorRef.class));
			}
		}
		
		return f.map(dataSources);	
	}
	
	private Future<Void> processService(final String serviceName, Future<MetadataDocument> metadataDocument, final Set<String> operatesOn) {
		return metadataDocument.flatMap(new Mapper<MetadataDocument, Future<Void>>() {
			
			@Override
			public Future<Void> apply(MetadataDocument metadataDocument) {
				return processService(serviceName, metadataDocument, operatesOn);
			}
			
		}, getContext().dispatcher());
	}
	
	private Future<Void> processService(String serviceName, MetadataDocument metadataDocument, Set<String> operatesOn) {
		try {
			// -- operates on -- (link to dataset md)
			metadataDocument.removeOperatesOn();
			
			String href = constants.getString("operatesOn.href");
			log.debug("service href: " + href);
		
			for (String uuid : operatesOn) {
				String uuidref = href + uuid;
				log.debug("service operatesOn uuidref: " + uuidref);
				
				metadataDocument.addOperatesOn(uuid, uuidref);			
			}
			
			/*
			 *  additional stuff START
			 */
			String wmsLinkage = constants.getString("onlineResource.wms") ;
			String serviceLinkage = null;
			String protocol = null;
			String operationName =  null;
			
			// -- servicetype --
			metadataDocument.removeServiceType();
			// TODO check content serviceName
			if (serviceName.equalsIgnoreCase("WMS")){
				serviceLinkage = constants.getString("onlineResource.wms") ;
				protocol = "OGC:WMS";
				operationName="GetMap";
			} else if (serviceName.equalsIgnoreCase("WFS")){
				serviceLinkage = constants.getString("onlineResource.wfs") ;
				protocol = "OGC:WFS";
				operationName="GetFeature";
			} else {
				// TODO exception?
			}
			metadataDocument.addServiceType(protocol);
			
			// -- service endpoint -- 
			metadataDocument.removeServiceEndpoint();
			// TODO more operations?
			String getCapName = "GetCapabilitities";
			// TODO put in config
			String codeList = "http://www.isotc211.org/2005/iso19119/resources/Codelist/gmxCodelists.xml#DCPList";
			String codeListValue = "WebServices";
			metadataDocument.addServiceEndpoint(getCapName, codeList, codeListValue, serviceLinkage);
			
			metadataDocument.removeBrowseGraphic();
			metadataDocument.removeServiceLinkage();
			metadataDocument.removeSVCoupledResource();
			for (String uuid : operatesOn) {
				// TODO  find layerName 
				String layerName = "b1:grenzen";

				// -- browse graphic --
				String filename = wmsLinkage + "request=GetMap&Service=WMS&SRS=EPSG:28992&CRS=EPSG:28992"
						+ "&Bbox=180000,459000,270000,540000&Width=600&Height=662&Format=image/png&Styles=";
				metadataDocument.addBrowseGraphic(filename + "&Layers=" + layerName);

				// -- transferOptions --
				metadataDocument.addServiceLinkage(serviceLinkage, protocol, layerName);

				// -- WMS layers, WFS features --
				// TODO scopedName = layerName ??
				String scopedName = layerName;
				metadataDocument.addSVCoupledResource(operationName, uuid, scopedName);
			}
			/*
			 *  additional stuff END
			 */

			log.debug("service processed: " + serviceName);
			
			return serviceMetadataTarget.put(serviceName, metadataDocument, getContext().dispatcher());
		} catch(Exception e) {
			return Futures.failed(e);
		}
	}
	
	private Future<Void> processDataset(Map<String, Set<String>> operatesOn, MetadataDocument metadataDocument, String datasetId, String datasetUuid, String schemaName, String tableName, ServiceContent serviceContent) {
		try {
			metadataDocument.removeServiceLinkage();		
			
			for(VirtualService service : serviceContent.getServices()) {
				String serviceName = service.getName();
				
				for(Layer layer : service.getLayers()) {
					if(schemaName.equals(layer.getSchemaName()) && 
						tableName.equals(layer.getTableName())) {
						
						log.debug("layer for dataset " + datasetUuid + " found (table: " + schemaName + "." + tableName + ": " + layer.getName() + " , service: " + serviceName + ")");
						
						final Set<String> serviceOperatesOn;
						if(operatesOn.containsKey(serviceName)) {
							serviceOperatesOn = operatesOn.get(serviceName);						
						} else {
							serviceOperatesOn = new HashSet<String>();
							operatesOn.put(serviceName, serviceOperatesOn);
						}
						
						serviceOperatesOn.add(datasetUuid);
						
						// WMS
						String linkage = constants.getString("onlineResource.wms") ;
						String protocol = "OGC:WMS";
						String name = layer.getName();
						
						metadataDocument.addServiceLinkage(linkage, protocol, name);					
						
						// WFS
						linkage = constants.getString("onlineResource.wfs") ;
						protocol = "OGC:WFS";
						name = layer.getName();
						
						metadataDocument.addServiceLinkage(linkage, protocol, name);
					}
				}
			}
			
			log.debug("dataset processed: " + datasetId);
			
			return datasetMetadataTarget.put(datasetUuid, metadataDocument, getContext().dispatcher());
		} catch(Exception e) {
			return Futures.failed(e);
		}
	}
	
	private Future<Map<String, MetadataDocument>> getMetadataDocuments(Future<Map<String, ActorRef>> dataSources, final TypedList<Tuple> queryResult) {
		return f.flatMap(dataSources, new Mapper<Map<String, ActorRef>, Future<Map<String, MetadataDocument>>>() {
			
			public Future<Map<String, MetadataDocument>> apply(Map<String, ActorRef> dataSources) {
				log.debug("dataSources collected");
				
				return getMetadataDocuments(dataSources, queryResult);
			}
		});
	}
	
	private Future<Map<String, MetadataDocument>> getMetadataDocuments(Map<String, ActorRef> dataSources, TypedList<Tuple> queryResult) {
		Map<String, Future<MetadataDocument>> metadataDocuments = new HashMap<String, Future<MetadataDocument>>();
		
		for(Tuple item : queryResult) {
			String sourceDatasetId = item.get(sourceDataset.identification);
			String dataSourceId = item.get(dataSource.identification);
			
			log.debug("fetching metadata: " + sourceDatasetId);
			
			ActorRef dataSource = dataSources.get(dataSourceId);
			log.debug("dataSource: " + dataSource);
			
			metadataDocuments.put(sourceDatasetId, f.ask(dataSource, new GetDatasetMetadata(sourceDatasetId), MetadataDocument.class));
		}
		
		return f.map(metadataDocuments);
	}
}
