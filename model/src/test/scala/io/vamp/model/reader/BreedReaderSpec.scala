package io.vamp.model.reader

import io.vamp.model.artifact._
import io.vamp.model.notification._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BreedReaderSpec extends ReaderSpec {

  "BreedReader" should "read the simplest YAML (name/deployable only)" in {
    BreedReader.read(res("breed/breed1.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the ports" in {
    BreedReader.read(res("breed/breed2.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080/http")))),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "read the environment variables and dependencies" in {
    BreedReader.read(res("breed/breed3.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080/http")))),
      'environmentVariables(List(EnvironmentVariable("DB_HOST", None, Some("$db.host")), EnvironmentVariable("DB_PORT", None, Some("$db.ports.port")))),
      'dependencies(Map("db" → BreedReference("mysql")))
    )
  }

  it should "read the YAML source with value expansion" in {
    BreedReader.read(res("breed/breed4.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080/http")))),
      'environmentVariables(List(EnvironmentVariable("DB_HOST", None, Some("$db.host")))),
      'dependencies(Map("db" → BreedReference("mysql")))
    )
  }

  it should "read the YAML source with partially expanded reference dependencies" in {
    BreedReader.read(res("breed/breed5.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" → BreedReference("mysql")))
    )
  }

  it should "read the YAML source with fully expanded reference dependencies" in {
    BreedReader.read(res("breed/breed6.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" → BreedReference("mysql")))
    )
  }

  it should "read the YAML source with embedded dependencies" in {
    BreedReader.read(res("breed/breed7.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" → DefaultBreed("mysql", Map(), Deployable("magneticio/mysql:latest"), Nil, Nil, Nil, Nil, Map(), None)))
    )
  }

  it should "read the YAML source with expanded embedded dependencies" in {
    BreedReader.read(res("breed/breed8.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List()),
      'dependencies(Map("db" → DefaultBreed("mysql", Map(), Deployable("magneticio/mysql:latest"), Nil, Nil, Nil, Nil, Map(), None)))
    )
  }

  it should "read the YAML source with embedded dependencies with dependencies" in {
    BreedReader.read(res("breed/breed9.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080/tcp")))),
      'environmentVariables(List(EnvironmentVariable("DB_HOST", None, Some("$db.host")), EnvironmentVariable("DB_PORT", None, Some("$db.ports.port")))),
      'dependencies(Map("db" → DefaultBreed("mysql-wrapper", Map(), Deployable("magneticio/mysql-wrapper:latest"), List(Port("port", None, Some("3006/tcp"))), Nil, Nil, Nil, Map("mysql" → BreedReference("mysql")), None)))
    )
  }

  it should "fail on no deployable" in {
    expectedError[MissingPathValueError]({
      BreedReader.read(res("breed/breed10.yml"))
    }) should have(
      'path("deployable/definition")
    )
  }

  it should "not fail on missing port values" in {
    expectedError[MissingPortValueError]({
      BreedReader.read(res("breed/breed11.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Map(), Deployable("magneticio/monarch:latest"), List(Port("port", None, None)), Nil, Nil, Nil, Map(), None)),
      'port(Port("port", None, None))
    )
  }

  it should "not fail on missing environment variable values" in {
    BreedReader.read(res("breed/breed12.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List(EnvironmentVariable("port", None, None))),
      'dependencies(Map())
    )
  }

  it should "not fail on non unique port name but should get the last one only" in {
    BreedReader.read(res("breed/breed13.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080/http")))),
      'environmentVariables(List()),
      'dependencies(Map())
    )
  }

  it should "not fail on non unique environment variable name but should get the last one only" in {
    BreedReader.read(res("breed/breed14.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List()),
      'environmentVariables(List(EnvironmentVariable("port", None, Some("8080/http")))),
      'dependencies(Map())
    )
  }

  it should "fail on unresolved dependency reference" in {
    expectedError[UnresolvedDependencyInTraitValueError]({
      BreedReader.read(res("breed/breed15.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Map(), Deployable("magneticio/monarch:latest"), Nil, List(EnvironmentVariable("port", None, Some("$es.ports.port"))), Nil, Nil, Map("db" → BreedReference("mysql")), None)),
      'reference("es.ports.port")
    )
  }

  it should "fail on missing dependency environment variable" in {
    expectedError[UnresolvedDependencyInTraitValueError]({
      BreedReader.read(res("breed/breed16.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Map(), Deployable("magneticio/monarch:latest"), Nil, List(EnvironmentVariable("port", None, Some("$db.ports.web"))), Nil, Nil, Map("db" → DefaultBreed("mysql", Map(), Deployable("vamp/mysql"), Nil, Nil, Nil, Nil, Map(), None)), None)),
      'reference("db.ports.web")
    )
  }

  it should "fail on missing dependency port" in {
    expectedError[UnresolvedDependencyInTraitValueError]({
      BreedReader.read(res("breed/breed17.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Map(), Deployable("magneticio/monarch:latest"), List(Port("port", None, Some("$db.ports.web"))), Nil, Nil, Nil, Map("db" → DefaultBreed("mysql", Map(), Deployable("vamp/mysql"), Nil, Nil, Nil, Nil, Map(), None)), None)),
      'reference("db.ports.web")
    )
  }

  it should "fail on direct recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BreedReader.read(res("breed/breed18.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Map(), Deployable("magneticio/monarch:latest"), List(Port("port", None, Some("$db.ports.web"))), Nil, Nil, Nil, Map("db" → BreedReference("monarch")), None))
    )
  }

  it should "fail on indirect recursive dependency" in {
    expectedError[RecursiveDependenciesError]({
      BreedReader.read(res("breed/breed19.yml"))
    }) should have(
      'breed(DefaultBreed("monarch2", Map(), Deployable("magneticio/monarch2:latest"), Nil, Nil, Nil, Nil, Map("es" → BreedReference("monarch1")), None))
    )
  }

  it should "fail on missing constant values" in {
    expectedError[MissingConstantValueError]({
      BreedReader.read(res("breed/breed20.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Map(), Deployable("magneticio/monarch:latest"), Nil, Nil, List(Constant("port", None, None)), Nil, Map(), None)),
      'constant(Constant("port", None, None))
    )
  }

  it should "fail on missing dependency constant" in {
    expectedError[UnresolvedDependencyInTraitValueError]({
      BreedReader.read(res("breed/breed21.yml"))
    }) should have(
      'breed(DefaultBreed("monarch", Map(), Deployable("magneticio/monarch:latest"), List(Port("port", None, Some("$db.constants.web"))), Nil, Nil, Nil, Map("db" → DefaultBreed("mysql", Map(), Deployable("vamp/mysql"), List(Port("web", None, Some("80"))), List(EnvironmentVariable("web", None, Some("80"))), Nil, Nil, Map(), None)), None)),
      'reference("db.constants.web")
    )
  }

  it should "resolve dependency constant" in {
    BreedReader.read(res("breed/breed22.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("web", None, Some("$db.constants.port")))),
      'environmentVariables(List()),
      'dependencies(Map("db" → DefaultBreed("mysql", Map(), Deployable("vamp/mysql"), Nil, Nil, List(Constant("port", None, Some("80/tcp"))), Nil, Map(), None)))
    )
  }

  it should "resolve environment variable alias" in {
    BreedReader.read(res("breed/breed23.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(Nil),
      'environmentVariables(List(EnvironmentVariable("period", None, Some("100")), EnvironmentVariable("timeout", Some("TIME_OUT"), Some("10")))),
      'dependencies(Map())
    )
  }

  it should "resolve deployable implicit (default) type and definition" in {
    BreedReader.read(res("breed/breed24.yml")) should have(
      'name("monarch"),
      'deployable(Deployable(None, "magneticio/vamp:latest"))
    )
  }

  it should "resolve deployable schema" in {
    BreedReader.read(res("breed/breed25.yml")) should have(
      'name("sava:1.0.0"),
      'deployable(Deployable(None, "magneticio/sava:1.0.0"))
    )
  }

  it should "fail on invalid trait name: '.'" in {
    expectedError[IllegalStrictName]({
      BreedReader.read(res("breed/breed26.yml"))
    }) should have(
      'name("time.out")
    )
  }

  it should "fail on invalid trait name: '/'" in {
    expectedError[IllegalStrictName]({
      BreedReader.read(res("breed/breed27.yml"))
    }) should have(
      'name("time/out")
    )
  }

  it should "fail on invalid trait name: '['" in {
    expectedError[IllegalStrictName]({
      BreedReader.read(res("breed/breed28.yml"))
    }) should have(
      'name("timeout[]")
    )
  }

  it should "resolve arguments" in {
    BreedReader.read(res("breed/breed29.yml")) should have(
      'name("sava:1.0.0"),
      'deployable(Deployable(None, "magneticio/sava:1.0.0")),
      'arguments(List(Argument("arg1", "test1"), Argument("arg2", "test2")))
    )
  }

  it should "expand single argument" in {
    BreedReader.read(res("breed/breed30.yml")) should have(
      'name("sava:1.0.0"),
      'deployable(Deployable(None, "magneticio/sava:1.0.0")),
      'arguments(List(Argument("arg1", "test1")))
    )
  }

  it should "expand multiple arguments" in {
    BreedReader.read(res("breed/breed31.yml")) should have(
      'name("sava:1.0.0"),
      'deployable(Deployable(None, "magneticio/sava:1.0.0")),
      'arguments(List(Argument("arg", "test"), Argument("privileged", "true")))
    )
  }

  it should "fail on multi key-value argument" in {
    expectedError[InvalidArgumentError.type]({
      BreedReader.read(res("breed/breed32.yml"))
    })
  }

  it should "fail on no argument" in {
    expectedError[InvalidArgumentError.type]({
      BreedReader.read(res("breed/breed33.yml"))
    })
  }

  it should "fail on invalid privileged value" in {
    expectedError[InvalidArgumentValueError]({
      BreedReader.read(res("breed/breed34.yml"))
    }) should have(
      'argument(Argument("privileged", "1"))
    )
  }

  it should "resolve deployable explicit type and definition" in {
    BreedReader.read(res("breed/breed35.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("container/rkt", "magneticio/vamp:latest"))
    )
  }

  it should "fail on invalid kind" in {
    expectedError[InconsistentArtifactKind]({
      BreedReader.read(res("breed/breed36.yml"))
    }) should have(
      'given("blueprints")
    )
  }

  it should "fail if parser error" in {
    expectedError[ParserError]({
      BreedReader.read(res("breed/breed37.yml"))
    })
  }

  it should "read the health checks" in {
    BreedReader.read(res("breed/breed38.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080/http"), 8080, Port.Type.Http))),
      'environmentVariables(List()),
      'dependencies(Map()),
      'healthChecks(Some(List(HealthCheck("path/to/check", "port", Time(30), Time(4), Time(60), 5, "HTTPS"), HealthCheck("path/to/check2", "port", Time(30), Time(4), Time(60), 5, "HTTPS"))))
    )
  }

  it should "read expanded health check" in {
    BreedReader.read(res("breed/breed39.yml")) should have(
      'name("monarch"),
      'deployable(Deployable("magneticio/monarch:latest")),
      'ports(List(Port("port", None, Some("8080/http"), 8080, Port.Type.Http))),
      'environmentVariables(List()),
      'dependencies(Map()),
      'healthChecks(Some(List(HealthCheck("path/to/check", "port", Time(30), Time(4), Time(60), 5, "HTTPS"))))
    )
  }

  it should "throw an error when a port reference in a health check is unresolved" in {
    expectedError[UnresolvedPortReferenceError] {
      BreedReader.read(res("breed/breed40.yml"))
    }
  }

  it should "throw an error when failure number is negative" in {
    expectedError[NegativeFailuresNumberError] {
      BreedReader.read(res("breed/breed41.yml"))
    }
  }
}
