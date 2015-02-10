package controllers;

import static models.Domain.from;
import models.Domain;
import models.Domain.Function;
import nl.idgis.publisher.domain.response.Page;
import nl.idgis.publisher.domain.response.Response;
import nl.idgis.publisher.domain.service.CrudOperation;
import nl.idgis.publisher.domain.web.TiledLayer;
import play.Logger;
import play.Play;
import play.data.Form;
import play.data.validation.Constraints;
import play.libs.Akka;
import play.libs.F;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import views.html.tiledlayers.form;
import views.html.tiledlayers.list;
import actions.DefaultAuthenticator;
import akka.actor.ActorSelection;

@Security.Authenticated (DefaultAuthenticator.class)
public class Tiledlayers extends Controller {

	private final static String databaseRef = Play.application().configuration().getString("publisher.database.actorRef");

	private static Promise<Result> renderCreateForm (final Form<TiledLayerForm> tiledlayerForm) {
		 return Promise.promise(new F.Function0<Result>() {
             @Override
             public Result apply() throws Throwable {
                 return ok (form.render (tiledlayerForm, true));
             }
         });
	}
	
	public static Promise<Result> submitCreateUpdate () {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);
		final Form<TiledLayerForm> form = Form.form (TiledLayerForm.class).bindFromRequest ();
		Logger.debug ("submit TiledLayer: " + form.field("name").value());
		
		// validation
		if (form.hasErrors ()) {
			return renderCreateForm (form);
		}
		
		final TiledLayerForm tiledlayerForm = form.get ();
		final TiledLayer tiledlayer = new TiledLayer(tiledlayerForm.id, tiledlayerForm.name, 
				tiledlayerForm.mimeFormats,
				tiledlayerForm.metaWidth, tiledlayerForm.metaHeight, 
				tiledlayerForm.expireCache, tiledlayerForm.expireClients,
				tiledlayerForm.gutter, tiledlayerForm.enabled   
				);
		
		return from (database)
			.put(tiledlayer)
			.executeFlat (new Function<Response<?>, Promise<Result>> () {
				@Override
				public Promise<Result> apply (final Response<?> response) throws Throwable {
					if (CrudOperation.CREATE.equals (response.getOperation())) {
						Logger.debug ("Created tiledlayer " + tiledlayer);
						flash ("success", Domain.message("web.application.page.tiledlayers.name") + " " + tiledlayerForm.getName () + " is " + Domain.message("web.application.added").toLowerCase());
					}else{
						Logger.debug ("Updated tiledlayer " + tiledlayer);
						flash ("success", Domain.message("web.application.page.tiledlayers.name") + " " + tiledlayerForm.getName () + " is " + Domain.message("web.application.updated").toLowerCase());
					}
					return Promise.pure (redirect (routes.Tiledlayers.list ()));
				}
			});
	}
	
	public static Promise<Result> list () {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);

		Logger.debug ("list TiledLayers ");
		
		return from (database)
			.list (TiledLayer.class)
			.execute (new Function<Page<TiledLayer>, Result> () {
				@Override
				public Result apply (final Page<TiledLayer> tiledlayers) throws Throwable {
					return ok (list.render (tiledlayers));
				}
			});
	}

	public static Promise<Result> create () {
		Logger.debug ("create TiledLayer");
		final Form<TiledLayerForm> tiledlayerForm = Form.form (TiledLayerForm.class).fill (new TiledLayerForm ());
		
		return renderCreateForm (tiledlayerForm);
	}
	
	public static Promise<Result> edit (final String tiledlayerId) {
		Logger.debug ("edit TiledLayer: " + tiledlayerId);
		final ActorSelection database = Akka.system().actorSelection (databaseRef);
		
		return from (database)
			.get (TiledLayer.class, tiledlayerId)
			.execute (new Function<TiledLayer, Result> () {

				@Override
				public Result apply (final TiledLayer tiledlayer) throws Throwable {
					final Form<TiledLayerForm> tiledlayerForm = Form
							.form (TiledLayerForm.class)
							.fill (new TiledLayerForm (tiledlayer));
					
					Logger.debug ("Edit tiledlayerForm: " + tiledlayerForm);						

					return ok (form.render (tiledlayerForm, false));
				}
			});
	}

	public static Promise<Result> delete(final String tiledlayerId){
		Logger.debug ("delete TiledLayer " + tiledlayerId);
		final ActorSelection database = Akka.system().actorSelection (databaseRef);
		
		from(database).delete(TiledLayer.class, tiledlayerId)
		.execute(new Function<Response<?>, Result>() {
			
			@Override
			public Result apply(Response<?> a) throws Throwable {
				return redirect (routes.Tiledlayers.list ());
			}
		});
		
		return Promise.pure (redirect (routes.Tiledlayers.list ()));
	}
	
	
	public static class TiledLayerForm {
		
		@Constraints.Required
		private String id;
		@Constraints.Required
		@Constraints.MinLength (1)
		private String name;
		@Constraints.Required
		private Boolean enabled = false;
		private String mimeFormats;
		@Constraints.Required
		private Integer metaWidth = 4;
		@Constraints.Required
		private Integer metaHeight = 4;
		@Constraints.Required
		private Integer expireCache = 0;
		@Constraints.Required
		private Integer expireClients = 0;
		@Constraints.Required
		private Integer gutter = 0;
		
		public TiledLayerForm(){
			super();
		}

		public TiledLayerForm(final TiledLayer tl){
			this.id = tl.id();
			this.name = tl.name();
			this.enabled = tl.enabled();
			this.mimeFormats = tl.mimeFormats();
			this.metaWidth = tl.metaWidth();
			this.metaHeight = tl.metaHeight();
			this.expireCache = tl.expireCache();
			this.expireClients = tl.expireClients();
			this.gutter = tl.gutter();
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Boolean getEnabled() {
			return enabled;
		}

		public void setEnabled(Boolean enabled) {
			this.enabled = enabled;
		}

		public String getMimeFormats() {
			return mimeFormats;
		}

		public void setMimeFormats(String mimeFormats) {
			this.mimeFormats = mimeFormats;
		}

		public Integer getMetaWidth() {
			return metaWidth;
		}

		public void setMetaWidth(Integer metaWidth) {
			this.metaWidth = metaWidth;
		}

		public Integer getMetaHeight() {
			return metaHeight;
		}

		public void setMetaHeight(Integer metaHeight) {
			this.metaHeight = metaHeight;
		}

		public Integer getExpireCache() {
			return expireCache;
		}

		public void setExpireCache(Integer expireCache) {
			this.expireCache = expireCache;
		}

		public Integer getExpireClients() {
			return expireClients;
		}

		public void setExpireClients(Integer expireClients) {
			this.expireClients = expireClients;
		}

		public Integer getGutter() {
			return gutter;
		}

		public void setGutter(Integer gutter) {
			this.gutter = gutter;
		}
		
	}
}