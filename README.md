HRRS (HTTP Request Record Suite) is a set of tools that you can leverage to
record and replay HTTP requests in your JEE and Spring web applications written
in Java 6 or higher. In essence, HRRS bundles a servlet filter for recording
and a standalone command-line Java application for replaying the requests.

# Rationale

Why would someone want to record HTTP requests as is? There are two major
problems that HRRS is aiming to solve:

- **Realistic performance tests:** Artificially generated test data falls
  short of covering many production states. Testing with unrealistic user
  behaviour can cause caches to misbehave. Or benchmarks might have used
  JSON/XML for simplicity, while the actual production systems communicate
  over a binary protocol such as Protocol Buffers or Thrift. These short
  comings undermine the reliability of performance figures and renders
  regression reports unusable. HRRS lets the production load to be stored
  and reflected back to the test environment for more credible test results.

- **Diagnosing production problems:** It might not always be a viable option
  to remotely debug an instance for production surfacing problems. HRRS can be
  leveraged to record the problem on production and replay it on development
  environment for further inspection.

# Overview

HRRS ships the following artifacts:

- **hrrs-api:** Basic API models and interfaces like `HttpRequestHeader`,
  `HttpRequestRecord`, `HttpRequestRecordReader`,
  `HttpRequestRecordReaderSource`, etc.
- **hrrs-servlet-filter:** Basic servlet filter leveraging the functionality
  of the API interfaces.
- **hrrs-replayer:** The command line replayer application.

These artifacts provide interfaces for the potential concrete implementations.
Fortunately, we provide one for you: File-based Base64 implementation. That is,
HTTP request records are encoded in Base64 and stored in a plain text file.
Following artifacts provide this functionality:

- **hrrs-serializer-base64:** The reader/writer implementation using Base64.
- **hrrs-servlet-filter-base64:** Servlet filter implementation using the Base64
  serializer.
- **hrrs-replayer-base64:** The command line replayer implementation using the
  Base64 serializer.

Per see, HRRS is designed with extensibility in mind. As of now, it only
supports file sourced/targeted Base64 readers/writers. But all you need is a
few lines of code to introduce your own serialization schemes powered by a
storage backend (RDBMS, NoSQL, etc.) of your preference.

Source code also contains the following modules to exemplify the usage of HRRS
with certain Java web frameworks:

- **hrrs-example-jaxrs**
- **hrrs-example-spring**

# Getting Started

In order to start recording HTTP requests, all you need is to plug the HRRS
servlet filter into your Java web application. Below, we will use Base64
serialization for recording HTTP requests in a Spring web application. (See
`example` directory for the actual sources and the JAX-RS example.)

Add the HRRS servlet filter Maven dependency in your `pom.xml`:

```xml
<dependency>
    <groupId>com.vlkan.hrrs</groupId>
    <artifactId>hrrs-servlet-filter-base64</artifactId>
    <version>${hrrs.version}</version>
</dependency>
```

In the second and last step, you expose the HRRS servlet filter as a bean so
that Spring can inject it as an interceptor:

```java
@Configuration
public class HrrsConfig {

    @Bean
    public HrrsFilter provideHrrsFilter() throws IOException {
        File writerTargetFile = File.createTempFile("hrrs-spring-records-", ".csv");
        return new Base64HrrsFilter(writerTargetFile);
    }

}
```

And that's it! The incoming HTTP requests will be recorded into
`writerTargetFile`. (You can also run `HelloApplication` of `example/spring`
in your IDE to see it in action.) Let's take a quick look at the contents
of the Base64-serialized HTTP request records:

```bash
$ zcat records.csv.gz | head -n 3
iyt6t60z_1sqtj  AA5peXQ2dDYwel8xc3F0agAFaGVsbG8AAAFaEDO9ywATL2hlbGxvP25hbWU9Zm9vLTEtMQAEUE9TVAAAAAABAAp0ZXh0L3BsYWluAAAAAAAAAAAAAAADX///
iyt6t61e_le34v  AA5peXQ2dDYxZV9sZTM0dgAFaGVsbG8AAAFaEDO90gATL2hlbGxvP25hbWU9Zm9vLTEtMgAEUE9TVAAAAAABAAp0ZXh0L3BsYWluAAAAAAAAAAAAAAAEcVL//w==
iyt6t624_5jvlj  AA5peXQ2dDYyNF81anZsagAFaGVsbG8AAAFaEDO97AATL2hlbGxvP25hbWU9Zm9vLTEtMwAEUE9TVAAAAAABAAp0ZXh0L3BsYWluAAAAAAAAAAAAAAAFW2t///8=
```

Here each line corresponds to an HTTP request record, where it is associated
with an identifier and the details (URI, method, headers, payload, etc.) are
in Base64-encoded binary.

Once you start recording HTTP requests, you
can setup [logrotate](https://github.com/logrotate/logrotate) to periodically
rotate and compress the record output file. You can even take one step further
and schedule a cron job to copy these records to a directory accessible by your
test environment.

You can replay HTTP request records using the replayer provided by HRRS:

```bash
$ java \
    -jar /path/to/hrrs-replayer-base64-<version>.jar \
    --targetHost localhost \
    --targetPort 8080 \
    --threadCount 10 \
    --maxRequestCountPerSecond 1000 \
    --inputUri file:///path/to/records.csv.gz
```

Below is the list of parameters supported by the replayer.

| Parameter | Required | Default | Description |
| --------- | -------- | ------- | ----------- |
| --help, -h | N | false | display this help and exit |
| --inputUri, -i | Y | | input URI for HTTP records (Base64 replayer can accept input URIs with `.gz` suffix.) |
| --jtlOutputFile, -oj | N | | Apache JMeter JTL output file for test results |
| --localAddress, -l | N | | address to bind to when making outgoing connections |
| --loggerLevelSpecs, -L | N | `*=warn,com.vlkan.hrrs=info` | comma-separated list of `loggerName=loggerLevel` pairs |
| --maxRequestCountPerSecond, -r | N | 1 | number of concurrent requests per second |
| --metricsOutputFile, -om | N | | output file to dump Dropwizard metrics |
| --metricsOutputPeriodSeconds, -mp | N | 10 | Dropwizard metrics report frequency in seconds |
| --rampUpDurationSeconds, -d | N | 1 | ramp up duration in seconds to reach to the maximum number of requests |
| --requestTimeoutSeconds, -t | N | 10 | HTTP request connect/write/read timeout in seconds |
| --targetHost, -th | Y | | remote HTTP server host |
| --targetPort, -tp | Y | | remote HTTP server port |
| --threadCount, -n | N | 2 | HTTP request worker pool size |
| --totalDurationSeconds, -D | N | 10 | total run duration in seconds |

# Recorder Configuration

By default, HRRS servlet filter records every HTTP request along with its
payload. This certainly is not a desired option for many applications. For such
cases, you can override certain methods of the `HrrsFilter` to have a more
fine-grained control over the recorder.

```java
public abstract class HrrsFilter implements Filter {

    // ...

    /**
     * Checks if the given HTTP request is recordable.
     */
    protected boolean isRequestRecordable(HttpServletRequest request) {
        return true;
    }

    /**
     * Maximum amount of bytes that can be recorded per request.
     */
    protected long getMaxRecordablePayloadByteCount() {
        return Long.MAX_VALUE;
    }

    /**
     * Create a group name for the given request.
     *
     * Group names are used to group requests and later on are used
     * as identifiers while reporting statistics in the replayer.
     * It is strongly recommended to use group names similar to Java
     * package names.
     */
    protected String createRequestGroupName(HttpServletRequest request) {
        String requestUri = createRequestUri(request);
        return requestUri
                .replaceFirst("\\?.*", "")      // Replace query parameters.
                .replaceFirst("^/", "")         // Replace the initial slash.
                .replaceAll("/", ".");          // Replace all slashes with dots.
    }

    /**
     * Creates a unique identifier for the given request.
     */
    protected String createRequestId(HttpServletRequest request) {
        return ID_GENERATOR.next();
    }

    // ...

}
```

# Recorder Performance

HRRS provided servlet filter wraps the input stream of the HTTP request model.
Whenever user consumes from the input, we store the read bytes in a seperate
buffer, which later on gets Base64-encoded at request completion. There are
two issues with this approach:

- Duplication increases the memory usage.
- Encoding and storing the requests adds an extra processing overhead.

It is possible to use a fixed (thread local?) memory pool to avoid extra memory
allocations for each request. Further, encoding and storing can also be
performed in a separate thread to not block the request handler thread.
These being said, HRRS is successfully deployed on a 6-node JEE aplication
cluster (each node handles approximately 600 reqs/sec and requests generally
contain a payload close to 50KB) without any noticable memory or processing
overhead.

Additionally, you can override `HrrsFilter#isRequestRecordable()` and
`getMaxRecordablePayloadByteCount()` methods to have a more fine-grained
control over the recorded HTTP requests.

# Replayer Reports

If you have ever used HTTP benchmarking tools like
[JMeter](https://jmeter.apache.org/) or [Gatling](https://gatling.io/), then
you should be familiar with the reports generated by these tools. Rather than
generating its own eye candy reports, HRRS optionally (`--jtlOutputFile`)
dumps a [JMeter JTL file](https://wiki.apache.org/jmeter/JtlFiles) with the
statistics (timestamp, latency, etc.) of each executed request. A quick peek
at the JMeter JTL file looks as follows:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testResults version="1.2">
<httpSample t="108" lt="108" ts="1486330510795" s="true" rc="200" lb="hello" tn="RateLimitedExecutor-0"/>
<httpSample t="6" lt="6" ts="1486330510802" s="true" rc="200" lb="hello" tn="RateLimitedExecutor-1"/>
<httpSample t="3" lt="3" ts="1486330510828" s="true" rc="200" lb="hello" tn="RateLimitedExecutor-0"/>
...
```

For an overview or to track the progress, you can also command HRRS to
periodically dump Dropwizard metrics (`--metricsOutputFile` and
`--metricsOutputPeriodSeconds`) to a file as well. HRRS uses `ConsoleReporter`
of Dropwizard metrics to dump the statistics, which look as follows:

```
__all__
             count = 10
         mean rate = 1.01 calls/second
     1-minute rate = 1.00 calls/second
     5-minute rate = 1.00 calls/second
    15-minute rate = 1.00 calls/second
               min = 3.00 milliseconds
               max = 108.00 milliseconds
              mean = 16.15 milliseconds
            stddev = 29.46 milliseconds
            median = 8.00 milliseconds
              75% <= 9.00 milliseconds
              95% <= 108.00 milliseconds
              98% <= 108.00 milliseconds
              99% <= 108.00 milliseconds
            99.9% <= 108.00 milliseconds
__all__.200
             count = 10
         mean rate = 1.01 calls/second
     1-minute rate = 1.00 calls/second
     5-minute rate = 1.00 calls/second
    15-minute rate = 1.00 calls/second
               min = 3.00 milliseconds
               max = 108.00 milliseconds
              mean = 16.15 milliseconds
            stddev = 29.46 milliseconds
            median = 8.00 milliseconds
              75% <= 9.00 milliseconds
              95% <= 108.00 milliseconds
              98% <= 108.00 milliseconds
              99% <= 108.00 milliseconds
            99.9% <= 108.00 milliseconds
hello
             count = 10
         mean rate = 1.01 calls/second
     1-minute rate = 1.00 calls/second
     5-minute rate = 1.00 calls/second
    15-minute rate = 1.00 calls/second
               min = 3.00 milliseconds
               max = 108.00 milliseconds
              mean = 16.15 milliseconds
            stddev = 29.46 milliseconds
            median = 8.00 milliseconds
              75% <= 9.00 milliseconds
              95% <= 108.00 milliseconds
              98% <= 108.00 milliseconds
              99% <= 108.00 milliseconds
            99.9% <= 108.00 milliseconds
hello.200
             count = 10
         mean rate = 1.01 calls/second
     1-minute rate = 1.00 calls/second
     5-minute rate = 1.00 calls/second
    15-minute rate = 1.00 calls/second
               min = 3.00 milliseconds
               max = 108.00 milliseconds
              mean = 16.15 milliseconds
            stddev = 29.46 milliseconds
            median = 8.00 milliseconds
              75% <= 9.00 milliseconds
              95% <= 108.00 milliseconds
              98% <= 108.00 milliseconds
              99% <= 108.00 milliseconds
            99.9% <= 108.00 milliseconds
```

Here HRRS updates a Dropwizard timer with label `<groupName>.<responseCode>`
for each executed request. It also updates the metrics of a pseudo group,
called `__all__`, which covers all the existing groups.

# Replayer Debugging

Sometimes it becomes handy to have more insight into the replayer internals.
For such cases, you can increase the logging verbosity of certain packages.
As a starting point, adding `--loggerLevelSpecs "*=info,com.vlkan.hrrs=trace"`
to the replayer arguments is generally a good idea. Note that, you don't want
to have such a level of verbosity while executing the actual performance tests.

# License

Copyright 2017 Yazıcı, Volkan.

Licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)
(the "License"); you may not use this file except in compliance with the
License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
