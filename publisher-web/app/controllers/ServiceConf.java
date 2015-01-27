package controllers;

import static models.Domain.from;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.Domain.Constant;
import models.Domain.Function;
import models.Domain.Function2;
import models.Domain.Function4;
import nl.idgis.publisher.domain.job.ConfirmNotificationResult;
import nl.idgis.publisher.domain.query.DomainQuery;
import nl.idgis.publisher.domain.query.ListDatasetColumnDiff;
import nl.idgis.publisher.domain.query.ListDatasetColumns;
import nl.idgis.publisher.domain.query.ListDatasets;
import nl.idgis.publisher.domain.query.ListSourceDatasetColumns;
import nl.idgis.publisher.domain.query.ListSourceDatasets;
import nl.idgis.publisher.domain.query.PutNotificationResult;
import nl.idgis.publisher.domain.query.RefreshDataset;
import nl.idgis.publisher.domain.response.Page;
import nl.idgis.publisher.domain.response.Response;
import nl.idgis.publisher.domain.service.Column;
import nl.idgis.publisher.domain.service.ColumnDiff;
import nl.idgis.publisher.domain.service.CrudOperation;
import nl.idgis.publisher.domain.service.CrudResponse;
import nl.idgis.publisher.domain.web.Layer;
import nl.idgis.publisher.domain.web.LayerGroup;
import nl.idgis.publisher.domain.web.TiledLayer;
import nl.idgis.publisher.domain.web.Style;
import nl.idgis.publisher.domain.web.Service;
import play.Logger;
import play.Play;
import play.data.Form;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;
import play.libs.Akka;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import views.html.serviceconfig.list;
import actions.DefaultAuthenticator;
import actors.Database;
import akka.actor.ActorSelection;

import com.fasterxml.jackson.databind.node.ObjectNode;

@Security.Authenticated (DefaultAuthenticator.class)
public class ServiceConf extends Controller {
	private final static String databaseRef = Play.application().configuration().getString("publisher.database.actorRef");

	// Layer JSON
	public static Promise<Result> getLayerJson (final String id) {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);

		Logger.debug ("getLayerJson: " + id);
		
		return from (database)
			.get (Layer.class, id)
			.execute (new Function<Layer, Result> () {
				@Override
				public Result apply (final Layer result) throws Throwable {
					Logger.debug ("apply " + result);
					final ObjectNode json = Json.newObject ();
					
					json.put ("id", id);
					
					if (result == null) {
						json.put ("status", "notfound");
						return ok (json);
					}
					
					json.put ("status", "ok");
					json.put ("layer", Json.toJson (result));
					
					return ok (json);
				}
			});
	}

	// LayerGroup JSON
	public static Promise<Result> getLayerGroupJson (final String id) {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);

		Logger.debug ("getLayerGroupJson: " + id);
		
		return from (database)
			.get (LayerGroup.class, id)
			.execute (new Function<LayerGroup, Result> () {
				@Override
				public Result apply (final LayerGroup result) throws Throwable {
					final ObjectNode json = Json.newObject ();
					
					json.put ("id", id);
					
					if (result == null) {
						json.put ("status", "notfound");
						return ok (json);
					}
					
					json.put ("status", "ok");
					json.put ("LayerGroup", Json.toJson (result));
					
					return ok (json);
				}
			});
	}

	// TiledLayer JSON
	public static Promise<Result> getTiledLayerJson (final String id) {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);

		Logger.debug ("getTiledLayerJson: " + id);
		
		return from (database)
			.get (TiledLayer.class, id)
			.execute (new Function<TiledLayer, Result> () {
				@Override
				public Result apply (final TiledLayer result) throws Throwable {
					final ObjectNode json = Json.newObject ();
					
					json.put ("id", id);
					
					if (result == null) {
						json.put ("status", "notfound");
						return ok (json);
					}
					
					json.put ("status", "ok");
					json.put ("TiledLayer", Json.toJson (result));
					
					return ok (json);
				}
			});
	}

	// Style JSON
	public static Promise<Result> getStyleJson (final String id) {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);

		Logger.debug ("getStyleJson: " + id);
		
		return from (database)
			.get (Style.class, id)
			.execute (new Function<Style, Result> () {
				@Override
				public Result apply (final Style result) throws Throwable {
					final ObjectNode json = Json.newObject ();
					
					json.put ("id", id);
					
					if (result == null) {
						json.put ("status", "notfound");
						return ok (json);
					}
					
					json.put ("status", "ok");
					json.put ("Style", Json.toJson (result));
					
					return ok (json);
				}
			});
	}

	// Service JSON
	public static Promise<Result> getServiceJson (final String id) {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);

		Logger.debug ("getServiceJson: " + id);
		
		return from (database)
			.get (Service.class, id)
			.execute (new Function<Service, Result> () {
				@Override
				public Result apply (final Service result) throws Throwable {
					final ObjectNode json = Json.newObject ();
					
					json.put ("id", id);
					
					if (result == null) {
						json.put ("status", "notfound");
						return ok (json);
					}
					
					json.put ("status", "ok");
					json.put ("Service", Json.toJson (result));
					
					return ok (json);
				}
			});
	}

	
	// Layer list 
	public static Promise<Result> listLayers () {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);

		Logger.debug ("listLayers: ");
		
		return from (database)
			.list (Layer.class)
			.execute (new Function<Page<Layer>, Result> () {
				@Override
				public Result apply (final Page<Layer> layers) throws Throwable {
					return ok (list.render (layers.values(),null,null,null,null));
				}
			});
	}

	// TiledLayer list 
	public static Promise<Result> listTiledLayers () {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);

		Logger.debug ("listTiledLayers: ");
		
		return from (database)
			.list (TiledLayer.class)
			.execute (new Function<Page<TiledLayer>, Result> () {
				@Override
				public Result apply (final Page<TiledLayer> layers) throws Throwable {
					return ok (list.render (null,layers.values(),null,null,null));
				}
			});
	}

	// Style list 
	public static Promise<Result> listStyles () {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);

		Logger.debug ("listStyles: ");
		
		return from (database)
			.list (Style.class)
			.execute (new Function<Page<Style>, Result> () {
				@Override
				public Result apply (final Page<Style> styles) throws Throwable {
					return ok (list.render (null,null,null,styles.values(),null));
				}
			});
	}

	// Service list 
	public static Promise<Result> listServices () {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);

		Logger.debug ("listServices: ");
		
		return from (database)
			.list (Service.class)
			.execute (new Function<Page<Service>, Result> () {
				@Override
				public Result apply (final Page<Service> Services) throws Throwable {
					return ok (list.render (null,null,null,null,Services.values()));
				}
			});
	}

	
}
