package es.docklite.docklitebackend.audit.entity;

public enum ActivityAction {
    CREATE,
    START,
    STOP,
    RESTART,
    DELETE,
    PULL,
    CONNECT,
    DISCONNECT,
    UPDATE_MOUNTS,
    UPDATE_PORTS,
    UPDATE_ENV,
    UPDATE_COMMAND,
    UPDATE_HEALTHCHECK,
    UPDATE_RESOURCES,
    RENAME
}
