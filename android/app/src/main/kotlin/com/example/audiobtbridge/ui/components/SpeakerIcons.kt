package com.example.audiobtbridge.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Lightweight 24dp Material-style vector icons without pulling the multi-MB icons-extended artifact. */
object SpeakerIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    private val fillBrush = SolidColor(Color.Black)

    val PlayArrow: ImageVector by lazy {
        icon("PlayArrow") {
            path(fill = fillBrush) {
                moveTo(8f, 5.14f)
                verticalLineToRelative(14f)
                lineToRelative(11f, -7f)
                close()
            }
        }
    }

    val Stop: ImageVector by lazy {
        icon("Stop") {
            path(fill = fillBrush) {
                moveTo(6f, 6f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(12f)
                horizontalLineTo(6f)
                close()
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            path(
                stroke = fillBrush,
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }
    }

    val Speaker: ImageVector by lazy {
        icon("Speaker") {
            path(
                stroke = fillBrush,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 3f)
                horizontalLineTo(18f)
                verticalLineTo(21f)
                horizontalLineTo(6f)
                close()
                moveTo(12f, 7f)
                moveToRelative(-1f, 0f)
                arcToRelative(1f, 1f, 0f, true, true, 2f, 0f)
                arcToRelative(1f, 1f, 0f, true, true, -2f, 0f)
                moveTo(12f, 15f)
                moveToRelative(-3.5f, 0f)
                arcToRelative(3.5f, 3.5f, 0f, true, true, 7f, 0f)
                arcToRelative(3.5f, 3.5f, 0f, true, true, -7f, 0f)
            }
        }
    }

    val GraphicEq: ImageVector by lazy {
        icon("GraphicEq") {
            path(
                stroke = fillBrush,
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 10f); lineTo(4f, 14f)
                moveTo(8f, 6f); lineTo(8f, 18f)
                moveTo(12f, 3f); lineTo(12f, 21f)
                moveTo(16f, 7f); lineTo(16f, 17f)
                moveTo(20f, 10f); lineTo(20f, 14f)
            }
        }
    }

    val Wifi: ImageVector by lazy {
        icon("Wifi") {
            path(
                stroke = fillBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(2f, 8.8f); quadTo(12f, 1f, 22f, 8.8f)
                moveTo(5.5f, 12.6f); quadTo(12f, 7.2f, 18.5f, 12.6f)
                moveTo(9f, 16.4f); quadTo(12f, 13.8f, 15f, 16.4f)
            }
            path(fill = fillBrush) {
                moveTo(12f, 19.5f)
                moveToRelative(-1.4f, 0f)
                arcToRelative(1.4f, 1.4f, 0f, true, true, 2.8f, 0f)
                arcToRelative(1.4f, 1.4f, 0f, true, true, -2.8f, 0f)
            }
        }
    }

    val QrCodeScanner: ImageVector by lazy {
        icon("QrCodeScanner") {
            path(
                stroke = fillBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 7f); lineTo(3f, 3f); lineTo(7f, 3f)
                moveTo(17f, 3f); lineTo(21f, 3f); lineTo(21f, 7f)
                moveTo(21f, 17f); lineTo(21f, 21f); lineTo(17f, 21f)
                moveTo(7f, 21f); lineTo(3f, 21f); lineTo(3f, 17f)
                moveTo(7f, 7f); horizontalLineTo(10.5f); verticalLineTo(10.5f); horizontalLineTo(7f); close()
                moveTo(13.5f, 7f); horizontalLineTo(17f); verticalLineTo(10.5f); horizontalLineTo(13.5f); close()
                moveTo(7f, 13.5f); horizontalLineTo(10.5f); verticalLineTo(17f); horizontalLineTo(7f); close()
                moveTo(14f, 14f); lineTo(17f, 14f); moveTo(17f, 17f); lineTo(14f, 17f)
            }
        }
    }

    val Pin: ImageVector by lazy {
        icon("Pin") {
            path(
                stroke = fillBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(9f, 5f); lineTo(7f, 19f)
                moveTo(17f, 5f); lineTo(15f, 19f)
                moveTo(5f, 9.5f); lineTo(19f, 9.5f)
                moveTo(4f, 14.5f); lineTo(18f, 14.5f)
            }
        }
    }

    val Tune: ImageVector by lazy {
        icon("Tune") {
            path(
                stroke = fillBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 6f); lineTo(20f, 6f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 18f); lineTo(20f, 18f)
            }
            path(fill = fillBrush) {
                moveTo(9f, 6f); moveToRelative(-2f, 0f); arcToRelative(2f, 2f, 0f, true, true, 4f, 0f); arcToRelative(2f, 2f, 0f, true, true, -4f, 0f)
                moveTo(15f, 12f); moveToRelative(-2f, 0f); arcToRelative(2f, 2f, 0f, true, true, 4f, 0f); arcToRelative(2f, 2f, 0f, true, true, -4f, 0f)
                moveTo(10f, 18f); moveToRelative(-2f, 0f); arcToRelative(2f, 2f, 0f, true, true, 4f, 0f); arcToRelative(2f, 2f, 0f, true, true, -4f, 0f)
            }
        }
    }

    val Usb: ImageVector by lazy {
        icon("Usb") {
            path(
                stroke = fillBrush,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f); lineTo(12f, 18f)
                moveTo(9.5f, 5.5f); lineTo(12f, 3f); lineTo(14.5f, 5.5f)
                moveTo(12f, 15f); lineTo(7f, 12f); lineTo(7f, 9f)
                moveTo(12f, 13f); lineTo(17f, 10f); lineTo(17f, 8f)
            }
            path(fill = fillBrush) {
                moveTo(12f, 19.5f); moveToRelative(-2f, 0f); arcToRelative(2f, 2f, 0f, true, true, 4f, 0f); arcToRelative(2f, 2f, 0f, true, true, -4f, 0f)
            }
        }
    }

    val Computer: ImageVector by lazy {
        icon("Computer") {
            path(
                stroke = fillBrush,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 5f); horizontalLineTo(20f); verticalLineTo(15f); horizontalLineTo(4f); close()
                moveTo(2f, 18f); lineTo(22f, 18f)
            }
        }
    }

    val CheckCircle: ImageVector by lazy {
        icon("CheckCircle") {
            path(
                stroke = fillBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 12f)
                moveToRelative(-9f, 0f)
                arcToRelative(9f, 9f, 0f, true, true, 18f, 0f)
                arcToRelative(9f, 9f, 0f, true, true, -18f, 0f)
                moveTo(8.5f, 12.2f); lineTo(11f, 14.7f); lineTo(15.8f, 9.5f)
            }
        }
    }

    val Warning: ImageVector by lazy {
        icon("Warning") {
            path(
                stroke = fillBrush,
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.5f); lineTo(21f, 19.5f); lineTo(3f, 19.5f); close()
                moveTo(12f, 9.5f); lineTo(12f, 14f)
            }
            path(fill = fillBrush) {
                moveTo(12f, 16.8f); moveToRelative(-1f, 0f); arcToRelative(1f, 1f, 0f, true, true, 2f, 0f); arcToRelative(1f, 1f, 0f, true, true, -2f, 0f)
            }
        }
    }
}
