package com.todotracker.model

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Represents a single timer session for a task.
 * A task can have multiple time entries (paused and resumed sessions).
 * An entry with endTime == null is currently running.
 */
@Entity
@Table(name = "time_entries")
class TimeEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    val task: Task,

    @Column(nullable = false)
    val startTime: LocalDateTime = LocalDateTime.now(),

    /**
     * Null while the timer is running; set when the timer is stopped.
     */
    @Column
    var endTime: LocalDateTime? = null,

    /**
     * Elapsed seconds for this entry, stored when the timer is stopped.
     * While running (endTime == null), compute from startTime to now.
     */
    @Column(nullable = false)
    var durationSeconds: Long = 0
)
