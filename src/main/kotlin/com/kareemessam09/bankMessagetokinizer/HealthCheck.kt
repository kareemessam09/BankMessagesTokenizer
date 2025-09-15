package com.kareemessam09.bankMessagetokinizer

/**
 * Simple health check for containerized deployments
 */
object HealthCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            // Simple health check logic
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryUsage = (usedMemory.toDouble() / maxMemory * 100).toInt()

            if (memoryUsage > 90) {
                println("UNHEALTHY: Memory usage too high ($memoryUsage%)")
                System.exit(1)
            }

            println("HEALTHY: Memory usage normal ($memoryUsage%)")
            System.exit(0)
        } catch (e: Exception) {
            println("UNHEALTHY: Health check failed - ${e.message}")
            System.exit(1)
        }
    }
}
