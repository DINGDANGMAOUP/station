package com.dingdangmaoup.station.lifecycle;

import com.dingdangmaoup.station.grpc.client.StationGrpcClient;
import com.dingdangmaoup.station.node.discovery.NodeDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Handles graceful shutdown of the application
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GracefulShutdownHandler implements ApplicationListener<ContextClosedEvent> {

    private final NodeDiscoveryService nodeDiscoveryService;
    private final StationGrpcClient grpcClient;
    private final ReadinessProbe readinessProbe;

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("=== Starting graceful shutdown ===");

        try {
            log.info("Step 1: Marking readiness probe as draining");
            readinessProbe.setDraining(true);

            log.info("Step 2: Marking node as draining in discovery service");
            nodeDiscoveryService.markDraining()
                    .block(Duration.ofSeconds(5));

            log.info("Step 3: Waiting for in-flight requests to complete (max 30s)");
            Thread.sleep(2000);

            log.info("Step 4: Closing gRPC client connections");
            grpcClient.closeAllChannels();

            log.info("Step 5: Deregistering node from discovery service");
            nodeDiscoveryService.deregister()
                    .block(Duration.ofSeconds(5));

            log.info("=== Graceful shutdown completed successfully ===");

        } catch (Exception e) {
            log.error("Error during graceful shutdown", e);
        }
    }
}
