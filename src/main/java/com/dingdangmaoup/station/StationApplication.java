package com.dingdangmaoup.station;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StationApplication {

  public static void main(String[] args) {
    //Enable startup probe
    SpringApplication app = new SpringApplication(StationApplication.class);
    app.setApplicationStartup(new BufferingApplicationStartup(2048));
    app.run(args);
//    SpringApplication.run(StationApplication.class, args);
  }

}
