data class MyPoint2D(
    val x: Float,
    val y: Float,
)
private fun distance(a: MyPoint2D, b: MyPoint2D): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.hypot(dx, dy)
}