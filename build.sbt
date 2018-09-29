
lazy val `cluster-sandbox` =
  project
    .in(file("."))
    .enablePlugins(DockerPlugin, JavaAppPackaging)
    .settings(settings)
    .settings(libraryDependencies ++= Seq(
      dependencies.akkaActorTyped,
      dependencies.akkaClusterShardingTyped,
      dependencies.akkaDiscoveryDns,
      dependencies.akkaHttp,
      dependencies.akkaHttpSession,
      dependencies.akkaLog4j,
      dependencies.akkaManagementClusterBootstrap,
      dependencies.akkaManagementClusterHttp,
      dependencies.akkaPersistenceCassandra,
      dependencies.akkaPersistenceQuery,
      dependencies.akkaPersistenceTyped,
      dependencies.akkaStreamTyped,
      dependencies.alpakkaSse,
      dependencies.circeGeneric,
      dependencies.circeParser,
      dependencies.disruptor,
      dependencies.jaxbApi,
      dependencies.log4jApiScala,
      dependencies.log4jCore,
      dependencies.log4jSlf4jImpl, // Needed for transient slf4j depencencies, e.g. via akak-persistence-cassandra!
      dependencies.pureConfig,
      dependencies.embeddedKafka
    ))

lazy val settings =
  commonSettings ++
  dockerSettings

lazy val commonSettings =
  Seq(
    name := "cluster-sandbox",
    version := "0.0.1",
    scalaVersion := "2.12.6",
    organization := "grmontpetit",
    organizationName := "Gabriel Robitaille-Montpetit",
    startYear := Some(2018),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding", "UTF-8",
      "-Ypartial-unification",
      "-Ywarn-unused-import"
    )
  )

lazy val dockerSettings =
  Seq(
    Docker / daemonUser := "root",
    Docker / maintainer := "Gabriel Robitaille-Montpetit",
    Docker / version := "latest",
    dockerBaseImage := "openjdk:10.0.2-slim",
    dockerExposedPorts := Seq(2552, 8558),
    dockerUsername := Some("sniggel"),
    defaultLinuxInstallLocation := "/opt/docker"
  )

// Dependencies
lazy val dependencies =
  new {
    object Version {
      val akka                     = "2.5.17"
      val akkaHttp                 = "10.1.1"
      val akkaHttpJson             = "1.20.1"
      val akkaHttpSession          = "0.5.5"
      val akkaLog4j                = "1.6.1"
      val akkaManagement           = "0.16.0"
      val akkaPersistenceCassandra = "0.90"
      val akkaPersistenceInmemory  = "2.5.1.1"
      val alpakka                  = "0.19"
      val circe                    = "0.9.3"
      val disruptor                = "3.4.2"
      val jaxb                     = "2.3.0"
      val log4j                    = "2.11.0"
      val log4jApiScala            = "11.0"
      val mockito                  = "2.18.3"
      val pureConfig               = "0.9.1"
      val scalaCheck               = "1.14.0"
      val utest                    = "0.6.4"
      val kafka                    = "2.0.0"
    }
    val akkaActorTyped                 = "com.typesafe.akka"                  %% "akka-actor-typed"                  % Version.akka
    val akkaClusterShardingTyped       = "com.typesafe.akka"                  %% "akka-cluster-sharding-typed"       % Version.akka
    val akkaDiscoveryDns               = "com.lightbend.akka.discovery"       %% "akka-discovery-dns"                % Version.akkaManagement
    val akkaHttp                       = "com.typesafe.akka"                  %% "akka-http"                         % Version.akkaHttp
    val akkaHttpSession                = "com.softwaremill.akka-http-session" %% "core"                              % Version.akkaHttpSession
    val akkaHttpTestkit                = "com.typesafe.akka"                  %% "akka-http-testkit"                 % Version.akkaHttp
    val akkaLog4j                      = "de.heikoseeberger"                  %% "akka-log4j"                        % Version.akkaLog4j
    val akkaManagementClusterBootstrap = "com.lightbend.akka.management"      %% "akka-management-cluster-bootstrap" % Version.akkaManagement
    val akkaManagementClusterHttp      = "com.lightbend.akka.management"      %% "akka-management-cluster-http"      % Version.akkaManagement
    val akkaPersistenceCassandra       = "com.typesafe.akka"                  %% "akka-persistence-cassandra"        % Version.akkaPersistenceCassandra
    val embeddedKafka                  = "com.typesafe.akka"                  %% "akka-persistence-cassandra-launcher" % Version.akkaPersistenceCassandra
    val akkaPersistenceInmemory        = "com.github.dnvriend"                %% "akka-persistence-inmemory"         % Version.akkaPersistenceInmemory
    val akkaPersistenceQuery           = "com.typesafe.akka"                  %% "akka-persistence-query"            % Version.akka
    val akkaPersistenceTyped           = "com.typesafe.akka"                  %% "akka-persistence-typed"            % Version.akka
    val akkaStreamTyped                = "com.typesafe.akka"                  %% "akka-stream-typed"                 % Version.akka
    val akkaStreamTestkit              = "com.typesafe.akka"                  %% "akka-stream-testkit"               % Version.akka
    val akkaTestkitTyped               = "com.typesafe.akka"                  %% "akka-testkit-typed"                % Version.akka
    val alpakkaSse                     = "com.lightbend.akka"                 %% "akka-stream-alpakka-sse"           % Version.alpakka
    val circeGeneric                   = "io.circe"                           %% "circe-generic"                     % Version.circe
    val circeParser                    = "io.circe"                           %% "circe-parser"                      % Version.circe
    val disruptor                      = "com.lmax"                           %  "disruptor"                         % Version.disruptor
    val jaxbApi                        = "javax.xml.bind"                     %  "jaxb-api"                          % Version.jaxb
    val log4jApiScala                  = "org.apache.logging.log4j"           %% "log4j-api-scala"                   % Version.log4jApiScala
    val log4jCore                      = "org.apache.logging.log4j"           %  "log4j-core"                        % Version.log4j
    val log4jSlf4jImpl                 = "org.apache.logging.log4j"           %  "log4j-slf4j-impl"                  % Version.log4j
    val mockitoInline                  = "org.mockito"                        %  "mockito-inline"                    % Version.mockito
    val pureConfig                     = "com.github.pureconfig"              %% "pureconfig"                        % Version.pureConfig
    val scalaCheck                     = "org.scalacheck"                     %% "scalacheck"                        % Version.scalaCheck
    val utest                          = "com.lihaoyi"                        %% "utest"                             % Version.utest
  }