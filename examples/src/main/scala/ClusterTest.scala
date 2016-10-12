import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.contrib.d3._
import com.typesafe.config.ConfigFactory

import scala.util.{Failure, Random, Success}

object ClusterTest extends App {

  val config = ConfigFactory.parseString(
    s"""
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

  val aggregate = Domain(system).aggregateRef[Invoice](Invoice.Id(s"${Random.nextInt(100000)}"))

  val result = for {
    e1 ← aggregate ? InvoiceProtocol.InvoiceCommand.Create(Invoice.Amount(BigDecimal(200)))
    s1 ← aggregate.getState()
    e2 ← aggregate ? InvoiceProtocol.InvoiceCommand.Close("paid")
    s2 ← aggregate.getState()
  } yield (e1, s1, e2, s2)

  result.onComplete {
    case Success((e1, s1, e2, s2)) ⇒
      println(
        s"""
           |1. events: $e1
           |   state:  $s1
           |2. events: $e2
           |   state:  $s2
       """.stripMargin
      )
      Thread.sleep(10000)
      system.terminate()
    case Failure(exception) ⇒
      println(s"Something went wrong: ${exception.getMessage}")
      Thread.sleep(10000)
      system.terminate()
  }
}
