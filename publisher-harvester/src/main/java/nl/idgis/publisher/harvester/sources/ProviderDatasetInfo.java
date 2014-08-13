package nl.idgis.publisher.harvester.sources;

import java.util.ArrayList;
import java.util.List;

import scala.concurrent.Future;

import nl.idgis.publisher.domain.service.Column;
import nl.idgis.publisher.domain.service.Dataset;
import nl.idgis.publisher.domain.service.Table;
import nl.idgis.publisher.harvester.metadata.messages.GetAlternateTitle;
import nl.idgis.publisher.harvester.metadata.messages.GetTitle;
import nl.idgis.publisher.harvester.metadata.messages.ParseMetadataDocument;
import nl.idgis.publisher.harvester.sources.messages.Finished;
import nl.idgis.publisher.protocol.messages.Failure;
import nl.idgis.publisher.provider.protocol.database.DescribeTable;
import nl.idgis.publisher.provider.protocol.database.TableDescription;
import nl.idgis.publisher.provider.protocol.database.TableNotFound;
import nl.idgis.publisher.provider.protocol.metadata.MetadataItem;
import nl.idgis.publisher.stream.messages.End;
import nl.idgis.publisher.stream.messages.NextItem;
import nl.idgis.publisher.utils.Ask;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.dispatch.OnComplete;
import akka.dispatch.OnSuccess;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;

public class ProviderDatasetInfo extends UntypedActor {
	
	private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	
	private final ActorRef harvesterSession, harvester, database;
	
	public ProviderDatasetInfo(ActorRef harvesterSession, ActorRef harvester, ActorRef database) {
		this.harvesterSession = harvesterSession;
		this.harvester = harvester;
		this.database = database;		
	}
	
	public static Props props(ActorRef harvesterSession, ActorRef harvester, ActorRef database) {
		return Props.create(ProviderDatasetInfo.class, harvesterSession, harvester, database);
	}
	
	private OnSuccess<Object> processDocument(final ActorRef sender, final MetadataItem metadataItem) {
		return new OnSuccess<Object>() {

			@Override
			public void onSuccess(Object msg) throws Throwable {
				ActorRef metadataDocument = (ActorRef)msg;
				
				Future<Object> title = Patterns.ask(metadataDocument, new GetTitle(), 15000);
				final Future<Object> alternateTitle = Patterns.ask(metadataDocument, new GetAlternateTitle(), 15000);
				
				title.onSuccess(new OnSuccess<Object>() {

					@Override
					public void onSuccess(Object o) throws Throwable {
						final String title = (String)o;
						
						log.debug("metadata title: " + title);
						
						alternateTitle.onSuccess(new OnSuccess<Object>() {

							@Override
							public void onSuccess(Object o) throws Throwable {
								String alternateTitle = (String)o;
								
								log.debug("metadata alternate title: " + alternateTitle);
								
								processMetadata(sender, metadataItem.getIdentification(), title, alternateTitle);
							}
							
						}, getContext().dispatcher());
					}
					
				}, getContext().dispatcher());
			}
		};
	}
	
	private void processMetadata(final ActorRef sender, final String identification, final String title, final String alternateTitle) {
		final String tableName = ProviderUtils.getTableName(alternateTitle);
		if(tableName == null) {
			log.warning("couldn't determine table name: " + alternateTitle);
			
			sender.tell(new NextItem(), getSelf());
		} else {
			final String categoryId = ProviderUtils.getCategoryId(alternateTitle);
			if(categoryId == null) {
				log.warning("couldn't determine category id: " + alternateTitle);
				
				sender.tell(new NextItem(), getSelf());
			} else {				
				Future<Object> tableDescriptionFuture = Ask.ask(getContext(), database, new DescribeTable(tableName), 15000);
				tableDescriptionFuture.onComplete(new OnComplete<Object>() {
	
					@Override
					public void onComplete(Throwable t, Object msg) throws Throwable {
						if(t != null) {
							log.error("couldn't fetch table description: " + t);
							sender.tell(new NextItem(), getSelf());
						} else {
							if(msg instanceof TableNotFound) {
								log.error("table doesn't exist: " + tableName);
								sender.tell(new NextItem(), getSelf());
							} else {								
								TableDescription tableDescription = (TableDescription)msg;
								
								log.debug("table description received");
								
								List<Column> columns = new ArrayList<Column>();
								for(nl.idgis.publisher.provider.protocol.database.Column column : tableDescription.getColumns()) {
									columns.add(new Column(column.getName(), column.getType()));
								}
								
								Table table = new Table(title, columns);
								
								Patterns.ask(harvesterSession, new Dataset(identification, categoryId, table), 15000)
									.onSuccess(new OnSuccess<Object>() {

										@Override
										public void onSuccess(Object msg) throws Throwable {
											log.debug("dataset provided to harvester " + msg.toString());
											
											sender.tell(new NextItem(), getSelf());
										}
									}, getContext().dispatcher());
							}
						}
					}
				}, getContext().dispatcher());
			}
		}
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if(msg instanceof MetadataItem) {
			log.debug("metadata item received");
			
			final MetadataItem metadataItem = (MetadataItem)msg;
			
			Patterns.ask(harvester, new ParseMetadataDocument(metadataItem.getContent()), 15000)
				.onSuccess(processDocument(getSender(), metadataItem), 
					getContext().dispatcher());		
			
		} else if(msg instanceof End) {	
			log.debug("dataset retrieval completed");
			
			finish();
		} else if(msg instanceof Failure) {
			log.error(msg.toString());
			
			finish();
		} else {
			unhandled(msg);
		}
	}
	
	private void finish() {
		harvesterSession.tell(new Finished(), getSelf());
		getContext().stop(getSelf());
	}
}
