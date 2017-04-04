package akka.contrib

package object d3 {

  implicit def toCassandraReadSide(readSide: ReadSide): CassandraReadSideProvider =
    new CassandraReadSideProvider(readSide)

}
