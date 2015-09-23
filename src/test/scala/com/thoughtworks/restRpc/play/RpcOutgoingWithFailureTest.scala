package com.thoughtworks.restRpc.play

import java.util.concurrent.TimeUnit.SECONDS

import com.github.dreamhead.moco.{Moco, _}
import com.ning.http.client.AsyncHttpClientConfig
import com.thoughtworks.restRpc.core.{IRouteConfiguration, IUriTemplate}
import com.thoughtworks.restRpc.play.Implicits._
import com.thoughtworks.restRpc.play.exception.RestRpcException.StructuralApplicationException
import org.specs2.mock.{Mockito => SpecMockito}
import org.specs2.mutable.Specification
import org.specs2.specification.{AfterAll, BeforeAll}
import play.api.libs.ws._
import play.api.libs.ws.ning._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.language.implicitConversions


class RpcOutgoingWithFailureTest extends Specification with SpecMockito with BeforeAll with AfterAll {
  val ws: WSClient = new NingWSClient(new AsyncHttpClientConfig.Builder().build())

  val mockWsApi = new WSAPI {
    override def url(url: String) = ws.url(url)

    override def client = ws
  }

  var theServer: Runner = null

  "This is a specification of how rest rpc handle server error response".txt

  "Should throw StructuralApplicationException with STRUCTURAL_APPLICATION_FAILURE when structuralFailure is configured" >> {
    val configuration: IRouteConfiguration = mock[IRouteConfiguration]
    configuration.get_failureClassName returns "com.thoughtworks.restRpc.play.GeneralFailure"
    val template: IUriTemplate = new FakeUriTemplate("GET", "/my-method/1.0/name/failure", 2)
    configuration.nameToUriTemplate("myMethod") returns template

    val myRpc: MyRpcWithStructualException = MyOutgoingProxyFactory.outgoingProxy_com_thoughtworks_restRpc_play_MyRpcWithStructualException(
      new PlayOutgoingJsonService("http://localhost:8091", configuration, mockWsApi)
    )

    Await.result(myRpc.myMethod(1, "failure"), Duration(5, SECONDS)) must (throwA like {
      case StructuralApplicationException(generalFailure) =>
        generalFailure.asInstanceOf[GeneralFailure].errorMsg must equalTo("not found")
    })
  }

  def beforeAll() {
    val server = Moco.httpServer(8091)
    server.get(Moco.by(Moco.uri("/my-method/1.0/name/failure"))).response(Moco.`with`(Moco.text("{\"errorMsg\":\"not found\"}")), Moco.status(404))

    theServer = Runner.runner(server)
    theServer.start()
  }

  override def afterAll() = {
    theServer.stop()
    ws.close()
  }
}