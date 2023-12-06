package org.eda.ecommerce.integration

import io.quarkus.test.junit.QuarkusTest
import io.smallrye.common.annotation.Identifier
import jakarta.inject.Inject
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*


@QuarkusTest
class ReEmitterTest {

    @Inject
    @Identifier("default-kafka-broker")
    lateinit var kafkaConfig: Map<String, Any>

    lateinit var testProducer: KafkaProducer<String, String>
    lateinit var testAcknowledgedConsumer: KafkaConsumer<String, String>

    @BeforeEach
    fun setupKafkaHelpers() {
        testAcknowledgedConsumer = KafkaConsumer(consumerConfig(), StringDeserializer(), StringDeserializer())
        testProducer = KafkaProducer(kafkaConfig, StringSerializer(), StringSerializer())
    }

    fun consumerConfig(): Properties {
        val properties = Properties()
        properties.putAll(kafkaConfig)
        properties[ConsumerConfig.GROUP_ID_CONFIG] = "test-group-id"
        properties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "true"
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        return properties
    }

    @AfterEach
    fun tearDown() {
        testProducer.close()
        testAcknowledgedConsumer.close()
    }


    @Test
    fun testReEmitterOnce() {
        testAcknowledgedConsumer.subscribe(listOf("test-acknowledged"))

        testProducer.send(ProducerRecord("test", "A String Value"))

        val records: ConsumerRecords<String, String> = testAcknowledgedConsumer.poll(Duration.ofMillis(10000))

        Assertions.assertEquals(1, records.records("test-acknowledged").count())
        Assertions.assertEquals("A String Value", records.records("test-acknowledged").iterator().next().value())
    }

    @Test
    fun testReEmitterMultiple() {
        testAcknowledgedConsumer.subscribe(listOf("test-acknowledged"))

        testProducer.send(ProducerRecord("test", "first"))
        testProducer.send(ProducerRecord("test", "second"))
        testProducer.send(ProducerRecord("test", "third"))

        val records: ConsumerRecords<String, String> = testAcknowledgedConsumer.poll(Duration.ofMillis(10000))

        Assertions.assertEquals(3, records.count())
        val values = records.records("test-acknowledged").iterator().asSequence().toList().map { it.value() }
        Assertions.assertEquals("first", values[0])
        Assertions.assertEquals("second", values[1])
        Assertions.assertEquals("third", values[2])
    }
}
