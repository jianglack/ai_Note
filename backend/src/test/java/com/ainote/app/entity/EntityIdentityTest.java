package com.ainote.app.entity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class EntityIdentityTest {

    @Test
    void notesUsePersistentIdIdentityInHashSets() {
        Note first = new Note();
        first.setId("note-1");
        Note second = new Note();
        second.setId("note-1");

        HashSet<Note> notes = new HashSet<>();
        notes.add(first);
        notes.add(second);

        assertThat(notes).hasSize(1);
    }

    @Test
    void tagsUsePersistentIdIdentityInHashSets() {
        Tag first = new Tag();
        first.setId("tag-1");
        Tag second = new Tag();
        second.setId("tag-1");

        HashSet<Tag> tags = new HashSet<>();
        tags.add(first);
        tags.add(second);

        assertThat(tags).hasSize(1);
    }

    @Test
    void schedulesUsePersistentIdIdentityInHashSets() {
        Schedule first = new Schedule();
        first.setId("schedule-1");
        Schedule second = new Schedule();
        second.setId("schedule-1");

        HashSet<Schedule> schedules = new HashSet<>();
        schedules.add(first);
        schedules.add(second);

        assertThat(schedules).hasSize(1);
    }
}
