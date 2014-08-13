package nl.idgis.publisher.provider.metadata;

import java.io.File;
import java.io.IOException;

import nl.idgis.publisher.provider.protocol.metadata.GetAllMetadata;
import nl.idgis.publisher.provider.protocol.metadata.GetMetadata;
import nl.idgis.publisher.provider.protocol.metadata.MetadataItem;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Metadata extends UntypedActor {
	
	private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	
	private final File metadataDirectory;
	
	private ActorRef listProvider;
	
	public Metadata(File metadataDirectory) {
		if (!metadataDirectory.isDirectory()) {
			throw new IllegalArgumentException("metadataDirectory is not a directory");
		}
		
		this.metadataDirectory = metadataDirectory;
	}
	
	public static Props props(File metadataDirectory) {
		return Props.create(Metadata.class, metadataDirectory);
	}
	
	@Override
	public void preStart() throws Exception {
		listProvider = getContext().actorOf(MetadataListProvider.props(metadataDirectory), "list");
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if(msg instanceof GetAllMetadata) {			
			handleGetAllMetadata((GetAllMetadata)msg);
		} else if(msg instanceof GetMetadata) {
			handleGetMetadata((GetMetadata)msg);
		} else {
			unhandled(msg);
		}
	}

	private void handleGetMetadata(GetMetadata msg) throws IOException {
		String id = msg.getIdentification();
		
		log.debug("fetching single metadata document: " + id);
		File document = new File(metadataDirectory, id + ".xml");

		MetadataItem metadataItem = MetadataParser.createMetadataItem(document);
		getSender().tell(metadataItem, getSelf());
	}

	private void handleGetAllMetadata(GetAllMetadata msg) {
		log.debug("listing all metadata");
		listProvider.tell(msg, getSender());
	}

}
