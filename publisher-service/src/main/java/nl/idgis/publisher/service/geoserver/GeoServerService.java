package nl.idgis.publisher.service.geoserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Objects;
import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;

import nl.idgis.publisher.domain.job.JobState;

import nl.idgis.publisher.job.context.messages.UpdateJobState;
import nl.idgis.publisher.job.manager.messages.ServiceJobInfo;
import nl.idgis.publisher.messages.ActiveJob;
import nl.idgis.publisher.messages.ActiveJobs;
import nl.idgis.publisher.messages.GetActiveJobs;
import nl.idgis.publisher.protocol.messages.Ack;
import nl.idgis.publisher.protocol.messages.Failure;
import nl.idgis.publisher.service.geoserver.messages.EnsureFeatureTypeLayer;
import nl.idgis.publisher.service.geoserver.messages.EnsureGroupLayer;
import nl.idgis.publisher.service.geoserver.messages.EnsureWorkspace;
import nl.idgis.publisher.service.geoserver.messages.Ensured;
import nl.idgis.publisher.service.geoserver.messages.FinishEnsure;
import nl.idgis.publisher.service.geoserver.rest.DataStore;
import nl.idgis.publisher.service.geoserver.rest.DefaultGeoServerRest;
import nl.idgis.publisher.service.geoserver.rest.FeatureType;
import nl.idgis.publisher.service.geoserver.rest.GeoServerRest;
import nl.idgis.publisher.service.geoserver.rest.LayerGroup;
import nl.idgis.publisher.service.geoserver.rest.LayerRef;
import nl.idgis.publisher.service.geoserver.rest.Workspace;
import nl.idgis.publisher.service.manager.messages.GetService;
import nl.idgis.publisher.utils.FutureUtils;
import nl.idgis.publisher.utils.UniqueNameGenerator;

public class GeoServerService extends UntypedActor {
	
	private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	
	private final UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
	
	private final ActorRef serviceManager;
	
	private final String serviceLocation, user, password;
	
	private final Map<String, String> connectionParameters;
	
	private GeoServerRest rest;
	
	private FutureUtils f;

	public GeoServerService(ActorRef serviceManager, String serviceLocation, String user, String password, Map<String, String> connectionParameters) throws Exception {		
		this.serviceManager = serviceManager;
		this.serviceLocation = serviceLocation;
		this.user = user;
		this.password = password;
		this.connectionParameters = Collections.unmodifiableMap(connectionParameters);
	}
	
	public static Props props(ActorRef serviceManager, Config geoserverConfig, Config databaseConfig) {
		String serviceLocation = geoserverConfig.getString("url") + "rest/";
		String user = geoserverConfig.getString("user");
		String password = geoserverConfig.getString("password");		
		
		String url = databaseConfig.getString("url");
		
		Pattern urlPattern = Pattern.compile("jdbc:postgresql://(.*):(.*)/(.*)");
		Matcher matcher = urlPattern.matcher(url);
		
		if(!matcher.matches()) {
			throw new IllegalArgumentException("incorrect database url");
		}
		
		Map<String, String> connectionParameters = new HashMap<>();
		connectionParameters.put("dbtype", "postgis");
		connectionParameters.put("host", matcher.group(1));
		connectionParameters.put("port", matcher.group(2));
		connectionParameters.put("database", matcher.group(3));
		connectionParameters.put("user", databaseConfig.getString("user"));
		connectionParameters.put("passwd", databaseConfig.getString("password"));
		connectionParameters.put("schema", geoserverConfig.getString("schema"));
		
		return Props.create(GeoServerService.class, serviceManager, serviceLocation, user, password, connectionParameters);
	}
	
	@Override
	public void preStart() throws Exception {
		rest = new DefaultGeoServerRest(serviceLocation, user, password);
		f = new FutureUtils(getContext().dispatcher());
	}
	
	@Override
	public void onReceive(Object msg) throws Exception {
		if(msg instanceof ServiceJobInfo) {
			handleServiceJob((ServiceJobInfo)msg);
		} else if(msg instanceof GetActiveJobs) {
			getSender().tell(new ActiveJobs(Collections.<ActiveJob>emptyList()), getSelf());
		} else {
			unhandled(msg);
		}
	}
	
	private void elseProvisioning(Object msg, ServiceJobInfo serviceJob, ActorRef initiator) {
		if(msg instanceof ServiceJobInfo) {
			// this shouldn't happen too often, TODO: rethink job mechanism
			log.debug("receiving service job while provisioning");
			getSender().tell(new Ack(), getSelf());
		} else if(msg instanceof GetActiveJobs) {
			getSender().tell(new ActiveJobs(Collections.singletonList(new ActiveJob(serviceJob))), getSelf());
		} else if(msg instanceof Failure) {
			log.error("failure: {}", msg);
			
			// TODO: add logging
			initiator.tell(new UpdateJobState(JobState.FAILED), getSelf());
			getContext().become(receive());
		} else {
			unhandled(msg);
		}
	}
	
	private void toSelf(Object msg) {
		ActorRef self = getSelf();
		self.tell(msg, self);
	}
	
	private void toSelf(CompletableFuture<?> future) {
		ActorRef self = getSelf();
		
		future.whenComplete((msg, t) ->  {
			if(t != null) {
				self.tell(new Failure(t), self);
			} else {
				self.tell(msg, self);
			}
		});
	}
	
	private void ensured(ActorRef provisioningService) {
		log.debug("ensured");
		
		provisioningService.tell(new Ensured(), getSelf());
	}
	
	private static class LayerEnsured { }
	
	private static class GroupEnsured { }
	
	private static class WorkspaceEnsured { }
	
	private Procedure<Object> layers(
			ActorRef initiator, 
			ServiceJobInfo serviceJob, 
			ActorRef provisioningService, 
			Workspace workspace, 
			DataStore dataStore,
			Map<String, FeatureType> featureTypes,
			Map<String, LayerGroup> layerGroups) {
		
		return layers(null, initiator, serviceJob, provisioningService, workspace, dataStore, featureTypes, layerGroups);
	}
	
	private Procedure<Object> layers(
			EnsureGroupLayer groupLayer, 
			ActorRef initiator, 
			ServiceJobInfo serviceJob, 
			ActorRef provisioningService, 
			Workspace workspace, 
			DataStore dataStore,
			Map<String, FeatureType> featureTypes,
			Map<String, LayerGroup> layerGroups) {
		
		List<LayerRef> groupLayerContent = new ArrayList<>();
		
		log.debug("-> layers {}", groupLayer == null ? "" : null);
		
		return new Procedure<Object>() {
			
			private boolean unchanged(FeatureType rest, EnsureFeatureTypeLayer ensure) {
				log.debug("checking if feature type is changed");
				
				if(!Objects.equal(rest.getNativeName(), ensure.getTableName())) {
					log.debug("new table name: {}, was {}", ensure.getTableName(), rest.getNativeName());
					
					return false;
				}
				
				if(!Objects.equal(rest.getTitle(), ensure.getTitle())) {
					log.debug("new title: {}, was {}", ensure.getTitle(), rest.getTitle());
					
					return false;
				}
				
				if(!Objects.equal(rest.getAbstract(), ensure.getAbstract())) {
					log.debug("new abstract: {}, was {}", ensure.getAbstract(), rest.getAbstract());
					
					return false;
				}
				
				return true;
			}
			
			private boolean unchanged(LayerGroup rest) {
				log.debug("checking if layer group is changed");
				
				if(!Objects.equal(rest.getTitle(), groupLayer.getTitle())) {
					log.debug("new title: {}, was {}", groupLayer.getTitle(), rest.getTitle());
					return false;
				}
				
				if(!Objects.equal(rest.getAbstract(), groupLayer.getAbstract())) {
					log.debug("new abstract: {}, was {}", groupLayer.getAbstract(), rest.getAbstract());
					return false;
				}
				
				if(!rest.getLayers().equals(groupLayerContent)) {
					log.debug("new layer content: {}, was {}", groupLayerContent, rest.getLayers());
					return false;
				}
				
				return true;
			}
			
			FeatureType toFeatureType(EnsureFeatureTypeLayer ensureLayer) {
				String layerId = ensureLayer.getLayerId();
				
				log.debug("building feature type: {}", layerId);
				
				return new FeatureType(
					layerId, 
					ensureLayer.getTableName(),
					ensureLayer.getTitle(),
					ensureLayer.getAbstract());
			}
			
			void putFeatureType(EnsureFeatureTypeLayer ensureLayer) {
				log.debug("putting feature type");
				
				toSelf(
					rest.putFeatureType(workspace, dataStore, toFeatureType(ensureLayer)).thenApply(v -> {								
						log.debug("feature type updated");									
						return new LayerEnsured();
				}));
			}
			
			void postFeatureType(EnsureFeatureTypeLayer ensureLayer) {
				log.debug("posting feature type");
				
				toSelf(
					rest.postFeatureType(workspace, dataStore, toFeatureType(ensureLayer)).thenApply(v -> {								
						log.debug("feature type created");									
						return new LayerEnsured();
				}));
			}
			
			LayerGroup toLayerGroup() {
				String groupLayerId = groupLayer.getLayerId();
				
				log.debug("building layer group: {}", groupLayerId);
				
				return new LayerGroup(
						groupLayerId,
						groupLayer.getTitle(),
						groupLayer.getAbstract(),
						groupLayerContent);
			}
			
			void putLayerGroup() {
				log.debug("putting layer group");
				
				toSelf(
					rest.putLayerGroup(workspace, toLayerGroup()).thenApply(v -> {
						log.debug("layer group updated");									
						return new GroupEnsured();
				}));
			}
			
			void postLayerGroup() {
				log.debug("posting layer group");
				
				toSelf(
					rest.postLayerGroup(workspace, toLayerGroup()).thenApply(v -> {
						log.debug("layer group created");									
						return new GroupEnsured();
				}));
			}

			@Override
			public void apply(Object msg) throws Exception {
				if(msg instanceof EnsureGroupLayer) {
					EnsureGroupLayer ensureLayer = (EnsureGroupLayer)msg;
					
					ensured(provisioningService);
					groupLayerContent.add(new LayerRef(ensureLayer.getLayerId(), true));
					getContext().become(layers(ensureLayer, initiator, serviceJob, 
						provisioningService, workspace, dataStore, featureTypes, layerGroups), false);
				} else if(msg instanceof EnsureFeatureTypeLayer) {
					EnsureFeatureTypeLayer ensureLayer = (EnsureFeatureTypeLayer)msg;
					
					String layerId = ensureLayer.getLayerId();
					groupLayerContent.add(new LayerRef(layerId, false));
					if(featureTypes.containsKey(layerId)) {
						log.debug("existing feature type found: " + layerId);
						
						FeatureType featureType = featureTypes.get(layerId);
						if(unchanged(featureType, ensureLayer)) {
							log.debug("feature type unchanged");
							toSelf(new LayerEnsured());
						} else {
							log.debug("feature type changed");
							putFeatureType(ensureLayer);
						}
						
						featureTypes.remove(layerId);
					} else {					
						postFeatureType(ensureLayer);
					}
				} else if(msg instanceof LayerEnsured) {
					ensured(provisioningService);				
				} else if(msg instanceof FinishEnsure) {
					if(groupLayer == null) {
						log.debug("deleting removed items");
						
						List<CompletableFuture<Void>> futures = new ArrayList<>();
						for(FeatureType featureType : featureTypes.values()) {
							log.debug("deleting feature type {}", featureType.getName());
							futures.add(rest.deleteFeatureType(workspace, dataStore, featureType));
						}
						
						for(LayerGroup layerGroup : layerGroups.values()) {
							log.debug("deleting layer group {}", layerGroup.getName());
							futures.add(rest.deleteLayerGroup(workspace, layerGroup));
						}
						
						toSelf(f.sequence(futures).thenApply(v -> new WorkspaceEnsured()));
					} else {
						String groupLayerId = groupLayer.getLayerId();
						
						log.debug("unbecome group {}, groupLayers {}", groupLayerId, groupLayerContent);
						
						if(layerGroups.containsKey(groupLayerId)) {
							log.debug("existing layer group found: " + groupLayerId);
							
							LayerGroup layerGroup = layerGroups.get(groupLayerId);
							if(unchanged(layerGroup)) {
								log.debug("layer group unchanged");
								toSelf(new GroupEnsured());
							} else {
								log.debug("layer group changed");
								putLayerGroup();
							}
							
							layerGroups.remove(groupLayerId);
						} else {
							postLayerGroup();
						}
					}
				} else if(msg instanceof WorkspaceEnsured) {
					log.debug("ack");
					
					ensured(provisioningService);
					initiator.tell(new UpdateJobState(JobState.SUCCEEDED), getSelf());
					getContext().unbecome();
				} else if(msg instanceof GroupEnsured) {
					ensured(provisioningService);
					getContext().unbecome();
				} else {
					elseProvisioning(msg, serviceJob, initiator);
				}
			}				
		};
	}
	
	private static class EnsuringWorkspace {
		
		private final Workspace workspace;
		
		private final DataStore dataStore;
		
		private final Map<String, FeatureType> featureTypes;
		
		private final Map<String, LayerGroup> layerGroups;
		
		EnsuringWorkspace(Workspace workspace, DataStore dataStore) {
			this(workspace, dataStore, new HashMap<>(), new HashMap<>());
		}
		
		EnsuringWorkspace(Workspace workspace, DataStore dataStore, Map<String, FeatureType> featureTypes, Map<String, LayerGroup> layerGroups) {
			this.workspace = workspace;
			this.dataStore = dataStore;
			this.featureTypes = featureTypes;
			this.layerGroups = layerGroups;
		} 
		
		Workspace getWorkspace() {
			return workspace;
		}
		
		DataStore getDataStore() {
			return dataStore;
		}
		
		Map<String, FeatureType> getFeatureTypes() {
			return featureTypes;
		}
		
		Map<String, LayerGroup> getLayerGroups() {
			return layerGroups;
		}
	};
	
	private Procedure<Object> provisioning(ActorRef initiator, ServiceJobInfo serviceJob, ActorRef provisioningService) {
		log.debug("-> provisioning");
		
		return new Procedure<Object>() {

			@Override
			public void apply(Object msg) throws Exception {
				if(msg instanceof EnsureWorkspace) {
					initiator.tell(new UpdateJobState(JobState.STARTED), getSelf());
					initiator.tell(new Ack(), getSelf());
					
					String workspaceId = ((EnsureWorkspace)msg).getWorkspaceId();
					
					toSelf(
						rest.getWorkspace(workspaceId).thenCompose(optionalWorkspace -> {
							if(optionalWorkspace.isPresent()) {
								log.debug("existing workspace found: {}", workspaceId);
								
								Workspace workspace = optionalWorkspace.get();								
								return rest.getDataStore(workspace, "publisher-geometry").thenCompose(optionalDataStore -> {
									if(optionalDataStore.isPresent()) {
										log.debug("existing data store found: publisher-geometry");
										
										DataStore dataStore = optionalDataStore.get();										
										return rest.getFeatureTypes(workspace, dataStore)
											.thenCompose(f::sequence)
											.thenCompose(featureTypes ->
												rest.getLayerGroups(workspace)
												.thenCompose(f::sequence)
												.thenApply(layerGroups -> {
											
											Map<String, FeatureType> featureTypesMap = new HashMap<>();
											Map<String, LayerGroup> layerGroupsMap = new HashMap<>();
											
											for(FeatureType featureType : featureTypes) {
												featureTypesMap.put(featureType.getName(), featureType);
											}
											
											for(LayerGroup layerGroup : layerGroups) {
												layerGroupsMap.put(layerGroup.getName(), layerGroup);
											}
											
											return new EnsuringWorkspace(workspace, dataStore, featureTypesMap, layerGroupsMap);
										}));
									}
									
									throw new IllegalStateException("publisher-geometry data store is missing");
								});								
							}
							
							Workspace workspace = new Workspace(workspaceId);							
							return rest.postWorkspace(workspace).thenCompose(vWorkspace -> {
								log.debug("workspace created: {}", workspaceId);
								DataStore dataStore = new DataStore("publisher-geometry", connectionParameters);
								return rest.postDataStore(workspace, dataStore).thenApply(vDataStore -> {									
									log.debug("data store created: publisher-geometry");									
									return new EnsuringWorkspace(workspace, dataStore);
								});
							});
						}));					
				} else if(msg instanceof EnsuringWorkspace) {
					EnsuringWorkspace workspaceEnsured = (EnsuringWorkspace)msg;
					
					ensured(provisioningService);
					
					Workspace workspace = workspaceEnsured.getWorkspace();
					DataStore dataStore = workspaceEnsured.getDataStore();
					Map<String, FeatureType> featureTypes = workspaceEnsured.getFeatureTypes();
					Map<String, LayerGroup> layerGroups = workspaceEnsured.getLayerGroups();
					getContext().become(layers(initiator, serviceJob, provisioningService, workspace, dataStore, featureTypes, layerGroups));
				} else {
					elseProvisioning(msg, serviceJob, initiator);
				}
			}
			
		};
	}

	@Override
	public void postStop() {
		try {
			rest.close();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}	
	
	private void handleServiceJob(ServiceJobInfo serviceJob) {
		log.debug("executing service job: " + serviceJob);
		
		ActorRef provisioningService = getContext().actorOf(
				ProvisionService.props(), 
				nameGenerator.getName(ProvisionService.class));
		
		serviceManager.tell(new GetService(serviceJob.getServiceId()), provisioningService);
		
		getContext().become(provisioning(getSender(), serviceJob, provisioningService));
	}
}
