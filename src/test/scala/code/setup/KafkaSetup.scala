package code.setup

import bootstrap.liftweb.Boot
import code.kafka.{KafkaConsumer, KafkaHelper, NorthSideConsumer, Topics}
import code.util.Helper.MdcLoggable
import net.liftweb.json
import net.liftweb.json.{DefaultFormats, Extraction}
import scala.concurrent.ExecutionContext.Implicits.global
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{FeatureSpec, _}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait KafkaSetup extends FeatureSpec with EmbeddedKafka with KafkaHelper
  with BeforeAndAfterEach with GivenWhenThen
  with BeforeAndAfterAll
  with Matchers with MdcLoggable {



  implicit val formats = DefaultFormats
  implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092) //TODO the port should read from test.default.props, but fail
  implicit val stringSerializer = new StringSerializer
  implicit val stringDeserializer = new StringDeserializer

  lazy val requestMapResponseTopics:Map[String, String] =  NorthSideConsumer.listOfTopics
    .map(Topics.createTopicByClassName)
    .map(pair => (pair.request, pair.response))
    .toMap
  lazy val requestTopics = requestMapResponseTopics.keySet

  override def beforeAll(): Unit = {
    super.beforeAll()
    EmbeddedKafka.start()
    createCustomTopic("Request", Map.empty, 10, 1)
    createCustomTopic("Response", Map.empty, 10, 1)
    if(!KafkaConsumer.primaryConsumer.started){
      new Boot().boot
    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    EmbeddedKafka.stop()
  }

  def runWithKafka[U](inBound: AnyRef, waitTime: Duration = (10 second))(runner: => U): U = {
    try {
      // here is async, this means response is not send before request
      dispathResponse(inBound)
      runner
    } catch {
      case e: Throwable => { // clean kakfa message
        consumeNumberKeyedMessagesFromTopics(requestTopics, 1, true)
        throw e
      }
    }
  }
  def runWithKafkaFuture[U](inBound: AnyRef, waitTime: Duration = (10 second))(runner: => Future[U]): U = {
    this.runWithKafka[U](inBound, waitTime) {
      Await.result(runner, waitTime)
    }
  }

  /**
    * send an object to kafka as response
    *
    * @param inBound inBound object that will send to kafka as a response
    * @tparam T Outbound type
    */
  def dispathResponse(inBound: AnyRef): Unit = {
    val inBoundStr = json.compactRender(Extraction.decompose(inBound))
    Future{
      val requestKeyValue = consumeNumberKeyedMessagesFromTopics(requestTopics, 1, true)
      val (requestTopic, keyValueList) = requestKeyValue.find(_._2.nonEmpty).get
      val (key, _) = keyValueList.head
      val responseTopic = requestMapResponseTopics(requestTopic)
      publishToKafka(responseTopic, key, inBoundStr)
    }
  }

  /**
    * retrieve InBound object from Future
    *
    * @param response response Future
    * @param waitTime wait time
    * @tparam T InBound type
    * @return InBOund object to do assert
    */
  def getInBound[T: Manifest](response: Future[json.JValue], waitTime: Duration = (10 second)) = {
    val value = Await.result(response, waitTime)
    value.extract[T]
  }

  /**
    * get response after call api
    *
    * @param inBound  inBound object that return from kafka
    * @param waitTime max wait time to continue process after call the api
    * @param apiCall  call api method
    * @tparam T OutBound tye
    * @tparam D api return concrete object in the api return Future
    * @return extracted object from api future result
    */
  //  def getResponseByApi[T <: TopicTrait : ClassTag, D]
  //    (inBound: AnyRef, waitTime: Duration = (10 second))
  //    (apiCall: =>  Future[Box[(D, Option[CallContext])]]): Box[(D, Option[CallContext])] = {
  //    return runWithKafka[T]() {
  //      val futureResult =  apiCall
  //      dispathResponse[T](inBound)
  //      return Await.result(futureResult, waitTime)
  //    }
  //  }

  //     connector=kafka_vSept2018
  //api_instance_id=1
  //remotedata.timeout=30
}
