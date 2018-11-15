package code.kafka

import code.bankconnectors.vMar2017.{InboundBank, InboundStatusMessage}
import code.bankconnectors.vSept2018._
import code.setup.KafkaSetup

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._

class KafkaTest extends KafkaSetup {

  feature("Send and retrieve message") {
    scenario("Send and retrieve api message") {
      runWithKafka {
        When("send a OutboundGetBanks api message")
        val emptyStatusMessage = InboundStatusMessage("", "", "", "")
        val inBound = InboundGetBanks(AuthInfo(), Status("", List(emptyStatusMessage)), List(InboundBank("1", "2", "3", "4")))

        val req = OutboundGetBanks(AuthInfo())

        val eventualValue = processToFuture[OutboundGetBanks](req)
        dispathResponse[OutboundGetBanks](inBound)

        val rebackInBound = Await.result(eventualValue, (10 second)).extract[InboundGetBanks]
        rebackInBound should be equals(inBound)
      }
    }
  }
}
