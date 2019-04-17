import org.scalacheck.{Arbitrary, Gen}

final case class KeyValue(key: String, value: String)

object KeyValue {
  lazy val keyValueGenerator: Gen[KeyValue] = for {
    key   <- Gen.alphaStr
    value <- Gen.alphaStr
  } yield KeyValue(key, value)

  implicit val arbKeyValue: Arbitrary[KeyValue] = Arbitrary(keyValueGenerator)
}
