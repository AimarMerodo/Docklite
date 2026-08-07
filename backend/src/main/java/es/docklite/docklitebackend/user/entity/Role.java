package es.docklite.docklitebackend.user.entity;

public enum Role {
    ADMIN,
    USER,
    /** Read-only visitor for the public demo: sees its own resources but every mutation is rejected. */
    DEMO
}
