package org.eda.ecommerce

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import java.util.concurrent.CompletionStage

@Path("emit")
@ApplicationScoped
class KafkaEmitterOnPost {
    @Inject
    @Channel("test")
    private lateinit var testEmitter: Emitter<Double>


    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Operation(summary = "Accepts a plain Body that is interpreted as a Double and send as an Event into the 'test' topic.")
    fun addPrice(price: Double?) {
        val ack: CompletionStage<Void> = testEmitter.send(price)
    }

}
