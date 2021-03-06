package nl.idgis.publisher.stream;

import java.util.concurrent.TimeUnit;

import scala.concurrent.duration.Duration;

import nl.idgis.publisher.stream.messages.End;
import nl.idgis.publisher.stream.messages.Item;
import nl.idgis.publisher.stream.messages.NextItem;
import nl.idgis.publisher.stream.messages.Start;
import nl.idgis.publisher.stream.messages.Stop;

import akka.actor.ActorRef;
import akka.actor.ReceiveTimeout;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public abstract class StreamConverter extends UntypedActor {
	
	private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	
	private ActorRef sender, producer;
	
	@Override
	public final void preStart() throws Exception {
		getContext().setReceiveTimeout(Duration.apply(30, TimeUnit.SECONDS));
	}

	@Override
	public final void onReceive(Object msg) throws Exception {
		if(msg instanceof Start) {
			log.debug("start");
			
			sender = getSender();
			start((Start)msg);
		} else if(msg instanceof End) {
			log.debug("end");
			
			sender.tell(msg, getSelf());
			getContext().stop(getSelf());
		} else if(msg instanceof Item) {
			log.debug("item");
			
			producer = getSender();
			convert((Item)msg, sender);
		} else if(msg instanceof NextItem) {
			log.debug("next item");
			
			sender = getSender();
			producer.tell(msg, getSelf());
		} else if(msg instanceof Stop) {
			log.debug("stop");
			
			producer.tell(msg, getSelf());
			getContext().stop(getSelf());
		} else if(msg instanceof ReceiveTimeout){
			log.error("timeout");
			
			getContext().stop(getSelf());
		} else {
			unhandled(msg);
		}
	}
	
	protected abstract void start(Start msg) throws Exception;
	
	protected abstract void convert(Item msg, ActorRef sender) throws Exception;
	
}
