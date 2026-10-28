package com.envguard.domain.enums;

import java.util.EnumSet;
import java.util.Set;

public enum DeploymentRequestStatus {
    BUILDING,
    READY_FOR_APPROVAL,
    APPROVED,
    REJECTED,
    DEPLOYING,
    DEPLOYED,
    FAILED,
    CANCELLED,
    TIMED_OUT,
    INVALIDATED;

    private static final Set<DeploymentRequestStatus> TERMINAL = EnumSet.of(
            REJECTED, DEPLOYED, FAILED, CANCELLED, TIMED_OUT, INVALIDATED
    );

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(DeploymentRequestStatus target) {
        return switch (this) {
            case BUILDING -> target == READY_FOR_APPROVAL || target == FAILED || target == CANCELLED || target == INVALIDATED;
            case READY_FOR_APPROVAL -> target == APPROVED || target == REJECTED || target == TIMED_OUT
                    || target == CANCELLED || target == INVALIDATED;
            case APPROVED -> target == DEPLOYING || target == CANCELLED || target == INVALIDATED || target == TIMED_OUT;
            case DEPLOYING -> target == DEPLOYED || target == FAILED || target == INVALIDATED;
            case REJECTED, DEPLOYED, FAILED, CANCELLED, TIMED_OUT, INVALIDATED -> false;
        };
    }
}
