package com.todotracker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TodoTrackerApplication

fun main(args: Array<String>) {
    runApplication<TodoTrackerApplication>(*args)
}
