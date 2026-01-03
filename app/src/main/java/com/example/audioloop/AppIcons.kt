package com.example.audioloop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

object AppIcons {
    // Replacement for Icons.Default.Stop (Not in Core)
    val Stop: ImageVector
        get() {
            if (_stop != null) return _stop!!
            _stop = materialIcon(name = "Stop") {
                materialPath {
                    moveTo(6.0f, 6.0f)
                    horizontalLineToRelative(12.0f)
                    verticalLineToRelative(12.0f)
                    horizontalLineTo(6.0f)
                    close()
                }
            }
            return _stop!!
        }
    private var _stop: ImageVector? = null

    // Replacement for Icons.Default.GraphicEq
    val GraphicEq: ImageVector
        get() {
            if (_graphicEq != null) return _graphicEq!!
            _graphicEq = materialIcon(name = "GraphicEq") {
                // Simple 3-bar equalizer
                materialPath {
                    // Bar 1
                    moveTo(7.0f, 18.0f)
                    verticalLineTo(6.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    // Bar 2
                    moveTo(13.0f, 20.0f)
                    verticalLineTo(4.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineToRelative(16.0f)
                    close()
                    // Bar 3
                    moveTo(19.0f, 16.0f)
                    verticalLineTo(8.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineToRelative(8.0f)
                    close()
                }
            }
            return _graphicEq!!
        }
    private var _graphicEq: ImageVector? = null

    // Replacement for Icons.Default.ContentCut
    val ContentCut: ImageVector
        get() {
            if (_contentCut != null) return _contentCut!!
            _contentCut = materialIcon(name = "ContentCut") {
                // Simplified "Scissors"
                materialPath {
                     // Circle Left 
                    moveTo(6.0f, 16.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                    reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    // Circle Right
                    moveTo(18.0f, 16.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                    reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    // Blades X
                    moveTo(9.64f, 7.64f)
                    lineTo(12.0f, 10.0f) // Center
                    lineToRelative(2.36f, -2.36f)
                    // ... This is getting complex to simulate cutout.
                    // Let's just draw two crossing lines
                    moveTo(6.0f, 8.0f) // Start blade 1 top left
                    lineTo(16.0f, 18.0f) // End blade 1 bottom right (near handle) but handle is circle
                    
                    // Actually, let's use a simpler path:
                    // Loops at bottom, lines crossing to top
                    
                    // Left Blade
                    moveTo(7.5f, 16.5f)
                    lineTo(16.0f, 5.0f)
                    horizontalLineToRelative(2.0f) // Tip
                    lineTo(10.0f, 16.0f)
                    
                    // Right Blade
                    moveTo(16.5f, 16.5f)
                    lineTo(8.0f, 5.0f)
                    horizontalLineToRelative(-2.0f) // Tip
                    lineTo(14.0f, 16.0f)
                }
            }
            return _contentCut!!
        }
    private var _contentCut: ImageVector? = null

    // Replacement for Icons.AutoMirrored.Filled.ArrowBack
    val ArrowBack: ImageVector
        get() {
            if (_arrowBack != null) return _arrowBack!!
            _arrowBack = materialIcon(name = "ArrowBack") {
                materialPath {
                    moveTo(20.0f, 11.0f)
                    horizontalLineTo(7.83f)
                    lineToRelative(5.59f, -5.59f)
                    lineTo(12.0f, 4.0f)
                    lineToRelative(-8.0f, 8.0f)
                    lineToRelative(8.0f, 8.0f)
                    lineToRelative(1.41f, -1.41f)
                    lineTo(7.83f, 13.0f)
                    horizontalLineTo(20.0f)
                    verticalLineToRelative(-2.0f)
                    close()
                }
            }
            return _arrowBack!!
        }
    private var _arrowBack: ImageVector? = null

    // Replacement for Icons.AutoMirrored.Filled.ArrowForward
    val ArrowForward: ImageVector
        get() {
            if (_arrowForward != null) return _arrowForward!!
            _arrowForward = materialIcon(name = "ArrowForward") {
                materialPath {
                    moveTo(12.0f, 4.0f)
                    lineToRelative(-1.41f, 1.41f)
                    lineTo(16.17f, 11.0f)
                    horizontalLineTo(4.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(12.17f)
                    lineToRelative(-5.58f, 5.59f)
                    lineTo(12.0f, 20.0f)
                    lineToRelative(8.0f, -8.0f)
                    close()
                }
            }
            return _arrowForward!!
        }
    private var _arrowForward: ImageVector? = null
}
