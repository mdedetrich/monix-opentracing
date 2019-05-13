import org.scalacheck.{Arbitrary, Gen}

final case class MultipleKeysMultipleValues(keysAndValues: List[KeyValue])

object MultipleKeysMultipleValues {
  lazy val multipleKeyValueGenerator: Gen[MultipleKeysMultipleValues] =
    for {
      range     <- Gen.chooseNum(2, 10)
      keyValues <- Gen.listOfN(range, KeyValue.keyValueGenerator)
    } yield MultipleKeysMultipleValues(keyValues)

  implicit val arbMultipleKeyValue: Arbitrary[MultipleKeysMultipleValues] = Arbitrary(multipleKeyValueGenerator)
}
