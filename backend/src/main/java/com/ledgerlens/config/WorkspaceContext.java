package com.ledgerlens.config;

import java.util.UUID;

public final class WorkspaceContext {

    private static final ThreadLocal<UUID> CURRENT_WORKSPACE = new ThreadLocal<>();

    private WorkspaceContext() {
    }

    public static void setWorkspaceId(UUID workspaceId) {
        CURRENT_WORKSPACE.set(workspaceId);
    }

    public static UUID requireWorkspaceId() {
        UUID workspaceId = CURRENT_WORKSPACE.get();
        if (workspaceId == null) {
            throw new IllegalStateException("O espaço de trabalho não foi definido para esta requisição.");
        }
        return workspaceId;
    }

    public static void clear() {
        CURRENT_WORKSPACE.remove();
    }
}
