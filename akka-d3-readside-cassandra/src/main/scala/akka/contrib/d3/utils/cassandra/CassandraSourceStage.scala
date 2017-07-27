package akka.contrib.d3.utils.cassandra

import akka.stream._
import akka.stream.stage._
import com.datastax.driver.core._

import scala.concurrent.Future
import scala.util._

import GuavaFutures._

private[cassandra] final class CassandraSourceStage(
    futureStatement: Future[Statement],
    session:         Session
) extends GraphStage[SourceShape[Row]] {
  val out: Outlet[Row] = Outlet("CassandraSource.out")
  override val shape: SourceShape[Row] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      var maybeResultSet = Option.empty[ResultSet]
      var futureFetchedCallback: AsyncCallback[Try[ResultSet]] = _

      override def preStart(): Unit = {
        implicit val ec = materializer.executionContext

        futureFetchedCallback = getAsyncCallback[Try[ResultSet]](tryPushAfterFetch)

        (for {
          statement ← futureStatement
          resultSet ← session.executeAsync(statement).asScala()
        } yield resultSet).onComplete(futureFetchedCallback.invoke)
      }

      setHandler(
        out = out,
        handler = new OutHandler {
        override def onPull(): Unit = {
          implicit val ec = materializer.executionContext

          maybeResultSet match {
            case Some(resultSet) if resultSet.getAvailableWithoutFetching > 0 ⇒ push(out, resultSet.one())
            case Some(resultSet) if resultSet.isExhausted                     ⇒ completeStage()
            case Some(resultSet) ⇒
              // fetch next page
              val futureResultSet = resultSet.fetchMoreResults().asScala()
              futureResultSet.onComplete(futureFetchedCallback.invoke)
            case None ⇒ () // doing nothing, waiting for futureResultSet in preStart() to be completed
          }
        }
      }
      )

      private def tryPushAfterFetch(rsOrFailure: Try[ResultSet]): Unit = rsOrFailure match {
        case Success(resultSet) ⇒
          maybeResultSet = Some(resultSet)
          if (resultSet.getAvailableWithoutFetching > 0) {
            if (isAvailable(out)) {
              push(out, resultSet.one())
            }
          } else {
            completeStage()
          }

        case Failure(failure) ⇒ failStage(failure)
      }
    }

}
