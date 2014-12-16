package nl.idgis.publisher.harvester.sources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import nl.idgis.publisher.collector.Collector;
import nl.idgis.publisher.collector.messages.GetMessage;

import nl.idgis.publisher.domain.Log;
import nl.idgis.publisher.domain.service.Dataset;
import nl.idgis.publisher.domain.service.Type;

import nl.idgis.publisher.harvester.sources.messages.GetDataset;
import nl.idgis.publisher.harvester.sources.messages.GetDatasetMetadata;
import nl.idgis.publisher.harvester.sources.messages.ListDatasets;
import nl.idgis.publisher.harvester.sources.messages.StartImport;
import nl.idgis.publisher.harvester.sources.mock.ProviderMock;
import nl.idgis.publisher.harvester.sources.mock.messages.PutDataset;
import nl.idgis.publisher.metadata.MetadataDocument;
import nl.idgis.publisher.protocol.messages.Ack;
import nl.idgis.publisher.provider.protocol.Attachment;
import nl.idgis.publisher.provider.protocol.AttachmentType;
import nl.idgis.publisher.provider.protocol.Column;
import nl.idgis.publisher.provider.protocol.Record;
import nl.idgis.publisher.provider.protocol.Records;
import nl.idgis.publisher.provider.protocol.TableDescription;
import nl.idgis.publisher.provider.protocol.VectorDatasetInfo;
import nl.idgis.publisher.recorder.Recorder;
import nl.idgis.publisher.stream.messages.End;
import nl.idgis.publisher.stream.messages.NextItem;
import nl.idgis.publisher.utils.SyncAskHelper;

public class ProviderDataSourceTest {
	
	static VectorDatasetInfo vectorDatasetInfo;
	
	ActorSystem actorSystem;
	
	ActorRef recorder, provider, providerDataSource;
	
	SyncAskHelper sync;
	
	@BeforeClass
	public static void initStatics() throws Exception {
		InputStream inputStream = ProviderDataSourceTest.class.getResourceAsStream("/nl/idgis/publisher/metadata/dataset.xml");
		assertNotNull("metadata document missing", inputStream);
		
		byte[] metadataContent = IOUtils.toByteArray(inputStream);
		
		Set<Attachment> attachments = new HashSet<>();
		attachments.add(new Attachment("metadata", AttachmentType.METADATA, metadataContent));
		
		Set<Log> logs = new HashSet<>();
		
		Column[] columns = {new Column("id", Type.NUMERIC), new Column("title", Type.TEXT)};
		TableDescription tableDescription = new TableDescription(columns);
		
		vectorDatasetInfo = new VectorDatasetInfo("vectorDataset", "vectorDatasetTitle", "categoryId", new Date(), attachments, logs, "tableName", tableDescription, 42);				
	}
	
	@Before
	public void actorSystem() {
		Config config = ConfigFactory.empty().withValue("akka.loglevel", ConfigValueFactory.fromAnyRef("DEBUG"));		
		actorSystem = ActorSystem.create("test", config);
		
		recorder = actorSystem.actorOf(Recorder.props(), "recorder");
		
		provider = actorSystem.actorOf(ProviderMock.props(recorder), "providerMock");		
		providerDataSource = actorSystem.actorOf(ProviderDataSource.props(provider), "providerDataSource");
		
		sync = new SyncAskHelper(actorSystem);
	}
	
	@After
	public void stopActorSystem() {
		actorSystem.shutdown();
	}
	
	@Test
	public void testListDatasets() throws Exception {
		sync.ask(providerDataSource, new ListDatasets(), End.class);		
		
		sync.ask(provider, new PutDataset(vectorDatasetInfo), Ack.class);
		sync.ask(providerDataSource, new ListDatasets(), Dataset.class);
		sync.askSender(new NextItem(), End.class);
	}
	
	@Test
	public void testGetDatasetMetadata() throws Exception {
		sync.ask(provider, new PutDataset(vectorDatasetInfo), Ack.class);		
		sync.ask(providerDataSource, new GetDatasetMetadata("vectorDataset"), MetadataDocument.class);		
	}
	
	public static class DatasetReceiver extends UntypedActor {
		
		private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
		
		private final ActorRef collector;
		
		private final List<Record> records = new ArrayList<>();
		
		public DatasetReceiver(ActorRef collector) {
			this.collector = collector;
		}
		
		static Props props(ActorRef collector) {
			return Props.create(DatasetReceiver.class, collector);
		}

		@Override
		public void onReceive(Object msg) throws Exception {
			if(msg instanceof StartImport) {
				log.debug("start import received");
				
				getSender().tell(new Ack(), getSelf());
			} else if(msg instanceof Records) {
				log.debug("records received");
				
				records.addAll(((Records)msg).getRecords());
				getSender().tell(new NextItem(), getSelf());
			} else if(msg instanceof End) {
				log.debug("end received");
				
				collector.tell(records, getSelf());
				
				getContext().stop(getSelf());
			} else {
				unhandled(msg);
			}
		}
		
	}
	
	@Test
	public void testGetDataset() throws Exception {
		Set<Record> records = new HashSet<>();
		for(int i = 0; i < 42; i++) {
			records.add(new Record(Arrays.<Object>asList(i, "title" + i)));
		}
		
		sync.ask(provider, new PutDataset(vectorDatasetInfo, records), Ack.class);
		
		ActorRef collector = actorSystem.actorOf(Collector.props(), "collector");
		sync.ask(providerDataSource, new GetDataset("vectorDataset", Arrays.asList("id", "title"), DatasetReceiver.props(collector)), Ack.class);
		
		List<?> returnedRecords = sync.ask(collector, new GetMessage(), List.class);
		assertEquals(42, returnedRecords.size());
	}
}
