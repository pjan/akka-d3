package examples

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.contrib.d3._
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.util.{Failure, Random, Success}

object Clustered extends App {

  val config = ConfigFactory.parseString(
    s"""
       |akka.loglevel = "INFO"
       |akka.actor.provider = "cluster"
       |akka.contrib.d3.provider = "cluster"
       |akka.contrib.d3.invoices.passivation-timeout = 5 s
     """.stripMargin
  ).withFallback(ConfigFactory.load())

  implicit val system = ActorSystem("ClusterTest", config)
  implicit val dispatcher = system.dispatcher
  val cluster = Cluster(system)
  cluster.join(cluster.selfAddress)

  Domain(system)
    .register[Invoice](
      behavior = InvoiceBehavior,
      name = Option("invoices")
    )

  Thread.sleep(8000)

  val aggregates = List.fill(1000){ Random.nextInt(100000) }.map { id ⇒ Domain(system).aggregateRef[Invoice](Invoice.Id(s"$id")) }

  val result = Future.sequence {
    aggregates.map(aggregate ⇒
      for {
        e1 ← aggregate ? InvoiceProtocol.InvoiceCommand.Create(Invoice.Amount(BigDecimal(200)))
        s1 ← aggregate.state
        e2 ← aggregate ? InvoiceProtocol.InvoiceCommand.Close("paid")
        s2 ← aggregate.state
        q3 ← aggregate.exists(_.amount.value == BigDecimal(200))
        q4 ← aggregate.isInitialized
      } yield (e1, s1, e2, s2, q3, q4))
  }

  // scalastyle:off
  result.onComplete {
    case Success(l) ⇒
      l.foreach {
        case (e1, s1, e2, s2, q3, q4) ⇒
          println(
            s"""
             |1. events:       $e1
             |   state:        $s1
             |2. events:       $e2
             |   state:        $s2
             |3. amnt == 200?: $q3
             |4. initialized?: $q4
       """.stripMargin
          )
      }
      Thread.sleep(10000)
      system.terminate()
    case Failure(exception) ⇒
      println(s"Something went wrong: ${exception.getMessage}")
      Thread.sleep(10000)
      system.terminate()
  }
  // scalastyle:on
}
