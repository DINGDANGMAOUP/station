package com.dingdangmaoup.station.node.discovery;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;

/**
 * Kubernetes-based node discovery
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "station.discovery.type", havingValue = "kubernetes")
public class KubernetesNodeDiscovery implements NodeDiscoveryService {

    private KubernetesClient kubernetesClient;

    @Value("${station.node.id}")
    private String nodeId;

    @Value("${station.node.grpc-port:50051}")
    private int grpcPort;

    @Value("${station.node.http-port:5000}")
    private int httpPort;

    @Value("${station.discovery.kubernetes.namespace:default}")
    private String namespace;

    @Value("${station.discovery.kubernetes.label-selector:app=station}")
    private String labelSelector;

    private volatile NodeInfo.NodeStatus currentStatus = NodeInfo.NodeStatus.HEALTHY;

    @PostConstruct
    public void init() {
        try {
            this.kubernetesClient = new KubernetesClientBuilder().build();
            log.info("Initialized Kubernetes client for namespace: {}, selector: {}",
                    namespace, labelSelector);
        } catch (Exception e) {
            log.error("Failed to initialize Kubernetes client", e);
            throw new RuntimeException("Failed to initialize Kubernetes client", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (kubernetesClient != null) {
            kubernetesClient.close();
            log.info("Closed Kubernetes client");
        }
    }

    @Override
    public Mono<Void> registerSelf() {
        log.info("Node registration not needed in Kubernetes mode (pod: {})", nodeId);
        return Mono.empty();
    }

    @Override
    public Mono<Void> deregister() {
        log.info("Node deregistration not needed in Kubernetes mode (pod: {})", nodeId);
        return Mono.empty();
    }

    @Override
    public Mono<Void> heartbeat() {
        log.trace("Heartbeat not needed in Kubernetes mode");
        return Mono.empty();
    }

    @Override
    public Flux<NodeInfo> discoverNodes() {
        return Mono.fromCallable(() -> {
            List<Pod> pods = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabel(labelSelector)
                    .list()
                    .getItems();

            log.debug("Discovered {} pods in namespace {}", pods.size(), namespace);

            return pods.stream()
                    .filter(pod -> pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase()))
                    .filter(pod -> pod.getStatus().getPodIP() != null)
                    .filter(pod -> !pod.getMetadata().getName().equals(nodeId))
                    .map(this::podToNodeInfo)
                    .toList();
        })
        .flatMapMany(Flux::fromIterable)
        .doOnNext(node -> log.trace("Discovered node: {}", node));
    }

    @Override
    public Mono<NodeInfo> getNode(String targetNodeId) {
        return Mono.fromCallable(() -> {
            Pod pod = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(targetNodeId)
                    .get();

            if (pod == null || !"Running".equals(pod.getStatus().getPhase())) {
                return null;
            }

            return podToNodeInfo(pod);
        });
    }

    @Override
    public Mono<Void> markDraining() {
        currentStatus = NodeInfo.NodeStatus.DRAINING;
        log.info("Marked node as draining: {}", nodeId);
        return Mono.empty();
    }

    private NodeInfo podToNodeInfo(Pod pod) {
        String podName = pod.getMetadata().getName();
        String podIp = pod.getStatus().getPodIP();

        String startTime = pod.getStatus().getStartTime();
        long uptimeSeconds = 0;
        if (startTime != null) {
            try {
                Instant start = Instant.parse(startTime);
                uptimeSeconds = Instant.now().getEpochSecond() - start.getEpochSecond();
            } catch (Exception e) {
                log.warn("Failed to parse pod start time: {}", startTime);
            }
        }

        NodeInfo.NodeStatus status = determineNodeStatus(pod);

        return NodeInfo.builder()
                .nodeId(podName)
                .host(podIp)
                .grpcPort(grpcPort)
                .httpPort(httpPort)
                .status(status)
                .lastHeartbeat(Instant.now())
                .uptimeSeconds(uptimeSeconds)
                .build();
    }

    /**
     * Determine node status from Pod status and current state
     */
    private NodeInfo.NodeStatus determineNodeStatus(Pod pod) {
        String podName = pod.getMetadata().getName();

        if (podName.equals(nodeId)) {
            return currentStatus;
        }

        String phase = pod.getStatus().getPhase();

        if (pod.getMetadata().getDeletionTimestamp() != null) {
            return NodeInfo.NodeStatus.DRAINING;
        }

        if (pod.getStatus().getConditions() != null) {
            boolean isReady = pod.getStatus().getConditions().stream()
                    .filter(condition -> "Ready".equals(condition.getType()))
                    .anyMatch(condition -> "True".equals(condition.getStatus()));

            if (!isReady) {
                return NodeInfo.NodeStatus.UNHEALTHY;
            }
        }

        return "Running".equals(phase)
                ? NodeInfo.NodeStatus.HEALTHY
                : NodeInfo.NodeStatus.UNHEALTHY;
    }
}
