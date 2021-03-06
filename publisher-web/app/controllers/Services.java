package controllers;

import static models.Domain.from;

import java.util.UUID;

import controllers.Layers.LayerForm;
import controllers.Styles.StyleForm;
import models.Domain;
import models.Domain.Function;
import models.Domain.Function2;
import models.Domain.Function3;
import nl.idgis.publisher.domain.query.GetGroupStructure;
import nl.idgis.publisher.domain.response.Page;
import nl.idgis.publisher.domain.response.Response;
import nl.idgis.publisher.domain.service.CrudOperation;
import nl.idgis.publisher.domain.web.Category;
import nl.idgis.publisher.domain.web.LayerGroup;
import nl.idgis.publisher.domain.web.Service;
import nl.idgis.publisher.domain.web.Style;
import nl.idgis.publisher.domain.web.tree.GroupLayer;
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
import actions.DefaultAuthenticator;
import akka.actor.ActorSelection;
import views.html.services.form;
import views.html.services.list;


@Security.Authenticated (DefaultAuthenticator.class)
public class Services extends Controller {

	private final static String databaseRef = Play.application().configuration().getString("publisher.database.actorRef");
	private final static String ID="#CREATE_SERVICE#";
	
	private static Promise<Result> renderCreateForm (final Form<ServiceForm> serviceForm) {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);
		return from (database)
				.list (Category.class)
				.list (LayerGroup.class)
				.execute (new Function2<Page<Category>, Page<LayerGroup>, Result> () {

					@Override
					public Result apply (final Page<Category> categories, final Page<LayerGroup> groups) throws Throwable {
						return ok (form.render (serviceForm, true, categories, groups, null));
					}
				});
	}
	
	
	public static Promise<Result> submitCreateUpdate () {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);
		return from (database)
			.list (Service.class)
			.executeFlat (new Function<Page<Service>, Promise<Result>> () {
	
				@Override
				public Promise<Result> apply (final Page<Service> services) throws Throwable {
					final Form<ServiceForm> form = Form.form (ServiceForm.class).bindFromRequest ();
					final ServiceForm serviceForm = form.get ();
					Logger.debug ("submit Service: " + form.field("name").value());
					Logger.debug ("Form: "+ form);
					// validation start
					if (form.field("name").value().length() == 1 ) 
						form.reject("name", Domain.message("web.application.page.services.form.field.name.validation.error", "1"));
					if (form.field("id").value().equals(ID)){
						for (Service service : services.values()) {
							if (form.field("name").value().equals(service.name())){
								form.reject("name", Domain.message("web.application.page.services.form.field.name.validation.exists.error"));
							}
						}
					}
					if (form.field("rootGroupName").value()==null || form.field("rootGroupName").value().isEmpty()){
						form.reject("rootGroupName", Domain.message("web.application.page.services.form.field.rootgroup.validation.error"));
					}
					if (form.hasErrors ()) {
						return renderCreateForm (form);
					}
					// validation end
					
					final Service service = new Service(serviceForm.id, serviceForm.name, serviceForm.title, 
							serviceForm.alternateTitle,serviceForm.abstractText,serviceForm.keywords,
							serviceForm.metadata,serviceForm.watermark, serviceForm.published,
							serviceForm.rootGroupName,serviceForm.categoryName, serviceForm.constantsName);
					Logger.debug ("Update/create service: " + service);
					
					return from (database)
						.put(service)
						.executeFlat (new Function<Response<?>, Promise<Result>> () {
							@Override
							public Promise<Result> apply (final Response<?> response) throws Throwable {
								if (CrudOperation.CREATE.equals (response.getOperation())) {
									Logger.debug ("Created service " + service);
									flash ("success", Domain.message("web.application.page.services.name") + " " + serviceForm.getName () + " is " + Domain.message("web.application.added").toLowerCase());
								}else{
									Logger.debug ("Updated service " + service);
									flash ("success", Domain.message("web.application.page.services.name") + " " + serviceForm.getName () + " is " + Domain.message("web.application.updated").toLowerCase());
								}
								return Promise.pure (redirect (routes.Services.list ()));
							}
						});
				}
			});
	}
	
	public static Promise<Result> list () {
		final ActorSelection database = Akka.system().actorSelection (databaseRef);

		Logger.debug ("list Services ");
		
		return from (database)
			.list (Service.class)
			.execute (new Function<Page<Service>, Result> () {
				@Override
				public Result apply (final Page<Service> services) throws Throwable {
					Logger.debug ("List Service: #" + services.values().size());
					return ok (list.render (services));
				}
			});
	}

	public static Promise<Result> create () {
		Logger.debug ("create Service");
		final Form<ServiceForm> serviceForm = Form.form (ServiceForm.class).fill (new ServiceForm ());
		
		return renderCreateForm (serviceForm);
	}
	
	public static Promise<Result> edit (final String serviceId) {
		Logger.debug ("edit Service: " + serviceId);
		final ActorSelection database = Akka.system().actorSelection (databaseRef);
		
		return from (database)
			.get (Service.class, serviceId)			
			.list(Category.class)
			.list (LayerGroup.class)
			.executeFlat (new Function3<Service, Page<Category>, Page<LayerGroup>, Promise<Result>> () {

				@Override
				public Promise<Result> apply (final Service service, final Page<Category> categories, final Page<LayerGroup> groups) throws Throwable {

					return from (database)
						.get(Category.class, service.defaultCategoryId())
						.get(LayerGroup.class, service.rootGroupId())
						.query (new GetGroupStructure(service.rootGroupId()))
						.execute (new Function3<Category, LayerGroup, GroupLayer, Result> () {
							@Override
							public Result apply (final Category categorie, final LayerGroup group, final GroupLayer groupLayer) throws Throwable {

								ServiceForm  serviceForm = new ServiceForm (service);
								serviceForm.setRootGroupName(group.name());
								serviceForm.setCategoryName(categorie.name());
								Logger.debug ("Edit serviceForm: " + serviceForm);
								
								final Form<ServiceForm> formServiceForm = Form
										.form (ServiceForm.class)
										.fill (serviceForm);
								
								return ok (form.render (formServiceForm, false, categories, groups, groupLayer));
							}
					});
				}
			});
	}

	public static Promise<Result> delete(final String serviceId){
		Logger.debug ("delete Service " + serviceId);
		final ActorSelection database = Akka.system().actorSelection (databaseRef);
		
		from(database).delete(Service.class, serviceId)
		.execute(new Function<Response<?>, Result>() {
			
			@Override
			public Result apply(Response<?> a) throws Throwable {
				return redirect (routes.Services.list ());
			}
		});
		
		return Promise.pure (redirect (routes.Services.list ()));
	}
	
	
	public static class ServiceForm {

		@Constraints.Required
		private String id;
		@Constraints.Required
		@Constraints.MinLength (1)
		private String name;
		private String title;
		private String alternateTitle;
		private String abstractText;
		private String keywords;
		private String metadata;
		private String watermark;
		private Boolean published = false;
		private String categoryName = "";
		private String rootGroupName = "";
		private String constantsName = "";
		
		public ServiceForm (){
			super();
			this.id = ID;		
		}
		
		public ServiceForm (final Service service){
			this.id = service.id();
			this.name = service.name();
			this.title = service.title();
			this.alternateTitle = service.alternateTitle();
			this.abstractText = service.abstractText();
			this.keywords = service.keywords();
			this.metadata = service.metadata();
			this.watermark = service.watermark();
			this.published = service.published();
			this.categoryName =service.defaultCategoryId();
			this.rootGroupName =service.rootGroupId();
			this.constantsName =service.constantsId();
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


		public String getTitle() {
			return title;
		}


		public void setTitle(String title) {
			this.title = title;
		}


		public String getAlternateTitle() {
			return alternateTitle;
		}


		public void setAlternateTitle(String alternateTitle) {
			this.alternateTitle = alternateTitle;
		}


		public String getAbstractText() {
			return abstractText;
		}


		public void setAbstractText(String abstractText) {
			this.abstractText = abstractText;
		}


		public String getKeywords() {
			return keywords;
		}


		public void setKeywords(String keywords) {
			this.keywords = keywords;
		}


		public String getMetadata() {
			return metadata;
		}


		public void setMetadata(String metadata) {
			this.metadata = metadata;
		}


		public String getWatermark() {
			return watermark;
		}


		public void setWatermark(String watermark) {
			this.watermark = watermark;
		}

		public Boolean getPublished() {
			return published;
		}

		public void setPublished(Boolean published) {
			this.published = published;
		}

		public String getCategoryName() {
			return categoryName;
		}

		public void setCategoryName(String categoryName) {
			this.categoryName = categoryName;
		}

		public String getRootGroupName() {
			return rootGroupName;
		}

		public void setRootGroupName(String rootGroupName) {
			this.rootGroupName = rootGroupName;
		}

		public String getConstantsName() {
			return constantsName;
		}

		public void setConstantsName(String constantsName) {
			this.constantsName = constantsName;
		}
		
		@Override
		public String toString() {
			return "ServiceForm [id=" + id + ", name=" + name + ", title=" + title + ", alternateTitle="
					+ alternateTitle + ", abstractText=" + abstractText + ", keywords=" + keywords + ", metadata="
					+ metadata + ", watermark=" + watermark + ", published=" + published + ", categoryName=" + categoryName
					+ ", rootGroupName=" + rootGroupName + ", constantsName=" + constantsName + "]";
		}

	}
}