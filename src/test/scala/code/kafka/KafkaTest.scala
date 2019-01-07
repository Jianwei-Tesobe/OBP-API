package code.kafka

import code.api.util.CallContext
import code.bankconnectors.vMar2017.{Bank2, InboundBank, InboundStatusMessage}
import code.bankconnectors.vSept2018._
import code.setup.KafkaSetup
import net.liftweb.common.{Box, Full}
import net.liftweb.json

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import scala.concurrent.duration._

class KafkaTest extends KafkaSetup {
  val waitTime: Duration = (10 second)


  feature("Send and retrieve message") {
    scenario("Send and retrieve message directly to and from kafka") {
      val emptyStatusMessage = InboundStatusMessage("", "", "", "")
      val inBound = InboundGetBanks(AuthInfo(), Status("", List(emptyStatusMessage)), List(InboundBank("1", "2", "3", "4")))
      When("send a OutboundGetBanks message")

      dispathResponse(inBound)
      val req = OutboundGetBanks(AuthInfo())

      val future = processToFuture[OutboundGetBanks](req)
      val result:json.JValue =  Await.result(future, waitTime)

      val banks = result.extract[InboundGetBanks]
      banks should be equals (inBound)
    }

    scenario("Send and retrieve api message") {
      When("send a OutboundGetBanks api message")
      val emptyStatusMessage = InboundStatusMessage("", "", "", "")
      val singleInboundBank = List(InboundBank("1", "2", "3", "4"))
      val inBound = InboundGetBanks(AuthInfo(), Status("", List(emptyStatusMessage)), singleInboundBank)

      dispathResponse(inBound)

      val future = KafkaMappedConnector_vSept2018.getBanksFuture(None)
      val result:Box[(List[Bank2], Option[CallContext])] =  Await.result(future, waitTime)
      val expectResult = Full(singleInboundBank.map(Bank2))

      result should be equals(expectResult)
    }
  }
}
