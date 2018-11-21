package code.kafka

import bootstrap.liftweb.Boot
import code.bankconnectors.vMar2017.{InboundBank, InboundStatusMessage}
import code.bankconnectors.vSept2018._
import code.util.Helper.MdcLoggable
import net.liftweb.json
import net.liftweb.json.{DefaultFormats, Extraction}
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{FeatureSpec, _}

import scala.collection.immutable.List
import scala.concurrent.duration._
import scala.concurrent.Await

class KafkaTest extends FeatureSpec with EmbeddedKafka with KafkaHelper
  with BeforeAndAfterEach with GivenWhenThen
  with BeforeAndAfterAll
  with Matchers with MdcLoggable {

  override def beforeAll(): Unit = {
    super.beforeAll()
    new Boot().boot
  }

  implicit val formats = DefaultFormats
  implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)
  implicit val stringSerializer = new StringSerializer
  implicit val stringDeserializer = new StringDeserializer

  def runWithKafka(runner: => Unit): Unit = {
    withRunningKafka {
      createCustomTopic("Request", Map.empty, 10, 1)
      createCustomTopic("Response", Map.empty, 10, 1)
      runner
    }
  }

   feature("Send and retrieve message") {
//    scenario("Send and retrieve simple") {
//      runWithKafka {
//        When("send a string message to topic: someTopic")
//        publishStringMessageToKafka("someTopic", "some message=====================")
//        Then("get the message from topic: someTopic")
//        val str = consumeFirstStringMessageFrom("someTopic")
//        str.shouldBe("some message=====================")
//      }
//    }


        scenario("Send and retrieve api message") {
          runWithKafka {
            When("send a string api message")
            val topics = Topics.createTopicByClassName("OutboundGetBanks")
            val emptyStatusMessage = InboundStatusMessage("", "", "", "")
            val inBound = InboundGetBanks(AuthInfo(), Status("", List(emptyStatusMessage)), List(InboundBank("1", "2", "3", "4")))

          val req = OutboundGetBanks(AuthInfo())
          val inBoundStr = json.compactRender(Extraction.decompose(inBound))

//            val eventualValue = KafkaMappedConnector_vSept2018.processToBox[OutboundGetBanks](req)
//            val eventualValue = KafkaMappedConnector_vSept2018.getBanksFuture(None)
          val eventualValue = processToFuture[OutboundGetBanks](req)
//            Thread.sleep(1000)
            val keyAndPayload = consumeFirstKeyedMessageFrom[String, String](topics.request)
            println("xxx:"+keyAndPayload)
            publishToKafka(topics.response, keyAndPayload._1, inBoundStr)
            Await.result(eventualValue, (10 second))
            val serializable = eventualValue.value.getOrElse("").extract[InboundGetBanks]
            println(serializable + "-----------------------------------------------")
          }
        }
     
//     connector=kafka_vSept2018
//api_instance_id=1
//remotedata.timeout=30
  }
}
