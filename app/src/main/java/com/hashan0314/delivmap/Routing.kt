package com.hashan0314.delivmap

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Routing {
    fun haversine(a: LatLng, b: LatLng): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)

        val h = sin(dLat / 2).pow(2.0) + sin(dLng / 2).pow(2.0) * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(h), sqrt(1 - h))
        return earthRadius * c
    }

    fun nearestNeighborOrder(start: LatLng, stops: List<LatLng>): List<Int> {
        if (stops.isEmpty()) return emptyList()
        val remaining = stops.indices.toMutableSet()
        val order = mutableListOf<Int>()
        var current = start
        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { index -> haversine(current, stops[index]) } ?: break
            order.add(next)
            current = stops[next]
            remaining.remove(next)
        }
        return order
    }

    fun twoOpt(order: List<Int>, points: List<LatLng>, start: LatLng): List<Int> {
        if (order.size < 3) return order
        var bestOrder = order.toMutableList()
        var improved: Boolean
        var bestDistance = routeDistance(bestOrder, points, start)
        do {
            improved = false
            for (i in 0 until bestOrder.size - 1) {
                for (k in i + 1 until bestOrder.size) {
                    val newOrder = bestOrder.twoOptSwap(i, k)
                    val newDistance = routeDistance(newOrder, points, start)
                    if (newDistance + 1e-6 < bestDistance) {
                        bestOrder = newOrder.toMutableList()
                        bestDistance = newDistance
                        improved = true
                    }
                }
            }
        } while (improved)
        return bestOrder
    }

    private fun MutableList<Int>.twoOptSwap(i: Int, k: Int): List<Int> {
        val result = mutableListOf<Int>()
        result.addAll(subList(0, i))
        result.addAll(subList(i, k + 1).reversed())
        if (k + 1 < size) {
            result.addAll(subList(k + 1, size))
        }
        return result
    }

    private fun routeDistance(order: List<Int>, points: List<LatLng>, start: LatLng): Double {
        if (order.isEmpty()) return 0.0
        var distance = 0.0
        var last = start
        for (index in order) {
            val nextPoint = points[index]
            distance += haversine(last, nextPoint)
            last = nextPoint
        }
        return distance
    }
}
