package com.omnexus.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

@Getter
@Setter
public class NodeFailureEvent extends ApplicationEvent {
    private String clusterId;
    private String nodeId;
    private String failureType; // "dead", "unhealthy", "timeout"
    private String errorMessage;
    private boolean autoRestart;

    public NodeFailureEvent(Object source, String clusterId, String nodeId,
                            String failureType, String errorMessage, boolean autoRestart) {
        super(source);
        this.clusterId = clusterId;
        this.nodeId = nodeId;
        this.failureType = failureType;
        this.errorMessage = errorMessage;
        this.autoRestart = autoRestart;
    }
}