package code.setup

import java.util.concurrent.atomic.AtomicBoolean

import bootstrap.liftweb.Boot
import code.actorsystem.ObpActorSystem
import code.api.util.{APIUtil, ApiVersion}
import code.bankconnectors.Connector
import code.kafka._
import code.util.Helper.MdcLoggable
import net.liftweb.json
import net.liftweb.json.{DefaultFormats, Extraction}
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{FeatureSpec, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait KafkaSetup extends FeatureSpec with EmbeddedKafka with KafkaHelper
  with BeforeAndAfterEach with GivenWhenThen
  with BeforeAndAfterAll
  with Matchers with MdcLoggable {

  val kafkaTest = KafkaTest

  implicit val formats = DefaultFormats
  implicit val stringSerializer = new StringSerializer
  implicit val stringDeserializer = new StringDeserializer
  //TODO the port should read from test.default.props, but fail
  implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)


  lazy val requestMapResponseTopics: Map[String, String] = NorthSideConsumer.listOfTopics
    .map(Topics.createTopicByClassName)
    .map(pair => (pair.request, pair.response))
    .toMap

  lazy val requestTopics = requestMapResponseTopics.keySet

  def doStart(): Unit = {
    EmbeddedKafka.start()
    if (ObpActorSystem.obpActorSystem == null) {
      new Boot().boot
    }
    createCustomTopic("Request", Map.empty, 10, 1)
    createCustomTopic("Response", Map.empty, 10, 1)

    val actorSystem = ObpActorSystem.obpActorSystem
    KafkaHelperActors.startLocalKafkaHelperWorkers(actorSystem)
    // Start North Side Consumer if it's not already started
    KafkaConsumer.primaryConsumer.start()

    //change connector instance to kafka connector
    val kafkaConnectorName = APIUtil.getPropsValue("kafka.connector", "kafka_vSept2018")
    val kafkaVendor = Connector.getConnectorInstance(kafkaConnectorName)
    Connector.connector.default.set(kafkaVendor)
  }

  def doStop(): Unit = {
    // stop NorthSideConsumer pull kafka message thread
    KafkaConsumer.primaryConsumer.complete()
    // wait for NorthSideConsumer stop finished, because NorthSideConsumer pull message timeout is 100ms
    Thread.sleep(100)
    ObpActorSystem.obpActorSystem.terminate()
    System.out.println("Shutdown Hook is running !")
    EmbeddedKafka.stop()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    KafkaServer.doStart(this.doStart)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    KafkaServer.doStopAfterAll(this.doStop)
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
    Future {
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

  //connector=kafka_vSept2018
  //api_instance_id=1
  //remotedata.timeout=30
}

/**
  * a tool of KafkaServer, make the EmbededKafka start once and stop before process finished
  */
object KafkaServer {

  private val hasStarted = new AtomicBoolean(false)
  private val hasRegistedShutdown = new AtomicBoolean(false)

  /**
    * only start kafka once
    * @param start - function to do start kafka logic
    */
  def doStart(start: => Unit): Unit = {
    if (this.hasStarted.compareAndSet(false, true)) start
  }

  /**
    * register shutdown hook to do kafka stop
    * @param stopAfterAll - stop kafka process
    */
  def doStopAfterAll(stopAfterAll: => Unit) = {
    if (this.hasRegistedShutdown.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(new Thread() {
        override def run(): Unit = {
          stopAfterAll
        }
      })
    }
  }
}

/**
  * Test tags
  * Example: To run tests with tag "kafakTest":
  * 	mvn test -D kafakTest
  *
  *  This is made possible by the scalatest maven plugin
  */
object KafkaTest extends Tag("kafkaTest")