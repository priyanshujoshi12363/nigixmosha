package com.nigixmosha.app.data.model;

import java.util.List;

public final class Responses {
    private Responses() {
    }

    public static final class UserEnvelope {
        public User user;
    }

    public static final class ProjectsEnvelope {
        public List<Project> projects;
    }

    public static final class ErrorEnvelope {
        public String error;
        public String code;
    }
}
