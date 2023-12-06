package org.eda.ecommerce.services

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter

@ApplicationScoped
class KafkaTestAcknowledgedService {
    @Inject
    @Channel("test-acknowledged-out")
    private lateinit var testAcknowledgedEmitter: Emitter<String>

    fun emitTestMessage(message: String) {
        testAcknowledgedEmitter.send(message)
    }
}
