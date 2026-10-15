package com.deekshith.naturalcam.pose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

enum class PoseCategory(val label: String, val icon: String) {
    STANDING("Standing", "🧍"),
    SITTING("Sitting", "🪑"),
    PORTRAIT_CLOSEUP("Close-Up", "🤳"),
    FOREST_NATURE("Forest / Nature", "🌲")
}

enum class SilhouetteShape {
    STANDING_HANDS_POCKETS,
    STANDING_OVER_SHOULDER,
    STANDING_SIDE_PROFILE,
    SITTING_DIAGONAL_LEGS,
    SITTING_BENCH_RELAX,
    CLOSEUP_CHIN_REST,
    CLOSEUP_THREE_QUARTER,
    FOREST_TREE_LEAN,
    FOREST_PATH_WALKER
}

data class PoseGuide(
    val id: String,
    val name: String,
    val category: PoseCategory,
    val coachingTip: String,
    val whereToStandTip: String,
    val silhouetteShape: SilhouetteShape
)

object PoseLibrary {
    val poses = listOf(
        // --- Standing Poses ---
        PoseGuide(
            id = "stand_pockets",
            name = "Casual Pockets",
            category = PoseCategory.STANDING,
            coachingTip = "Hands loosely in pockets, shift 70% weight to back leg. Lower camera to waist height for taller proportion.",
            whereToStandTip = "Stand 2.5m away, centered in the lower 2/3 of frame.",
            silhouetteShape = SilhouetteShape.STANDING_HANDS_POCKETS
        ),
        PoseGuide(
            id = "stand_shoulder",
            name = "Over-The-Shoulder",
            category = PoseCategory.STANDING,
            coachingTip = "Turn back slightly to camera, look over shoulder with relaxed smile. Drop near shoulder 1 inch.",
            whereToStandTip = "Stand 2m away along the right third vertical line.",
            silhouetteShape = SilhouetteShape.STANDING_OVER_SHOULDER
        ),
        PoseGuide(
            id = "stand_profile",
            name = "Side Profile Stance",
            category = PoseCategory.STANDING,
            coachingTip = "Turn 45° to the sunlight. Elongate neck, chin slightly forward to define jawline.",
            whereToStandTip = "Align subject along left vertical grid line, facing toward open space.",
            silhouetteShape = SilhouetteShape.STANDING_SIDE_PROFILE
        ),

        // --- Sitting Poses ---
        PoseGuide(
            id = "sit_diagonal",
            name = "Diagonal Leg Lean",
            category = PoseCategory.SITTING,
            coachingTip = "Sit on edge of chair/steps, stretch front leg diagonally toward bottom corner of camera for maximum height elongation.",
            whereToStandTip = "Sit in lower-center area, camera held at eye-level.",
            silhouetteShape = SilhouetteShape.SITTING_DIAGONAL_LEGS
        ),
        PoseGuide(
            id = "sit_bench",
            name = "Relaxed Bench Rest",
            category = PoseCategory.SITTING,
            coachingTip = "Rest one arm along bench back, cross legs at ankles, lean torso slightly back for effortless candid look.",
            whereToStandTip = "Sit centered, leaving 1/3 headroom above for environment context.",
            silhouetteShape = SilhouetteShape.SITTING_BENCH_RELAX
        ),

        // --- Close-Up Portrait Poses ---
        PoseGuide(
            id = "portrait_chin",
            name = "Gentle Chin Rest",
            category = PoseCategory.PORTRAIT_CLOSEUP,
            coachingTip = "Lightly rest finger or jaw on hand without pushing skin. Tilt head 10° toward hand.",
            whereToStandTip = "Chest-up framing, subject's eyes aligned with upper 1/3 horizontal grid line.",
            silhouetteShape = SilhouetteShape.CLOSEUP_CHIN_REST
        ),
        PoseGuide(
            id = "portrait_three_quarter",
            name = "Golden 45° Angle",
            category = PoseCategory.PORTRAIT_CLOSEUP,
            coachingTip = "Turn face 45° to camera so far ear is hidden. Eyes looking back at lens. Natural, flattering depth.",
            whereToStandTip = "Shoulder-up framing, face centered with soft backlight behind hair.",
            silhouetteShape = SilhouetteShape.CLOSEUP_THREE_QUARTER
        ),

        // --- Forest & Outdoor Nature Poses ---
        PoseGuide(
            id = "forest_tree",
            name = "Canopy Tree Lean",
            category = PoseCategory.FOREST_NATURE,
            coachingTip = "Lean one shoulder against tree trunk, look up into the leafy canopy. Captures wonder & woodland scale.",
            whereToStandTip = "Position tree trunk along left/right third, looking inward toward forest depth.",
            silhouetteShape = SilhouetteShape.FOREST_TREE_LEAN
        ),
        PoseGuide(
            id = "forest_walker",
            name = "Trail Wanderer",
            category = PoseCategory.FOREST_NATURE,
            coachingTip = "Walk slowly down forest path, glance sideways at greenery mid-stride for natural motion capture.",
            whereToStandTip = "Walk away along center path, camera angled slightly low.",
            silhouetteShape = SilhouetteShape.FOREST_PATH_WALKER
        )
    )

    /**
     * Draws the Huawei-style wireframe silhouette guide onto the camera viewfinder
     */
    fun drawSilhouette(
        drawScope: DrawScope,
        shape: SilhouetteShape,
        color: Color,
        size: Size
    ) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = 3.0f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
        )
        val solidStroke = Stroke(width = 2.5f)

        when (shape) {
            SilhouetteShape.STANDING_HANDS_POCKETS -> {
                // Head
                drawScope.drawCircle(color, radius = w * 0.085f, center = Offset(w * 0.5f, h * 0.28f), style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.5f, h * 0.28f), 5f)
                // Eye-level subtle alignment guide
                drawScope.drawLine(color.copy(alpha = 0.4f), Offset(w * 0.44f, h * 0.27f), Offset(w * 0.56f, h * 0.27f), strokeWidth = 1.5f)

                // Shoulders & Torso
                val torso = Path().apply {
                    moveTo(w * 0.36f, h * 0.36f)
                    lineTo(w * 0.64f, h * 0.36f)
                    lineTo(w * 0.60f, h * 0.58f)
                    lineTo(w * 0.40f, h * 0.58f)
                    close()
                }
                drawScope.drawPath(torso, color, style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.36f, h * 0.36f))
                drawJoint(drawScope, color, Offset(w * 0.64f, h * 0.36f))

                // Arms in pockets
                drawScope.drawLine(color, Offset(w * 0.36f, h * 0.36f), Offset(w * 0.33f, h * 0.50f), strokeWidth = 2.5f)
                drawScope.drawLine(color, Offset(w * 0.33f, h * 0.50f), Offset(w * 0.41f, h * 0.56f), strokeWidth = 2.5f)
                drawJoint(drawScope, color, Offset(w * 0.41f, h * 0.56f))

                drawScope.drawLine(color, Offset(w * 0.64f, h * 0.36f), Offset(w * 0.67f, h * 0.50f), strokeWidth = 2.5f)
                drawScope.drawLine(color, Offset(w * 0.67f, h * 0.50f), Offset(w * 0.59f, h * 0.56f), strokeWidth = 2.5f)
                drawJoint(drawScope, color, Offset(w * 0.59f, h * 0.56f))

                // Legs
                drawScope.drawLine(color, Offset(w * 0.44f, h * 0.58f), Offset(w * 0.43f, h * 0.88f), strokeWidth = 2.5f)
                drawScope.drawLine(color, Offset(w * 0.56f, h * 0.58f), Offset(w * 0.57f, h * 0.88f), strokeWidth = 2.5f)
                drawJoint(drawScope, color, Offset(w * 0.43f, h * 0.88f), 5f)
                drawJoint(drawScope, color, Offset(w * 0.57f, h * 0.88f), 5f)
            }

            SilhouetteShape.STANDING_OVER_SHOULDER -> {
                drawScope.drawCircle(color, radius = w * 0.085f, center = Offset(w * 0.60f, h * 0.30f), style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.60f, h * 0.30f), 5f)
                val torso = Path().apply {
                    moveTo(w * 0.48f, h * 0.38f)
                    lineTo(w * 0.72f, h * 0.38f)
                    lineTo(w * 0.68f, h * 0.60f)
                    lineTo(w * 0.52f, h * 0.60f)
                    close()
                }
                drawScope.drawPath(torso, color, style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.48f, h * 0.38f))
                drawJoint(drawScope, color, Offset(w * 0.72f, h * 0.38f))
                drawScope.drawLine(color, Offset(w * 0.56f, h * 0.60f), Offset(w * 0.54f, h * 0.88f), strokeWidth = 2.5f)
                drawScope.drawLine(color, Offset(w * 0.64f, h * 0.60f), Offset(w * 0.66f, h * 0.88f), strokeWidth = 2.5f)
                drawJoint(drawScope, color, Offset(w * 0.54f, h * 0.88f), 5f)
                drawJoint(drawScope, color, Offset(w * 0.66f, h * 0.88f), 5f)
            }

            SilhouetteShape.STANDING_SIDE_PROFILE -> {
                drawScope.drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.42f, h * 0.28f), style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.42f, h * 0.28f), 5f)
                // Nose direction pointer
                drawScope.drawLine(color, Offset(w * 0.42f, h * 0.28f), Offset(w * 0.52f, h * 0.27f), strokeWidth = 2f)
                val body = Path().apply {
                    moveTo(w * 0.42f, h * 0.36f)
                    lineTo(w * 0.48f, h * 0.58f)
                    lineTo(w * 0.46f, h * 0.88f)
                }
                drawScope.drawPath(body, color, style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.48f, h * 0.58f))
                drawJoint(drawScope, color, Offset(w * 0.46f, h * 0.88f), 5f)
            }

            SilhouetteShape.SITTING_DIAGONAL_LEGS -> {
                drawScope.drawCircle(color, radius = w * 0.085f, center = Offset(w * 0.50f, h * 0.38f), style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.50f, h * 0.38f), 5f)
                // Torso
                drawScope.drawLine(color, Offset(w * 0.50f, h * 0.46f), Offset(w * 0.50f, h * 0.64f), strokeWidth = 3f)
                drawJoint(drawScope, color, Offset(w * 0.50f, h * 0.64f))
                // Thighs
                drawScope.drawLine(color, Offset(w * 0.50f, h * 0.64f), Offset(w * 0.64f, h * 0.72f), strokeWidth = 2.5f)
                drawJoint(drawScope, color, Offset(w * 0.64f, h * 0.72f))
                // Diagonal Shin extended toward bottom corner
                drawScope.drawLine(color, Offset(w * 0.64f, h * 0.72f), Offset(w * 0.82f, h * 0.88f), strokeWidth = 3f)
                drawJoint(drawScope, color, Offset(w * 0.82f, h * 0.88f), 6f)
                // Support leg
                drawScope.drawLine(color, Offset(w * 0.50f, h * 0.64f), Offset(w * 0.42f, h * 0.88f), strokeWidth = 2.5f)
                drawJoint(drawScope, color, Offset(w * 0.42f, h * 0.88f), 5f)
            }

            SilhouetteShape.SITTING_BENCH_RELAX -> {
                drawScope.drawCircle(color, radius = w * 0.085f, center = Offset(w * 0.48f, h * 0.36f), style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.48f, h * 0.36f), 5f)
                drawScope.drawLine(color, Offset(w * 0.48f, h * 0.44f), Offset(w * 0.48f, h * 0.66f), strokeWidth = 3f)
                drawJoint(drawScope, color, Offset(w * 0.48f, h * 0.66f))
                // Bench line
                drawScope.drawLine(color.copy(alpha = 0.7f), Offset(w * 0.20f, h * 0.66f), Offset(w * 0.80f, h * 0.66f), strokeWidth = 2f)
                drawScope.drawLine(color, Offset(w * 0.44f, h * 0.66f), Offset(w * 0.42f, h * 0.88f), strokeWidth = 2.5f)
                drawScope.drawLine(color, Offset(w * 0.52f, h * 0.66f), Offset(w * 0.54f, h * 0.88f), strokeWidth = 2.5f)
                drawJoint(drawScope, color, Offset(w * 0.42f, h * 0.88f), 5f)
                drawJoint(drawScope, color, Offset(w * 0.54f, h * 0.88f), 5f)
            }

            SilhouetteShape.CLOSEUP_CHIN_REST -> {
                // Larger head circle for portrait framing
                drawScope.drawCircle(color, radius = w * 0.18f, center = Offset(w * 0.50f, h * 0.40f), style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.50f, h * 0.40f), 6f)
                // Eye-level framing line
                drawScope.drawLine(color.copy(alpha = 0.5f), Offset(w * 0.38f, h * 0.38f), Offset(w * 0.62f, h * 0.38f), strokeWidth = 1.5f)
                // Shoulders
                val shoulders = Path().apply {
                    moveTo(w * 0.18f, h * 0.68f)
                    quadraticBezierTo(w * 0.32f, h * 0.56f, w * 0.42f, h * 0.56f)
                    moveTo(w * 0.58f, h * 0.56f)
                    quadraticBezierTo(w * 0.68f, h * 0.56f, w * 0.82f, h * 0.68f)
                }
                drawScope.drawPath(shoulders, color, style = stroke)
                // Chin support hand indicator
                drawScope.drawCircle(color, radius = w * 0.05f, center = Offset(w * 0.56f, h * 0.55f), style = solidStroke)
                drawJoint(drawScope, color, Offset(w * 0.56f, h * 0.55f), 5f)
            }

            SilhouetteShape.CLOSEUP_THREE_QUARTER -> {
                drawScope.drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.50f, h * 0.38f), style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.50f, h * 0.38f), 6f)
                // 45 degree gaze line
                drawScope.drawLine(color.copy(alpha = 0.5f), Offset(w * 0.50f, h * 0.38f), Offset(w * 0.62f, h * 0.36f), strokeWidth = 1.5f)
                val shoulders = Path().apply {
                    moveTo(w * 0.16f, h * 0.70f)
                    quadraticBezierTo(w * 0.35f, h * 0.54f, w * 0.50f, h * 0.54f)
                    quadraticBezierTo(w * 0.65f, h * 0.54f, w * 0.84f, h * 0.70f)
                }
                drawScope.drawPath(shoulders, color, style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.28f, h * 0.58f))
                drawJoint(drawScope, color, Offset(w * 0.72f, h * 0.58f))
            }

            SilhouetteShape.FOREST_TREE_LEAN -> {
                // Tree trunk reference on left
                drawScope.drawLine(color.copy(alpha = 0.5f), Offset(w * 0.22f, 0f), Offset(w * 0.22f, h), strokeWidth = 3f)
                drawScope.drawLine(color.copy(alpha = 0.5f), Offset(w * 0.30f, 0f), Offset(w * 0.30f, h), strokeWidth = 3f)
                // Leaning Subject
                drawScope.drawCircle(color, radius = w * 0.085f, center = Offset(w * 0.44f, h * 0.32f), style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.44f, h * 0.32f), 5f)
                val torso = Path().apply {
                    moveTo(w * 0.36f, h * 0.40f)
                    lineTo(w * 0.52f, h * 0.42f)
                    lineTo(w * 0.48f, h * 0.62f)
                    lineTo(w * 0.36f, h * 0.62f)
                    close()
                }
                drawScope.drawPath(torso, color, style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.36f, h * 0.40f))
                drawScope.drawLine(color, Offset(w * 0.42f, h * 0.62f), Offset(w * 0.42f, h * 0.88f), strokeWidth = 2.5f)
                drawJoint(drawScope, color, Offset(w * 0.42f, h * 0.88f), 5f)
            }

            SilhouetteShape.FOREST_PATH_WALKER -> {
                drawScope.drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.50f, h * 0.32f), style = stroke)
                drawJoint(drawScope, color, Offset(w * 0.50f, h * 0.32f), 5f)
                drawScope.drawLine(color, Offset(w * 0.50f, h * 0.40f), Offset(w * 0.50f, h * 0.60f), strokeWidth = 3f)
                drawJoint(drawScope, color, Offset(w * 0.50f, h * 0.60f))
                // Striding legs
                drawScope.drawLine(color, Offset(w * 0.50f, h * 0.60f), Offset(w * 0.40f, h * 0.88f), strokeWidth = 2.5f)
                drawScope.drawLine(color, Offset(w * 0.50f, h * 0.60f), Offset(w * 0.60f, h * 0.86f), strokeWidth = 2.5f)
                drawJoint(drawScope, color, Offset(w * 0.40f, h * 0.88f), 5f)
                drawJoint(drawScope, color, Offset(w * 0.60f, h * 0.86f), 5f)
            }
        }
    }

    private fun drawJoint(drawScope: DrawScope, color: Color, center: Offset, radius: Float = 5.5f) {
        // Outer tracking ring
        drawScope.drawCircle(
            color = color,
            radius = radius + 3.5f,
            center = center,
            style = Stroke(width = 1.5f)
        )
        // Solid center core
        drawScope.drawCircle(
            color = color,
            radius = radius,
            center = center
        )
    }
}


