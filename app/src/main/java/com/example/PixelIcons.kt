package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun PixelIcon(pixels16x16: List<String>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val pixelSize = size.width / 16f
        pixels16x16.forEachIndexed { y, row ->
            row.forEachIndexed { x, char ->
                val color = when (char) {
                    '#' -> Color.White
                    '%' -> Color.White.copy(alpha = 0.6f)
                    '.' -> Color.White.copy(alpha = 0.3f)
                    '@' -> Color(0xFFD71921) // Red alert
                    else -> Color.Transparent
                }
                if (color != Color.Transparent) {
                    drawRect(
                        color = color,
                        topLeft = Offset(x * pixelSize, y * pixelSize),
                        size = Size(pixelSize, pixelSize)
                    )
                }
            }
        }
    }
}

object PixelIcons {
    val LightningPath = listOf(
        "      .%#@.     ",
        "     .%#@.      ",
        "    .%#@.       ",
        "   .%#@.        ",
        "  .%%######%%.  ",
        " .%#@@@@@@#%.   ",
        "   ..%%%#@.     ",
        "      .%#@.     ",
        "     .%#@.      ",
        "    .%#@.       ",
        "   .%#@.        ",
        "  .%#@.         ",
        " .%#@.          ",
        ".%%.            ",
        "                ",
        "                "
    )
    
    val FloppyDisk = listOf(
        " %%%%%%%%%%%%%%.",
        " %############%.",
        " %#..........#%.",
        " %#.%######%.#%.",
        " %#.%######%.#%.",
        " %#.%%%%%%%%.#%.",
        " %#..........#%.",
        " %############%.",
        " %#....@@....#%.",
        " %#...@@@@...#%.",
        " %#..@.@@.@..#%.",
        " %#.@..@@..@.#%.",
        " %############%.",
        " %%%%%%%%%%%%%%.",
        " .............  ",
        "                "
    )

    val StopSign = listOf(
        "     %%%%%%     ",
        "   %%######%%   ",
        "  %##@@@@@@##%  ",
        " %#@@######@@#% ",
        " %#@#......#@#% ",
        " %#@#..##..#@#% ",
        " %#@#..##..#@#% ",
        " %#@#......#@#% ",
        " %#@#..##..#@#% ",
        " %#@#..##..#@#% ",
        " %#@#......#@#% ",
        " %#@@######@@#% ",
        "  %##@@@@@@##%  ",
        "   %%######%%   ",
        "     %%%%%%     ",
        "                "
    )

    val Moon = listOf(
        "      .%%%%.    ",
        "    .%%####%.   ",
        "   .%##%%..     ",
        "  .%##%.        ",
        " .%##%.    .%%. ",
        " %###.    .%##% ",
        " %###.   .%%##% ",
        " %###.    .%%%. ",
        " .%##%.         ",
        "  .%##%.        ",
        "   .%##%%..     ",
        "    .%%####%.   ",
        "      .%%%%.    ",
        "                ",
        "                ",
        "                "
    )

    val CloudRain = listOf(
        "       .%%.     ",
        "     .%%##%%.   ",
        "    .%######%.  ",
        "  .%%###%%###%. ",
        " .%#####..#####%",
        " %####......###%",
        " %##..........#%",
        " %%%%%%%%%%%%%%%",
        "                ",
        "   .%    .%     ",
        "  .%    .%      ",
        "                ",
        "     .%    .%   ",
        "    .%    .%    ",
        "                ",
        "                "
    )

    val Speaker = listOf(
        "      .%#       ",
        "     .%##.  %.  ",
        "    .%###.  %#  ",
        "  .%%%###.  %#% ",
        " .%##%###.  %#%#",
        " %###%###.  %#%#",
        " %###%###.  %#%#",
        " .%##%###.  %#%#",
        "  .%%%###.  %#% ",
        "    .%###.  %#  ",
        "     .%##.  %.  ",
        "      .%#       ",
        "                ",
        "                ",
        "                ",
        "                "
    )

    val Waveform = listOf(
        "                ",
        "                ",
        "       .#       ",
        "       ##  .%   ",
        "   .%  ##  ##   ",
        "   ##  ##  ##   ",
        " .%##  ##  ##.% ",
        " %###  ##  ###% ",
        " %###.%##.%###% ",
        " .%##%####%##.% ",
        "   ##%####%##   ",
        "   .% %##% %.   ",
        "       ##       ",
        "       .%       ",
        "                ",
        "                "
    )
}

