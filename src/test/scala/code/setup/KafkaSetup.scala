package code.setup

import bootstrap.liftweb.Boot
import code.kafka.{KafkaHelper, Topics}
import code.kafka.Topics.TopicTrait
import code.util.Helper.MdcLoggable
import net.liftweb.json
import net.liftweb.json.{DefaultFormats, Extraction}
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{FeatureSpec, _}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.{ClassTag, classTag}

trait KafkaSetup extends FeatureSpec with EmbeddedKafka with KafkaHelper
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

  def runWithKafka[T](runner: => T): T = {
    withRunningKafka {
      createCustomTopic("Request", Map.empty, 10, 1)
      createCustomTopic("Response", Map.empty, 10, 1)
      runner
    }
  }

  /**
    * send an object to kafka as response
    *
    * @param inBound inBound object that will send to kafka as a response
    * @tparam T Outbound type
    */
  def dispathResponse[T <: TopicTrait : ClassTag](inBound: AnyRef): Unit = {
    val outboundSimpleClassName = classTag[T].runtimeClass.getSimpleName
    val topics = Topics.createTopicByClassName(outboundSimpleClassName)
    val inBoundStr = json.compactRender(Extraction.decompose(inBound))
    val (key, _) = consumeFirstKeyedMessageFrom[String, String](topics.request)
    publishToKafka(topics.response, key, inBoundStr)
  }

  def getOutbound[T: Manifest](response: Future[json.JValue], waitTime: Duration = (10 second)) = {
    val value = Await.result(response, waitTime)
    value.extract[T]
  }

    //     connector=kafka_vSept2018
    //api_instance_id=1
    //remotedata.timeout=30
}
