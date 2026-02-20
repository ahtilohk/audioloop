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

    // Replacement for Icons.Default.ArrowDownward
    val ArrowDownward: ImageVector
        get() {
            if (_arrowDownward != null) return _arrowDownward!!
            _arrowDownward = materialIcon(name = "ArrowDownward") {
                materialPath {
                    moveTo(20.0f, 12.0f)
                    lineToRelative(-1.41f, -1.41f)
                    lineTo(13.0f, 16.17f)
                    verticalLineTo(4.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(12.17f)
                    lineToRelative(-5.58f, -5.59f)
                    lineTo(4.0f, 12.0f)
                    lineToRelative(8.0f, 8.0f)
                    lineToRelative(8.0f, -8.0f)
                    close()
                }
            }
            return _arrowDownward!!
        }
    private var _arrowDownward: ImageVector? = null

    // Replacement for Icons.Default.Mic
    val Mic: ImageVector
        get() {
            if (_mic != null) return _mic!!
            _mic = materialIcon(name = "Mic") {
                materialPath {
                    moveTo(12.0f, 14.0f)
                    curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
                    verticalLineTo(5.0f)
                    curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                    reflectiveCurveTo(9.0f, 3.34f, 9.0f, 5.0f)
                    verticalLineToRelative(6.0f)
                    curveTo(9.0f, 12.66f, 10.34f, 14.0f, 12.0f, 14.0f)
                    close()
                    moveTo(17.0f, 11.0f)
                    curveToRelative(0.0f, 2.76f, -2.24f, 5.0f, -5.0f, 5.0f)
                    reflectiveCurveToRelative(-5.0f, -2.24f, -5.0f, -5.0f)
                    horizontalLineTo(5.0f)
                    curveToRelative(0.0f, 3.53f, 2.61f, 6.43f, 6.0f, 6.92f)
                    verticalLineTo(21.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-3.08f)
                    curveToRelative(3.39f, -0.49f, 6.0f, -3.39f, 6.0f, -6.92f)
                    horizontalLineTo(17.0f)
                    close()
                }
            }
            return _mic!!
        }
    private var _mic: ImageVector? = null

    // Replacement for Icons.Default.Add
    val Add: ImageVector
        get() {
            if (_add != null) return _add!!
            _add = materialIcon(name = "Add") {
                materialPath {
                    moveTo(19.0f, 13.0f)
                    horizontalLineToRelative(-6.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-6.0f)
                    horizontalLineTo(5.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineToRelative(6.0f)
                    verticalLineTo(5.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(6.0f)
                    horizontalLineToRelative(6.0f)
                    verticalLineTo(13.0f)
                    close()
                }
            }
            return _add!!
        }
    private var _add: ImageVector? = null

    // Replacement for Icons.Default.PlayArrow
    val PlayArrow: ImageVector
        get() {
            if (_playArrow != null) return _playArrow!!
            _playArrow = materialIcon(name = "PlayArrow") {
                materialPath {
                    moveTo(8.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    lineToRelative(11.0f, -7.0f)
                    close()
                }
            }
            return _playArrow!!
        }
    private var _playArrow: ImageVector? = null

    val Play: ImageVector
        get() = PlayArrow

    // Replacement for Icons.Default.Pause
    val Pause: ImageVector
        get() {
            if (_pause != null) return _pause!!
            _pause = materialIcon(name = "Pause") {
                materialPath {
                    moveTo(6.0f, 19.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineTo(5.0f)
                    horizontalLineTo(6.0f)
                    verticalLineToRelative(14.0f)
                    close()
                    moveTo(14.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineTo(5.0f)
                    horizontalLineToRelative(-4.0f)
                    close()
                }
            }
            return _pause!!
        }
    private var _pause: ImageVector? = null

    // Replacement for Icons.Default.MoreVert
    val MoreVert: ImageVector
        get() {
            if (_moreVert != null) return _moreVert!!
            _moreVert = materialIcon(name = "MoreVert") {
                materialPath {
                    moveTo(12.0f, 8.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                    reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
                    reflectiveCurveTo(11.1f, 8.0f, 12.0f, 8.0f)
                    close()
                    moveTo(12.0f, 10.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                    reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                    reflectiveCurveTo(13.1f, 10.0f, 12.0f, 10.0f)
                    close()
                    moveTo(12.0f, 16.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                    reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                    reflectiveCurveTo(13.1f, 16.0f, 12.0f, 16.0f)
                    close()
                }
            }
            return _moreVert!!
        }
    private var _moreVert: ImageVector? = null

    // Replacement for Icons.Default.Settings
    val Settings: ImageVector
        get() {
            if (_settings != null) return _settings!!
            _settings = materialIcon(name = "Settings") {
                materialPath {
                    moveTo(19.14f, 12.94f)
                    curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
                    curveToRelative(0.0f, -0.32f, -0.02f, -0.64f, -0.06f, -0.94f)
                    lineToRelative(2.03f, -1.58f)
                    curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
                    lineToRelative(-1.92f, -3.32f)
                    curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
                    lineToRelative(-2.39f, 0.96f)
                    curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
                    lineToRelative(-0.36f, -2.54f)
                    curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
                    horizontalLineToRelative(-3.84f)
                    curveToRelative(-0.24f, 0.0f, -0.43f, 0.17f, -0.47f, 0.41f)
                    lineTo(9.25f, 4.35f)
                    curveToRelative(-0.59f, 0.24f, -1.13f, 0.57f, -1.62f, 0.94f)
                    lineTo(5.24f, 4.33f)
                    curveToRelative(-0.22f, -0.08f, -0.47f, 0.0f, -0.59f, 0.22f)
                    lineTo(2.73f, 7.87f)
                    curveToRelative(-0.11f, 0.21f, -0.06f, 0.47f, 0.12f, 0.61f)
                    lineToRelative(2.03f, 1.58f)
                    curveToRelative(-0.04f, 0.3f, -0.06f, 0.61f, -0.06f, 0.94f)
                    reflectiveCurveToRelative(0.02f, 0.64f, 0.06f, 0.94f)
                    lineToRelative(-2.03f, 1.58f)
                    curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
                    lineToRelative(1.92f, 3.32f)
                    curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
                    lineToRelative(2.39f, -0.96f)
                    curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
                    lineToRelative(0.36f, 2.54f)
                    curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
                    horizontalLineToRelative(3.84f)
                    curveToRelative(0.24f, 0.0f, 0.44f, -0.17f, 0.47f, -0.41f)
                    lineToRelative(0.36f, -2.54f)
                    curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
                    lineToRelative(2.39f, 0.96f)
                    curveToRelative(0.22f, 0.08f, 0.47f, 0.0f, 0.59f, -0.22f)
                    lineToRelative(1.92f, -3.32f)
                    curveToRelative(0.11f, -0.22f, 0.05f, -0.47f, -0.12f, -0.61f)
                    lineToRelative(-2.01f, -1.58f)
                    close()
                    moveTo(12.0f, 15.6f)
                    curveToRelative(-1.98f, 0.0f, -3.6f, -1.62f, -3.6f, -3.6f)
                    reflectiveCurveToRelative(1.62f, -3.6f, 3.6f, -3.6f)
                    reflectiveCurveToRelative(3.6f, 1.62f, 3.6f, 3.6f)
                    reflectiveCurveToRelative(-1.62f, 3.6f, -3.6f, 3.6f)
                    close()
                }
            }
            return _settings!!
        }
    private var _settings: ImageVector? = null

    // Replacement for Icons.Default.Radio
    val Radio: ImageVector
        get() {
            if (_radio != null) return _radio!!
            _radio = materialIcon(name = "Radio") {
                materialPath(fillAlpha = 0.5f, strokeAlpha = 0.5f) {
                    moveTo(3.24f, 6.15f)
                    curveTo(2.51f, 6.43f, 2.0f, 7.17f, 2.0f, 8.0f)
                    verticalLineToRelative(12.0f)
                    curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(16.0f)
                    curveToRelative(1.11f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineTo(8.0f)
                    curveToRelative(0.0f, -1.11f, -0.89f, -2.0f, -2.0f, -2.0f)
                    horizontalLineTo(8.3f)
                    lineToRelative(8.26f, -3.34f)
                    lineTo(15.88f, 1.0f)
                    lineTo(3.24f, 6.15f)
                    close()
                    moveTo(7.0f, 20.0f)
                    curveToRelative(-1.66f, 0.0f, -3.0f, -1.34f, -3.0f, -3.0f)
                    reflectiveCurveToRelative(1.34f, -3.0f, 3.0f, -3.0f)
                    reflectiveCurveToRelative(3.0f, 1.34f, 3.0f, 3.0f)
                    reflectiveCurveToRelative(-1.34f, 3.0f, -3.0f, 3.0f)
                    close()
                    moveTo(18.0f, 10.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                    // Note: Simplified radio mostly
                }
            }
            return _radio!!
        }
    private var _radio: ImageVector? = null

    // Replacement for Icons.Default.Check
    val Check: ImageVector
        get() {
            if (_check != null) return _check!!
            _check = materialIcon(name = "Check") {
                materialPath {
                    moveTo(9.0f, 16.17f)
                    lineTo(4.83f, 12.0f)
                    lineToRelative(-1.42f, 1.41f)
                    lineTo(9.0f, 19.0f)
                    lineTo(21.0f, 7.0f)
                    lineToRelative(-1.41f, -1.41f)
                    close()
                }
            }
            return _check!!
        }
    private var _check: ImageVector? = null

    // Replacement for Icons.Default.KeyboardArrowDown (ChevronDown)
    val ChevronDown: ImageVector
        get() {
            if (_chevronDown != null) return _chevronDown!!
            _chevronDown = materialIcon(name = "ChevronDown") {
                materialPath {
                    moveTo(7.41f, 8.59f)
                    lineTo(12.0f, 13.17f)
                    lineToRelative(4.59f, -4.58f)
                    lineTo(18.0f, 10.0f)
                    lineToRelative(-6.0f, 6.0f)
                    lineToRelative(-6.0f, -6.0f)
                    close()
                }
            }
            return _chevronDown!!
        }
    private var _chevronDown: ImageVector? = null

    // Replacement for Icons.Default.KeyboardArrowUp (ChevronUp)
    val ChevronUp: ImageVector
        get() {
            if (_chevronUp != null) return _chevronUp!!
            _chevronUp = materialIcon(name = "ChevronUp") {
                materialPath {
                    moveTo(7.41f, 15.41f)
                    lineTo(12.0f, 10.83f)
                    lineToRelative(4.59f, 4.58f)
                    lineTo(18.0f, 14.0f)
                    lineToRelative(-6.0f, -6.0f)
                    lineToRelative(-6.0f, 6.0f)
                    close()
                }
            }
            return _chevronUp!!
        }
    private var _chevronUp: ImageVector? = null

   // Replacement for Icons.Default.DragIndicator (GripVertical)
    val GripVertical: ImageVector
        get() {
            if (_gripVertical != null) return _gripVertical!!
            _gripVertical = materialIcon(name = "GripVertical") {
                 materialPath {
                    moveTo(11.0f, 18.0f)
                    curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                    reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                    close()
                    moveTo(11.0f, 6.0f)
                    curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                    reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                    close()
                    moveTo(11.0f, 12.0f)
                    curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                    reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                    close()
                    moveTo(15.0f, 6.0f)
                    curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                    reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                    close()
                    moveTo(15.0f, 12.0f)
                    curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                    reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                    close()
                    moveTo(15.0f, 18.0f)
                    curveToRelative(0.0f, 1.1f, -0.9f, 2.0f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                    reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                    close()
                }
            }
            return _gripVertical!!
        }
    private var _gripVertical: ImageVector? = null

    // Replacement for Icons.Default.Close (X)
    val Close: ImageVector
        get() {
            if (_close != null) return _close!!
            _close = materialIcon(name = "Close") {
                materialPath {
                    moveTo(19.0f, 6.41f)
                    lineTo(17.59f, 5.0f)
                    lineTo(12.0f, 10.59f)
                    lineTo(6.41f, 5.0f)
                    lineTo(5.0f, 6.41f)
                    lineTo(10.59f, 12.0f)
                    lineTo(5.0f, 17.59f)
                    lineTo(6.41f, 19.0f)
                    lineTo(12.0f, 13.41f)
                    lineTo(17.59f, 19.0f)
                    lineTo(19.0f, 17.59f)
                    lineTo(13.41f, 12.0f)
                    close()
                }
            }
            return _close!!
        }
    private var _close: ImageVector? = null

    // Replacement for Icons.Default.Edit (Pencil)
    val Edit: ImageVector
        get() {
            if (_edit != null) return _edit!!
            _edit = materialIcon(name = "Edit") {
                 materialPath {
                    moveTo(3.0f, 17.25f)
                    verticalLineTo(21.0f)
                    horizontalLineToRelative(3.75f)
                    lineTo(17.81f, 9.94f)
                    lineToRelative(-3.75f, -3.75f)
                    lineTo(3.0f, 17.25f)
                    close()
                    moveTo(20.71f, 7.04f)
                    curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                    lineToRelative(-2.34f, -2.34f)
                    curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                    lineToRelative(-1.83f, 1.83f)
                    lineToRelative(3.75f, 3.75f)
                    lineToRelative(1.83f, -1.83f)
                    close()
                }
            }
            return _edit!!
        }
    private var _edit: ImageVector? = null

    // Replacement for Icons.Default.Delete (Trash2)
    val Delete: ImageVector
        get() {
            if (_delete != null) return _delete!!
            _delete = materialIcon(name = "Delete") {
                materialPath {
                     moveTo(6.0f, 19.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(8.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineTo(7.0f)
                    horizontalLineTo(6.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(19.0f, 4.0f)
                    horizontalLineToRelative(-3.5f)
                    lineToRelative(-1.0f, -1.0f)
                    horizontalLineToRelative(-5.0f)
                    lineToRelative(-1.0f, 1.0f)
                    horizontalLineTo(5.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineTo(4.0f)
                    close()
                }
            }
            return _delete!!
        }
    private var _delete: ImageVector? = null

    // Replacement for Icons.Default.Share
    val Share: ImageVector
        get() {
            if (_share != null) return _share!!
            _share = materialIcon(name = "Share") {
                materialPath {
                    moveTo(18.0f, 16.08f)
                    curveToRelative(-0.76f, 0.0f, -1.44f, 0.3f, -1.96f, 0.77f)
                    lineTo(8.91f, 12.7f)
                    curveToRelative(0.05f, -0.23f, 0.09f, -0.46f, 0.09f, -0.7f)
                    reflectiveCurveToRelative(-0.04f, -0.47f, -0.09f, -0.7f)
                    lineToRelative(7.05f, -4.11f)
                    curveToRelative(0.53f, 0.47f, 1.21f, 0.77f, 1.96f, 0.77f)
                    curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
                    reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
                    reflectiveCurveToRelative(-3.0f, 1.34f, -3.0f, 3.0f)
                    curveToRelative(0.0f, 0.24f, 0.04f, 0.47f, 0.09f, 0.7f)
                    lineTo(8.04f, 9.81f)
                    curveTo(7.5f, 9.31f, 6.79f, 9.0f, 6.0f, 9.0f)
                    curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                    reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                    curveToRelative(0.79f, 0.0f, 1.5f, -0.31f, 2.04f, -0.81f)
                    lineToRelative(7.12f, 4.16f)
                    curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
                    curveToRelative(0.0f, 1.61f, 1.31f, 2.92f, 2.92f, 2.92f)
                    reflectiveCurveToRelative(2.92f, -1.31f, 2.92f, -2.92f)
                    reflectiveCurveToRelative(-1.31f, -2.92f, -2.92f, -2.92f)
                    close()
                }
            }
            return _share!!
        }
    private var _share: ImageVector? = null

    // Speed (Fast-Forward)
    val Speed: ImageVector
        get() {
            if (_speed != null) return _speed!!
            _speed = materialIcon(name = "Speed") {
                materialPath {
                    // First triangle
                    moveTo(4.0f, 18.0f)
                    lineTo(4.0f, 6.0f)
                    lineTo(12.0f, 12.0f)
                    close()
                    // Second triangle
                    moveTo(12.0f, 18.0f)
                    lineTo(12.0f, 6.0f)
                    lineTo(20.0f, 12.0f)
                    close()
                }
            }
            return _speed!!
        }
    private var _speed: ImageVector? = null

    // Loop (Repeat arrows)
    val Loop: ImageVector
        get() {
             if (_loop != null) return _loop!!
             _loop = materialIcon(name = "Loop") {
                 materialPath {
                    // Top arrow (pointing right)
                    moveTo(7.0f, 7.0f)
                    horizontalLineTo(17.17f)
                    lineTo(14.59f, 4.41f)
                    lineTo(16.0f, 3.0f)
                    lineTo(21.0f, 8.0f)
                    lineTo(16.0f, 13.0f)
                    lineTo(14.59f, 11.59f)
                    lineTo(17.17f, 9.0f)
                    horizontalLineTo(7.0f)
                    curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
                    reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
                    horizontalLineTo(17.0f)
                    verticalLineTo(21.0f)
                    horizontalLineTo(7.0f)
                    curveToRelative(-3.87f, 0.0f, -7.0f, -3.13f, -7.0f, -7.0f)
                    reflectiveCurveToRelative(3.13f, -7.0f, 7.0f, -7.0f)
                    close()
                 }
             }
             return _loop!!
        }
    private var _loop: ImageVector? = null

    // Shadow (Layers)
    val Shadow: ImageVector
        get() {
            if (_shadow != null) return _shadow!!
            _shadow = materialIcon(name = "Shadow") {
                materialPath {
                    moveTo(11.99f, 18.54f)
                    lineToRelative(-7.37f, -5.73f)
                    lineTo(3.0f, 14.07f)
                    lineToRelative(9.0f, 7.0f)
                    lineToRelative(9.0f, -7.0f)
                    lineToRelative(-1.63f, -1.27f)
                    lineToRelative(-7.38f, 5.74f)
                    close()
                    moveTo(12.0f, 16.0f)
                    lineToRelative(7.36f, -5.73f)
                    lineTo(21.0f, 9.0f)
                    lineToRelative(-9.0f, -7.0f)
                    lineToRelative(-9.0f, 7.0f)
                    lineToRelative(1.63f, 1.27f)
                    lineTo(12.0f, 16.0f)
                    close()
                }
            }
            return _shadow!!
        }
    private var _shadow: ImageVector? = null

    // Sleep (Timer/Hourglass-ish)
    val Sleep: ImageVector
        get() {
            if (_sleep != null) return _sleep!!
             _sleep = materialIcon(name = "Sleep") {
                 materialPath {
                    moveTo(11.99f, 2.0f)
                    curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                    curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                    reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                    close()
                    moveTo(12.0f, 20.0f)
                    curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                    reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                    reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                    reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                    close()
                    moveTo(12.5f, 7.0f)
                    horizontalLineTo(11.0f)
                    verticalLineToRelative(6.0f)
                    lineToRelative(5.25f, 3.15f)
                    lineToRelative(0.75f, -1.23f)
                    lineToRelative(-4.5f, -2.67f)
                    verticalLineTo(7.0f)
                    close()
                 }
             }
             return _sleep!!
        }
    private var _sleep: ImageVector? = null

    // Replay (Reset/Restart)
    val Replay: ImageVector
        get() {
            if (_replay != null) return _replay!!
            _replay = materialIcon(name = "Replay") {
                materialPath {
                    moveTo(12.0f, 5.0f)
                    verticalLineTo(1.0f)
                    lineTo(7.0f, 6.0f)
                    lineToRelative(5.0f, 5.0f)
                    verticalLineTo(7.0f)
                    curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                    reflectiveCurveToRelative(-2.69f, 6.0f, -6.0f, 6.0f)
                    reflectiveCurveToRelative(-6.0f, -2.69f, -6.0f, -6.0f)
                    horizontalLineTo(4.0f)
                    curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                    reflectiveCurveToRelative(8.0f, -3.58f, 8.0f, -8.0f)
                    reflectiveCurveToRelative(-3.58f, -8.0f, -8.0f, -8.0f)
                    close()
                }
            }
            return _replay!!
        }
    private var _replay: ImageVector? = null

    // Info (Circle with i)
    val Info: ImageVector
        get() {
            if (_info != null) return _info!!
            _info = materialIcon(name = "Info") {
                materialPath {
                    moveTo(12.0f, 2.0f)
                    curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                    reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                    reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                    close()
                    moveTo(13.0f, 17.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-6.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(6.0f)
                    close()
                    moveTo(13.0f, 9.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineTo(7.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    close()
                }
            }
            return _info!!
        }
    private var _info: ImageVector? = null

    // Stream (Broadcasting/Signal - Sensors style)
    val Stream: ImageVector
        get() {
            if (_stream != null) return _stream!!
            _stream = materialIcon(name = "Stream") {
                materialPath {
                    // Central dot
                    moveTo(12.0f, 15.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                    reflectiveCurveToRelative(-2.0f, 0.9f, -2.0f, 2.0f)
                    reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                    close()
                    // Inner ring
                    moveTo(12.0f, 7.0f)
                    curveToRelative(-3.31f, 0.0f, -6.0f, 2.69f, -6.0f, 6.0f)
                    curveToRelative(0.0f, 1.66f, 0.68f, 3.15f, 1.76f, 4.24f)
                    lineToRelative(1.42f, -1.42f)
                    curveTo(8.42f, 15.05f, 8.0f, 14.08f, 8.0f, 13.0f)
                    curveToRelative(0.0f, -2.21f, 1.79f, -4.0f, 4.0f, -4.0f)
                    reflectiveCurveToRelative(4.0f, 1.79f, 4.0f, 4.0f)
                    curveToRelative(0.0f, 1.08f, -0.42f, 2.05f, -1.18f, 2.82f)
                    lineToRelative(1.42f, 1.42f)
                    curveTo(17.32f, 16.15f, 18.0f, 14.66f, 18.0f, 13.0f)
                    curveToRelative(0.0f, -3.31f, -2.69f, -6.0f, -6.0f, -6.0f)
                    close()
                    // Outer ring
                    moveTo(12.0f, 3.0f)
                    curveTo(6.48f, 3.0f, 2.0f, 7.48f, 2.0f, 13.0f)
                    curveToRelative(0.0f, 2.76f, 1.12f, 5.26f, 2.93f, 7.07f)
                    lineToRelative(1.41f, -1.41f)
                    curveTo(4.94f, 17.26f, 4.0f, 15.24f, 4.0f, 13.0f)
                    curveToRelative(0.0f, -4.42f, 3.58f, -8.0f, 8.0f, -8.0f)
                    reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                    curveToRelative(0.0f, 2.24f, -0.94f, 4.26f, -2.34f, 5.66f)
                    lineToRelative(1.41f, 1.41f)
                    curveTo(20.88f, 18.26f, 22.0f, 15.76f, 22.0f, 13.0f)
                    curveToRelative(0.0f, -5.52f, -4.48f, -10.0f, -10.0f, -10.0f)
                    close()
                }
            }
            return _stream!!
        }
    private var _stream: ImageVector? = null

}
