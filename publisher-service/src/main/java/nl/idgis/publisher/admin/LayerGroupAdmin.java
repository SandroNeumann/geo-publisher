package nl.idgis.publisher.admin;

import static nl.idgis.publisher.database.QGenericLayer.genericLayer;
import static nl.idgis.publisher.database.QLayerStructure.layerStructure;
import static nl.idgis.publisher.database.QLayerStyle.layerStyle;
import static nl.idgis.publisher.database.QLeafLayer.leafLayer;
import static nl.idgis.publisher.database.QStyle.style;
import static nl.idgis.publisher.database.QService.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.mysema.query.sql.SQLSubQuery;

import nl.idgis.publisher.domain.query.GetGroupStructure;
import nl.idgis.publisher.domain.query.GetLayerServices;
import nl.idgis.publisher.domain.query.PutGroupStructure;
import nl.idgis.publisher.domain.query.PutLayerStyles;
import nl.idgis.publisher.domain.response.Page;
import nl.idgis.publisher.domain.response.Response;
import nl.idgis.publisher.domain.service.CrudOperation;
import nl.idgis.publisher.domain.service.CrudResponse;
import nl.idgis.publisher.domain.web.LayerGroup;
import nl.idgis.publisher.domain.web.NotFound;
import nl.idgis.publisher.domain.web.QLayerGroup;
import nl.idgis.publisher.domain.web.tree.GroupLayer;
import nl.idgis.publisher.domain.web.tree.Service;
import nl.idgis.publisher.protocol.messages.Failure;
import nl.idgis.publisher.service.manager.messages.GetGroupLayer;
import nl.idgis.publisher.utils.StreamUtils;
import nl.idgis.publisher.service.manager.messages.GetServicesWithLayer;
import nl.idgis.publisher.utils.TypedIterable;
import akka.actor.ActorRef;
import akka.actor.Props;

public class LayerGroupAdmin extends AbstractAdmin {
	
	private final ActorRef serviceManager;
	
	public LayerGroupAdmin(ActorRef database, ActorRef serviceManager) {
		super(database); 
		
		this.serviceManager = serviceManager;
	}
	
	public static Props props(ActorRef database, ActorRef serviceManager) {
		return Props.create(LayerGroupAdmin.class, database, serviceManager);
	}

	@Override
	protected void preStartAdmin() {
		doList(LayerGroup.class, this::handleListLayergroups);
		doGet(LayerGroup.class, this::handleGetLayergroup);
		doPut(LayerGroup.class, this::handlePutLayergroup);
		doDelete(LayerGroup.class, this::handleDeleteLayergroup);
		
		doQueryOptional(GetLayerServices.class, this::handleGetLayerServices);
		
		doQueryOptional(GetGroupStructure.class, this::handleGetGroupStructure);
		doQuery(PutGroupStructure.class, this::handlePutGroupStructure);

	}

	private CompletableFuture<Optional<List<String>>> handleGetLayerServices (GetLayerServices getLayerServices) {
		log.debug ("handleGetLayerServices of layer: " + getLayerServices.layerId());
		return f.ask(this.serviceManager, new GetServicesWithLayer(getLayerServices.layerId())).thenApply(resp -> {
			if(resp instanceof NotFound) {
				return Optional.empty();
			} else if (resp instanceof Failure){
				return Optional.empty();
			} else {
				TypedIterable<String> services = (TypedIterable<String>)resp;
				List<String> serviceList = new ArrayList<String>(services.asCollection());
				return Optional.of(serviceList);
			}
		});
	}

	private CompletableFuture<Optional<GroupLayer>> handleGetGroupStructure (GetGroupStructure getGroupStructure) {
		log.debug ("handleListLayergroups");
		return f.ask(this.serviceManager, new GetGroupLayer(getGroupStructure.groupId())).thenApply(resp -> {
			if(resp instanceof NotFound) {
				return Optional.empty();
			} else if (resp instanceof Failure){
				return Optional.empty();
			} else {
				return Optional.of((GroupLayer)resp);
			}
		});
	}

	private CompletableFuture<Page<LayerGroup>> handleListLayergroups () {
		log.debug ("handleListLayergroups");
		return 
			db.query().from(genericLayer)
			.leftJoin(leafLayer).on(genericLayer.id.eq(leafLayer.genericLayerId))
			.where(leafLayer.genericLayerId.isNull())
			.list(new QLayerGroup(
					genericLayer.identification,
					genericLayer.name,
					genericLayer.title, 
					genericLayer.abstractCol,
					genericLayer.published
				))
			.thenApply(this::toPage);
	}

	
	private CompletableFuture<Optional<LayerGroup>> handleGetLayergroup (String layergroupId) {
		log.debug ("handleGetLayergroup: " + layergroupId);
		return 
			db.query().from(genericLayer)
			.where(genericLayer.identification.eq(layergroupId))
			.singleResult(new QLayerGroup(
					genericLayer.identification,
					genericLayer.name,
					genericLayer.title, 
					genericLayer.abstractCol,
					genericLayer.published
			));		
	}
	
	private CompletableFuture<Response<?>> handlePutLayergroup(LayerGroup theLayergroup) {
		String layergroupId = theLayergroup.id();
		String layergroupName = theLayergroup.name();
		log.debug ("handle update/create layergroup: " + layergroupId);
		
		return db.transactional(tx ->
			// Check if there is another layergroup with the same id
			tx.query().from(genericLayer)
			.where(genericLayer.identification.eq(layergroupId))
			.singleResult(genericLayer.identification)
			.thenCompose(msg -> {
				if (!msg.isPresent()){
					// INSERT
					log.debug("Inserting new layergroup with name: " + layergroupName);
					String identification = UUID.randomUUID().toString();
					return tx.insert(genericLayer)
					.set(genericLayer.identification, identification)
					.set(genericLayer.name, layergroupName)
					.set(genericLayer.title, theLayergroup.title())
					.set(genericLayer.abstractCol, theLayergroup.abstractText())
					.set(genericLayer.published, theLayergroup.published())
					.execute()
					.thenApply(l -> new Response<String>(CrudOperation.CREATE, CrudResponse.OK, identification));
				} else {
					// UPDATE
					log.debug("Updating layergroup with name: " + layergroupName + ", id:" + layergroupId);
					return tx.update(genericLayer)
							.set(genericLayer.title, theLayergroup.title())
							.set(genericLayer.abstractCol, theLayergroup.abstractText())
							.set(genericLayer.published, theLayergroup.published())
					.where(genericLayer.identification.eq(layergroupId))
					.execute()
					.thenApply(l -> new Response<String>(CrudOperation.UPDATE, CrudResponse.OK, layergroupId));
				}
		}));
	}

	private CompletableFuture<Response<?>> handleDeleteLayergroup(String layergroupId) {
		log.debug("handleDeleteLayergroup: " + layergroupId);
		//first check if generic layer is available and is not a service rootgroup
		return db.transactional(tx -> 
			tx.query()
			.from(genericLayer)
			.leftJoin(service).on(genericLayer.id.eq(service.rootgroupId))
			.where(genericLayer.identification.eq(layergroupId)
					.and(service.id.isNull())
					)
			.limit(1)
			.singleResult(genericLayer.id)
			.thenCompose(
				glId -> {
					if (glId.isPresent()){
						// remove from layerStructure if present in parent or child
						log.debug("delete layerstructures " + glId.get());
						return 
							tx.delete(layerStructure)
							.where(layerStructure.parentLayerId.eq(glId.get()).or(
								   layerStructure.childLayerId.eq(glId.get())))
							.execute()
							.thenCompose(
								nr -> {
									log.debug("LayerStructures deleted: #" + nr);
									log.debug("delete genericLayer: " + glId.get());
									return 
										tx.delete(genericLayer)
										.where(genericLayer.id.eq(glId.get()))
										.execute()
										.thenApply(
											l -> new Response<String>(CrudOperation.DELETE,
												CrudResponse.OK, layergroupId));
							});
						} else {
							// generic layer id not in table
							log.debug("delete genericLayer failed: " + layergroupId);
							return f.successful(new Response<String>(CrudOperation.DELETE,
									CrudResponse.NOK, layergroupId));
						}
					})
				);
	}
	
	private CompletableFuture<Response<?>> handlePutGroupStructure (final PutGroupStructure putGroupStructure) {
		String groupId = putGroupStructure.groupId();
		List<String> layerIdList =  putGroupStructure.layerIdList();
		log.debug("handlePutGroupStructure groupId: " + groupId + ", layer id's: " +layerIdList);
		return db.transactional(tx -> tx
			.query()
			.from(genericLayer)
			.where(genericLayer.identification.eq(groupId))
			.singleResult(genericLayer.id)
			.thenCompose(
				glId -> {
				log.debug("genericlayer id: " + glId.get());
					// A. delete the existing structure of this layer
					return tx.delete(layerStructure)
						.where(layerStructure.parentLayerId.eq(glId.get()))
						.execute()
						.thenCompose(
							llNr -> {
								// B. insert items of layerStructure
								return f.sequence(												
									StreamUtils.index(layerIdList.stream())
										.map(layerIdIndexed -> 
											tx
												.insert(layerStructure)
												.columns(
													layerStructure.parentLayerId, 
													layerStructure.childLayerId,
													layerStructure.layerOrder)
												.select(new SQLSubQuery().from(genericLayer)
													.where(genericLayer.identification.eq(layerIdIndexed.getValue()))
													.list(
														glId.get(),
														genericLayer.id,
														layerIdIndexed.getIndex()))
												.execute()
												)
										.collect(Collectors.toList())).thenApply(whatever ->
											new Response<String>(CrudOperation.UPDATE,
												CrudResponse.OK, groupId));												
							});
		}));
	}
	
}
