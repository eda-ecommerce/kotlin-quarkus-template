package org.eda.ecommerce.integration

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.kafka.InjectKafkaCompanion
import io.quarkus.test.kafka.KafkaCompanionResource
import io.restassured.RestAssured.given
import io.smallrye.common.annotation.Identifier
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion
import io.vertx.core.json.JsonObject
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.eda.ecommerce.data.models.events.TestEntityEvent
import org.eda.ecommerce.data.repositories.TestEntityRepository
import org.eda.ecommerce.helpers.KafkaTestHelper
import org.junit.jupiter.api.*
import java.time.Duration


@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestEntityTest {

    @InjectKafkaCompanion
    lateinit var companion: KafkaCompanion

    @Inject
    @Identifier("default-kafka-broker")
    lateinit var kafkaConfig: Map<String, Any>

    @Inject
    lateinit var testEntityRepository: TestEntityRepository

    lateinit var consumer: KafkaConsumer<String, TestEntityEvent>


    @BeforeEach
    @Transactional
    fun cleanRepositoryAndKafkaTopics() {
        KafkaTestHelper.clearTopicIfNotEmpty(companion, "test-entity")
        KafkaTestHelper.clearTopicIfNotEmpty(companion, "test-entity")

        consumer = KafkaTestHelper.setupConsumer<TestEntityEvent>(kafkaConfig)

        testEntityRepository.deleteAll()
    }

    @AfterEach
    fun unsubscribeConsumer() {
        KafkaTestHelper.deleteConsumer(consumer)
    }

    @Test
    fun testCreationAndPersistenceOnPost() {
        val jsonBody: JsonObject = JsonObject()
            .put("value", "test")

        given()
            .contentType("application/json")
            .body(jsonBody.toString())
            .`when`().post("/entity")
            .then()
            .statusCode(201)

        Assertions.assertEquals(1, testEntityRepository.count())
        Assertions.assertEquals(jsonBody.getValue("value"), testEntityRepository.findById(1L).value)
    }

    @Test
    fun testKafkaEmitOnPost() {
        consumer.subscribe(listOf("test-entity"))

        val jsonBody: JsonObject = JsonObject()
            .put("value", "a value")

        given()
            .contentType("application/json")
            .body(jsonBody.toString())
            .`when`().post("/entity")
            .then()
            .statusCode(201)

        val records: ConsumerRecords<String, TestEntityEvent> = consumer.poll(Duration.ofMillis(10000))

        val event = records.records("test-entity").iterator().asSequence().toList().first()
        val eventPayload = event.value()

        Assertions.assertEquals(jsonBody.getValue("value"), eventPayload.payload.value)
    }

    @Test
    fun testDelete() {
        consumer.subscribe(listOf("test-entity"))

        val jsonBody: JsonObject = JsonObject()
            .put("value", "Test")

        given()
            .contentType("application/json")
            .body(jsonBody.toString())
            .`when`().post("/entity")
            .then()
            .statusCode(201)

        Assertions.assertEquals(1, testEntityRepository.count())

        val createdId = testEntityRepository.listAll()[0].id

        given()
            .contentType("application/json")
            .`when`()
            .queryParam("id", createdId)
            .delete("/entity")
            .then()
            .statusCode(202)

        val records: ConsumerRecords<String, TestEntityEvent> = consumer.poll(Duration.ofMillis(10000))

        val event = records.records("test-entity").iterator().asSequence().toList()[1]
        val eventPayload = event.value()

        Assertions.assertEquals("test-service", eventPayload.source)
        Assertions.assertEquals("deleted", eventPayload.type)
        Assertions.assertEquals(createdId, eventPayload.payload.id)
        Assertions.assertEquals(null, eventPayload.payload.value)

        Assertions.assertEquals(0, testEntityRepository.count())
    }

    @Test
    fun testUpdate() {
        consumer.subscribe(listOf("test-entity"))

        val jsonBody: JsonObject = JsonObject()
            .put("value", "A thing")

        given()
            .contentType("application/json")
            .body(jsonBody.toString())
            .`when`().post("/entity")
            .then()
            .statusCode(201)

        Assertions.assertEquals(1, testEntityRepository.count())

        val createdId = testEntityRepository.listAll()[0].id

        val jsonBodyUpdated: JsonObject = JsonObject()
            .put("id", createdId)
            .put("color", "Another thing")

        given()
            .contentType("application/json")
            .body(jsonBodyUpdated.toString())
            .`when`()
            .put("/entity")
            .then()
            .statusCode(202)

        val records: ConsumerRecords<String, TestEntityEvent> = consumer.poll(Duration.ofMillis(10000))

        val event = records.records("test-entity").iterator().asSequence().toList()[1]
        val eventPayload = event.value()

        Assertions.assertEquals("test-service", eventPayload.source)
        Assertions.assertEquals("updated", eventPayload.type)
        Assertions.assertEquals(createdId, eventPayload.payload.id)
        Assertions.assertEquals(jsonBodyUpdated.getValue("value"), eventPayload.payload.value)

        Assertions.assertEquals(1, testEntityRepository.count())
    }

}
