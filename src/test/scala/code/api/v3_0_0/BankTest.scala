package code.api.v3_0_0

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanUseFirehoseAtAnyBank
import code.api.util.ErrorMessages.{FirehoseViewsNotAllowedOnThisInstance, UserHasMissingRoles}
import code.bankconnectors.KafkaMappedConnector.KafkaInboundBank
import code.setup.KafkaSetup
import net.liftweb.json.JsonAST.compactRender

class BankTest extends V300ServerSetup with KafkaSetup {

  feature("Assuring that entitlement requirements are checked for account(s) related endpoints") {

    scenario("We try to get firehose accounts without required role " + CanUseFirehoseAtAnyBank){

      When("We have to find it by endpoint getFirehoseAccountsAtOneBank")
      val requestGet = (v3_0Request / "banks").GET  <@(user1)

      val inBoundObject = List(KafkaInboundBank("some bank name", "some bank id", "some logo", "some url"))
      val responseGet = runWithKafka(inBoundObject) {
        makeGetRequest(requestGet)
      }

      And("We should get a 403")
      responseGet.code should equal(200)
      compactRender(responseGet.body \ "error").replaceAll("\"", "") should equal(FirehoseViewsNotAllowedOnThisInstance +" or " + UserHasMissingRoles + CanUseFirehoseAtAnyBank  )
    }}


  }
