package com.ethan.familyadmin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyAdminApplicationTest {

    @Test
    void applicationClassCanBeLoaded() {
        assertThat(FamilyAdminApplication.class).isNotNull();
    }
}

