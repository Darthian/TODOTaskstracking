package com.todotracker.repository

import com.todotracker.model.TimeEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface TimeEntryRepository : JpaRepository<TimeEntry, Long> {

    /**
     * Returns the currently running timer entry for a task, or null if no timer is active.
     * At most one entry per task should have endTime == null.
     */
    fun findByTaskIdAndEndTimeIsNull(taskId: Long): TimeEntry?

    /**
     * All time entries for a task, ordered by start time.
     */
    @Query("SELECT te FROM TimeEntry te WHERE te.task.id = :taskId ORDER BY te.startTime ASC")
    fun findAllByTaskId(@Param("taskId") taskId: Long): List<TimeEntry>

    /**
     * All time entries whose session started within [startDate, endDate).
     * JOIN FETCH avoids N+1 when accessing te.task fields.
     */
    @Query("""
        SELECT te FROM TimeEntry te
        JOIN FETCH te.task
        WHERE te.startTime >= :startDate
          AND te.startTime < :endDate
        ORDER BY te.startTime ASC
    """)
    fun findEntriesInRange(
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<TimeEntry>

    /**
     * Delete all time entries belonging to a task (used before deleting the task).
     */
    fun deleteAllByTaskId(taskId: Long)
}
