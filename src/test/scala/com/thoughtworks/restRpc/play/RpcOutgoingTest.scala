package com.thoughtworks.restRpc.play

import java.util.concurrent.TimeUnit.SECONDS

import com.github.dreamhead.moco.{Moco, _}
import com.ning.http.client.AsyncHttpClientConfig
import com.thoughtworks.restRpc.core.{IRouteConfiguration, IUriTemplate}
import com.thoughtworks.restRpc.play.Implicits._
import com.thoughtworks.restRpc.play.exception.RpcApplicationException
import org.specs2.mock.{Mockito => SpecMockito}
import org.specs2.mutable.Specification
import org.specs2.specification.{AfterAll, BeforeAll}
import play.api.libs.ws._
import play.api.libs.ws.ning._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.language.implicitConversions

class RpcOutgoingTest extends Specification with SpecMockito with BeforeAll with AfterAll {
  val ws:WSClient = new NingWSClient(new AsyncHttpClientConfig.Builder().build())

  val mockWsApi = new WSAPI {
    override def url(url: String) = ws.url(url)
    override def client = ws
  }

  var theServer: Runner = null

  "This is a specification of using rest-rpc-play tools to make http requests".txt

  "Should throw RpcApplicationException with TEXT_APPLICATION_FAILURE when structuralFailure is not configured" >> {
    val configuration: IRouteConfiguration = mock[IRouteConfiguration]
    configuration.failureClassName() returns null
    val template: IUriTemplate = new FakeUriTemplate("GET", "/my-method/1.0/name/failure", 2)
    configuration.nameToUriTemplate("myMethod") returns template

    val myRpc: MyRpc = MyOutgoingProxyFactory.outgoingProxy_com_thoughtworks_restRpc_play_MyRpc(
      new PlayOutgoingJsonService("http://localhost:8090", configuration, mockWsApi)
    )

    Await.result(myRpc.myMethod(1, "failure"), Duration(5, SECONDS)) must (throwA[RpcApplicationException] like {
      case RpcApplicationException(failure) => failure.getParams().__get(0).asInstanceOf[String] === "server error"
    })
  }

  "Should convert myMethod to http get request and get the response" >> {
    val configuration: IRouteConfiguration = mock[IRouteConfiguration]
    val template: IUriTemplate = new FakeUriTemplate("GET", "/my-method/1.0/name/abc", 2)
    configuration.nameToUriTemplate("myMethod") returns template

    val myRpc: MyRpc = MyOutgoingProxyFactory.outgoingProxy_com_thoughtworks_restRpc_play_MyRpc(
      new PlayOutgoingJsonService("http://localhost:8090", configuration, mockWsApi)
    )

    val response = Await.result(myRpc.myMethod(1, "abc"), Duration(5, SECONDS))

    response.myInnerEntity.message === "this is a message"
    response.myInnerEntity.code === 1
  }

  "Should convert createResource to http post request and get created response" >> {
    val configuration: IRouteConfiguration = mock[IRouteConfiguration]

    val template: IUriTemplate = new FakeUriTemplate("POST", "/books", 1)
    configuration.nameToUriTemplate("createResource") returns template

    val myRpc: MyRpc = MyOutgoingProxyFactory.outgoingProxy_com_thoughtworks_restRpc_play_MyRpc(
      new PlayOutgoingJsonService("http://localhost:8090", configuration, mockWsApi)
    )

    val response = Await.result(myRpc.createResource("books", new Book(1, "name")), Duration(5, SECONDS))

    response.result === "created"
  }

  def beforeAll() {
    val server = Moco.httpServer(8090)
    server.get(Moco.by(Moco.uri("/my-method/1.0/name/abc"))).response("""
          {
            "myInnerEntity": {
              "code":1,
              "message":"this is a message"
            }
          }""")

    server.get(Moco.by(Moco.uri("/my-method/1.0/name/failure"))).response(Moco.`with`(Moco.text("server error")), Moco.status(500))

    server.post(Moco.by(Moco.uri("/books"))).response(
      """
        {"result":"created"}
      """)

    theServer = Runner.runner(server)
    theServer.start()
  }

  override def afterAll()  = {
    theServer.stop()
    ws.close()
  }
}
