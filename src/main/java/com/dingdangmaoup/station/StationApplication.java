package com.dingdangmaoup.station;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    exclude = {
    }
//    exclude = {
//    org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration.class
//}
)
@EnableScheduling
public class StationApplication {

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(StationApplication.class);
    app.setApplicationStartup(new BufferingApplicationStartup(2048));
    app.run(args);
//    SpringApplication.run(StationApplication.class, args);
  }

}
