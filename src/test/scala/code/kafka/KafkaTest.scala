package code.kafka

import code.api.util.CallContext
import code.bankconnectors.vMar2017.{Bank2, InboundBank, InboundStatusMessage}
import code.bankconnectors.vSept2018._
import code.setup.KafkaSetup
import net.liftweb.common.{Box, Full}
import net.liftweb.json
import net.liftweb.json.DefaultFormats

import scala.collection.immutable.List

class KafkaTest extends KafkaSetup {
  implicit val formats = DefaultFormats

  feature("Send and retrieve message") {
    scenario("Send and retrieve message directly to and from kafka", kafkaTest) {
      val emptyStatusMessage = InboundStatusMessage("", "", "", "")
      val inBound = InboundGetBanks(AuthInfo(), Status("", List(emptyStatusMessage)), List(InboundBank("1", "2", "3", "4")))
      When("send a OutboundGetBanks message")
      val result:json.JValue = runWithKafkaFuture(inBound) {
        val req = OutboundGetBanks(AuthInfo())
        processToFuture[OutboundGetBanks](req)
      }
      val banks = result.extract[InboundGetBanks]
      banks should be equals (inBound)
    }

    scenario("Send and retrieve api message", kafkaTest) {
      When("send a OutboundGetBanks api message")
      val emptyStatusMessage = InboundStatusMessage("", "", "", "")
      val singleInboundBank = List(InboundBank("1", "2", "3", "4"))
      val inBound = InboundGetBanks(AuthInfo(), Status("", List(emptyStatusMessage)), singleInboundBank)

      val result:Box[(List[Bank2], Option[CallContext])] = runWithKafkaFuture(inBound) {
        KafkaMappedConnector_vSept2018.getBanksFuture(None)
      }
      val expectResult = Full(singleInboundBank.map(Bank2))
      result should be equals(expectResult)
    }
  }
}
