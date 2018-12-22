/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
  */
package code.api.v3_1_0

import code.api.ErrorMessage
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.ApiVersion
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateTaxResidence, CanGetAccountApplications, CanUpdateAccountApplications}
import code.api.util.ErrorMessages.{AccountApplicationNotFound, InvalidJsonFormat, UserHasMissingRoles, UserNotLoggedIn}
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.customer.Customer
import code.entitlement.Entitlement
import code.usercustomerlinks.UserCustomerLink
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.Box
import net.liftweb.json.Serialization.write
import org.scalatest.Tag
import code.accountapplication.AccountApplication
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.accountApplicationUpdateStatusJson
import code.bankconnectors.Connector
import code.model.BankAccount
import code.products.Products.ProductCode
import org.scalatest.Matchers._

import scala.concurrent.duration._
import scala.concurrent.Await

class AccountApplicationTest extends V310ServerSetup {

  var canUpdateAccountApplications: Box[Entitlement] = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    canUpdateAccountApplications = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateAccountApplications.toString)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    Entitlement.entitlement.vend.deleteEntitlement(canUpdateAccountApplications)
  }

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations3_1_0.createAccountApplication))
  object ApiEndpoint2 extends Tag(nameOf(Implementations3_1_0.getAccountApplications))
  object ApiEndpoint3 extends Tag(nameOf(Implementations3_1_0.getAccountApplication))
  object ApiEndpoint4 extends Tag(nameOf(Implementations3_1_0.updateAccountApplicationStatus))

  val postAccountApplicationJson = AccountApplicationJson(
    product_code = "saving1",
    user_id = userId,
    customer_id = None
  )
  lazy val bankId = randomBankId

  feature("Add the AccountApplication v3.1.0") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications").POST
      val response310 = makePostRequest(request310, write(postAccountApplicationJson))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }

    scenario("We will call the endpoint with user credentials, send wrong json", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications").POST <@(user1)
      val wrongFormatJson = ("anyKey", "anyValue")
      val response310 = makePostRequest(request310, write(wrongFormatJson))
      Then("We should get a 400")
      response310.code should equal(400)
      val errorMessage = s"$InvalidJsonFormat The Json body should be the $AccountApplicationJson "
      And(errorMessage)
      response310.body.extract[ErrorMessage].error should equal  (errorMessage)
    }

    scenario("We will call the endpoint with user credentials, product_code is blank", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications").POST <@(user1)
      val accountApplicationJson = postAccountApplicationJson.copy(product_code = null)
      val response310 = makePostRequest(request310, write(accountApplicationJson))
      Then("We should get a 400")
      response310.code should equal(400)
      val errorMessage = s"$InvalidJsonFormat product_code should not be empty."
      And(errorMessage)
      response310.body.extract[ErrorMessage].error should equal(errorMessage)
    }

    scenario("We will call the endpoint with user credentials, user_id and customer_id are all blank", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications").POST <@(user1)
      val accountApplicationJson = postAccountApplicationJson.copy(user_id=None, customer_id = None)
      val response310 = makePostRequest(request310, write(accountApplicationJson))
      Then("We should get a 400")
      response310.code should equal(400)
      val errorMessage = s"$InvalidJsonFormat User_id and customer_id should not both are empty."
      And(errorMessage)
      response310.body.extract[ErrorMessage].error should equal (errorMessage)
    }

    scenario("We will call the endpoint with user credentials, and post valid json", ApiEndpoint1, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications").POST <@(user1)
      val response310 = makePostRequest(request310, write(postAccountApplicationJson))
      Then("We should get a 201")
      response310.code should equal(201)
      response310.body.extract[AccountApplicationResponseJson]
    }

  }


  feature("Get the AccountApplications v3.1.0") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }

    scenario("We will call the endpoint without CanGetAccountApplications role", ApiEndpoint2, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications").GET <@(user2)
      val response310 = makeGetRequest(request310)
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMessage = UserHasMissingRoles + CanGetAccountApplications
      And("error should be " + errorMessage)
      response310.body.extract[ErrorMessage].error should equal (errorMessage)
    }

    scenario("We will call the endpoint with CanGetAccountApplications role", ApiEndpoint2, VersionOfApi) {
      // add one AccountApplication to DB
      AccountApplication.accountApplication.vend.createAccountApplication(ProductCode("saving1"), userId, None)
      // prepare role
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetAccountApplications.toString)
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications").GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(200)
      val responseJson = response310.body.extract[AccountApplicationsJsonV310]
      responseJson.account_applications should not be empty
    }
  }

  feature("Get the AccountApplication by account_application_id v3.1.0") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications"/ "ACCOUNT_APPLICATION_ID").GET
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }

    scenario("We will call the endpoint with user credentials, but account_application_id is wrong", ApiEndpoint3, VersionOfApi) {
      When("We make a request v3.1.0")
      val fakeId = "not exists account_application_id"
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications"/ fakeId).GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 400")
      response310.code should equal(400)
      val errorMessage = s"$AccountApplicationNotFound Current Account-Application-Id($fakeId)"
      And("error should be " + errorMessage)
      response310.body.extract[ErrorMessage].error should equal (errorMessage)
    }

    scenario("We will call the endpoint with user credentials, and account_application_id exists", ApiEndpoint3, VersionOfApi) {
      // add one AccountApplication to DB
      val accountApplication = AccountApplication.accountApplication.vend.createAccountApplication(ProductCode("saving2"), userId, None)
      val accountApplicationId = Await.result(accountApplication, (10 seconds)) map (_.accountApplicationId) orNull

      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications"/ accountApplicationId).GET <@(user1)
      val response310 = makeGetRequest(request310)
      Then("We should get a 200")
      response310.code should equal(200)
      val json = response310.body.extract[AccountApplicationResponseJson]
      json.status should equal("REQUESTED")
    }
  }


  feature("Update one AccountApplication status v3.1.0") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint4, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications"/ "ACCOUNT_APPLICATION_ID").PUT
      val response310 = makePutRequest(request310, write(accountApplicationUpdateStatusJson))
      Then("We should get a 400")
      response310.code should equal(400)
      And("error should be " + UserNotLoggedIn)
      response310.body.extract[ErrorMessage].error should equal (UserNotLoggedIn)
    }

    scenario("We will call the endpoint with user credentials, but without CanUpdateAccountApplications role", ApiEndpoint4, VersionOfApi) {
      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications"/ "ACCOUNT_APPLICATION_ID").PUT <@(user1)
      val response310 = makePutRequest(request310, write(accountApplicationUpdateStatusJson))
      Then("We should get a 403")
      response310.code should equal(403)
      val errorMessage = UserHasMissingRoles + CanUpdateAccountApplications
      And("error should be " + errorMessage)
      response310.body.extract[ErrorMessage].error should equal (errorMessage)
    }

    scenario("We will call the endpoint with user credentials, send wrong json", ApiEndpoint4, VersionOfApi) {
      // prepare role for user1
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateAccountApplications.toString)

      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications"/ "ACCOUNT_APPLICATION_ID").PUT <@(user1)
      val wrongFormatJson =  ("anyKey", "anyValue")
      val response310 = makePutRequest(request310, write(wrongFormatJson))

      Then("We should get a 400")
      response310.code should equal(400)
      val errorMessage = s"$InvalidJsonFormat The Json body should be the $AccountApplicationUpdateStatusJson "
      And(errorMessage)
      response310.body.extract[ErrorMessage].error should equal (errorMessage)
    }

    scenario("We will call the endpoint with user credentials, status is blank", ApiEndpoint4, VersionOfApi) {
      // prepare role for user1
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateAccountApplications.toString)

      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications"/ "ACCOUNT_APPLICATION_ID").PUT <@(user1)
      val noStatusJson =  AccountApplicationUpdateStatusJson("")
      val response310 = makePutRequest(request310, write(noStatusJson))

      Then("We should get a 400")
      response310.code should equal(400)
      val errorMessage = s"$InvalidJsonFormat status should not be blank."
      And(errorMessage)
      response310.body.extract[ErrorMessage].error should equal (errorMessage)
    }

    scenario("We will call the endpoint with user credentials, account_application_id not exists", ApiEndpoint4, VersionOfApi) {
      // prepare role for user1
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateAccountApplications.toString)

      When("We make a request v3.1.0")
      val fakeId = "ACCOUNT_APPLICATION_ID"
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications"/ fakeId).PUT <@(user1)
      val noStatusJson =  AccountApplicationUpdateStatusJson("PROCESSING")
      val response310 = makePutRequest(request310, write(noStatusJson))

      Then("We should get a 400")
      response310.code should equal(400)
      val errorMessage = s"$AccountApplicationNotFound Current Account-Application-Id($fakeId)"
      And(errorMessage)
      response310.body.extract[ErrorMessage].error should equal (errorMessage)
    }

    scenario("We will call the endpoint with user credentials, status is PROCESSING", ApiEndpoint4, VersionOfApi) {
      // prepare role for user1
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateAccountApplications.toString)
      // add one AccountApplication to DB
      val accountApplication = AccountApplication.accountApplication.vend.createAccountApplication(ProductCode("saving3"), userId, None)
      val accountApplicationId = Await.result(accountApplication, (10 seconds)) map (_.accountApplicationId) orNull

      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications"/ accountApplicationId).PUT <@(user1)
      val noStatusJson =  AccountApplicationUpdateStatusJson("PROCESSING")
      val response310 = makePutRequest(request310, write(noStatusJson))

      Then("We should get a 200")
      response310.code should equal(200)
      val responseJson = response310.body.extract[AccountApplicationResponseJson]
      responseJson.status should equal("PROCESSING")
    }

    scenario("We will call the endpoint with user credentials, status is ACCEPTED", ApiEndpoint4, VersionOfApi) {
      // prepare role for user1
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanUpdateAccountApplications.toString)
      // add one AccountApplication to DB
      val accountApplication = AccountApplication.accountApplication.vend.createAccountApplication(ProductCode("saving4"), userId, None)
      val accountApplicationId = Await.result(accountApplication, (10 seconds)) map (_.accountApplicationId) orNull

      When("We make a request v3.1.0")
      val request310 = (v3_1_0_Request / "banks" / bankId / "account-applications"/ accountApplicationId).PUT <@(user1)
      val noStatusJson =  AccountApplicationUpdateStatusJson("ACCEPTED")
      val response310 = makePutRequest(request310, write(noStatusJson))

      Then("We should get a 200")
      response310.code should equal(200)
      val responseJson = response310.body.extract[AccountApplicationResponseJson]
      responseJson.status should equal("ACCEPTED")
      // TODO need check whether created Account, and type is "saving4"
    }

  }
}
